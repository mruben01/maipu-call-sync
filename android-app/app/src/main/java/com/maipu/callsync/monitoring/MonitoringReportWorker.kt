package com.maipu.callsync.monitoring

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.maipu.callsync.BuildConfig
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class MonitoringReportWorker(
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {
    private val profileStore = DeviceProfileStore(context)

    override fun doWork(): Result {
        if (!profileStore.isProfileConfigured()) {
            // The setup UI can surface this as an unconfigured identity, not an error.
            return Result.success()
        }
        if (!profileStore.canCheckBinding(System.currentTimeMillis())) {
            return Result.success()
        }

        return try {
            val client = MonitoringHttpClient()
            val snapshot = MonitoringSnapshotReader(applicationContext).read()
            client.send(profileStore.getDeviceId(), profileStore.getOrCreateMonitoringToken(), snapshot)
                .also { response ->
                    profileStore.applyAssignedIdentity(
                        name = response.assignedName,
                        line = response.assignedLine,
                    )
                }
            Result.success()
        } catch (exception: MonitoringRequestException) {
            if (exception.notEnrolled) {
                profileStore.recordBindingCheck(System.currentTimeMillis())
            }
            if (exception.retryable) Result.retry()
            else Result.failure(
                androidx.work.workDataOf(ERROR_OUTPUT_KEY to exception.message.orEmpty()),
            )
        } catch (exception: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val ERROR_OUTPUT_KEY = "monitoring_error"
    }
}

internal class MonitoringHttpClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    fun send(
        deviceId: String,
        token: String,
        snapshot: MonitoringSnapshot,
    ): MonitoringReportResponse {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/').toHttpUrlOrNull()
            ?: throw MonitoringRequestException(
                "La APK no recibió una SUPABASE_URL válida para monitoreo.",
                retryable = false,
            )
        if (!baseUrl.isHttps || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            throw MonitoringRequestException(
                "La configuración HTTPS o la publishable key de monitoreo no está disponible.",
                retryable = false,
            )
        }
        val endpoint = baseUrl.newBuilder()
            .addPathSegments("functions/v1/monitor-report")
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(MonitoringReportPayload.toJson(deviceId, snapshot).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorJson = runCatching { JSONObject(body) }.getOrNull()
                    val errorCode = errorJson?.optString("code").orEmpty()
                    val errorText = errorJson?.optString("error").orEmpty()
                    val errorMessage = errorJson?.optString("message").orEmpty()
                    val notEnrolled = response.code == 404 ||
                        errorCode.equals("DEVICE_NOT_ENROLLED", ignoreCase = true) ||
                        errorText.equals("DEVICE_NOT_ENROLLED", ignoreCase = true) ||
                        errorMessage.contains("device not enrolled", ignoreCase = true) ||
                        errorMessage.contains("device not registered", ignoreCase = true)
                    val detail = errorMessage
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.replace(token, "[redacted]")
                    throw MonitoringRequestException(
                        buildString {
                            append("El servidor rechazó el reporte de monitoreo (HTTP ${response.code}).")
                            if (detail != null) append(" $detail")
                            if (notEnrolled) append(" El dispositivo todavía no está vinculado.")
                        },
                        retryable = response.code == 408 || response.code == 429 ||
                            response.code in 500..599,
                        notEnrolled = notEnrolled,
                    )
                }

                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: throw MonitoringRequestException(
                        "El servidor devolvió una respuesta de monitoreo inválida.",
                        retryable = true,
                    )
                if (!json.optBoolean("ok", false)) {
                    throw MonitoringRequestException(
                        "El servidor respondió HTTP ${response.code} sin confirmar el reporte.",
                        retryable = false,
                    )
                }
                val assignedName = json.optNullableString("assigned_name")
                    ?: json.optNullableString("seller_name")
                val assignedLine = json.optNullableString("assigned_line")
                    ?: json.optNullableString("corporate_line")
                if (assignedName == null || assignedLine == null) {
                    throw MonitoringRequestException(
                        "El servidor aceptó el reporte, pero no devolvió la identidad asignada.",
                        retryable = true,
                    )
                }
                return MonitoringReportResponse(assignedName, assignedLine)
            }
        } catch (exception: MonitoringRequestException) {
            throw exception
        } catch (_: UnknownHostException) {
            throw MonitoringRequestException("No hay conexión con el servidor de monitoreo.")
        } catch (_: SocketTimeoutException) {
            throw MonitoringRequestException("La solicitud de monitoreo agotó el tiempo de espera.")
        } catch (_: IOException) {
            throw MonitoringRequestException("Falló la conexión del reporte de monitoreo.")
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        opt(key)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal data class MonitoringReportResponse(
    val assignedName: String,
    val assignedLine: String,
)

internal class MonitoringRequestException(
    message: String,
    val retryable: Boolean = true,
    val notEnrolled: Boolean = false,
) : IOException(message)