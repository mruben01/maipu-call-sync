package com.maipu.callsync.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.maipu.callsync.BuildConfig
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSink
import org.json.JSONObject

class SupabaseCallRecordingClient(context: Context) {
    private val resolver: ContentResolver =
        context.applicationContext.contentResolver
    private val projectUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    fun validateConfiguration() {
        endpoint("rest", "v1", "call_recordings")
    }

    fun uploadAudio(
        contentUri: Uri,
        sizeBytes: Long?,
        mimeType: String?,
        storagePath: String,
        expectedSha256: String,
    ) {
        val url = endpoint("storage", "v1", "object", BUCKET, storagePath)
        val body = ContentUriRequestBody(
            resolver = resolver,
            contentUri = contentUri,
            contentLengthBytes = sizeBytes?.takeIf { it >= 0 },
            mimeType = mimeType,
            expectedSha256 = expectedSha256,
        )
        val request = authorizedRequest(url)
            .header("x-upsert", "false")
            .post(body)
            .build()

        execute(request, "subida de audio") { response, responseBody ->
            val duplicateObject = response.code == 400 || response.code == 409
            if (duplicateObject &&
                responseBody.contains("already exists", ignoreCase = true)
            ) {
                return@execute
            }
            if (!response.isSuccessful) {
                throw storageError(response.code, responseBody)
            }
        }
    }

    fun insertMetadata(entry: StoredSyncEntry) {
        val storagePath = entry.storagePath
            ?: throw SyncOperationException("Falta la ruta del audio cargado.")
        val payload = JSONObject()
            .put("phone_number", entry.metadata.phoneNumber ?: JSONObject.NULL)
            .put("call_datetime", entry.metadata.callDatetime ?: JSONObject.NULL)
            .put(
                "duration_seconds",
                entry.metadata.durationSeconds ?: JSONObject.NULL,
            )
            .put("file_name", entry.metadata.fileName)
            .put("storage_path", storagePath)
            .put("device_id", entry.metadata.deviceId)
            .put("sync_status", "synced")
            .toString()

        val request = authorizedRequest(endpoint("rest", "v1", "call_recordings"))
            .header("Prefer", "return=minimal")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        execute(request, "registro de metadatos") { response, responseBody ->
            if (isExistingMetadataConflict(response.code, responseBody)) {
                return@execute
            }
            if (!response.isSuccessful) {
                throw postgrestError(response.code, responseBody)
            }
        }
    }

    private fun authorizedRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", publishableKey)

    private fun endpoint(vararg pathSegments: String): HttpUrl {
        if (projectUrl.isBlank() || publishableKey.isBlank()) {
            throw SyncOperationException(
                "La APK no recibió SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY " +
                    "durante la compilación.",
            )
        }

        val baseUrl = projectUrl.toHttpUrlOrNull()
            ?: throw SyncOperationException("SUPABASE_URL no tiene un formato válido.")
        if (!baseUrl.isHttps) {
            throw SyncOperationException("Supabase debe configurarse con HTTPS.")
        }
        return pathSegments.fold(baseUrl.newBuilder()) { builder, segment ->
            builder.addPathSegment(segment)
        }.build()
    }

    private inline fun execute(
        request: Request,
        operation: String,
        handleResponse: (okhttp3.Response, String) -> Unit,
    ) {
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                handleResponse(response, responseBody)
            }
        } catch (exception: SyncOperationException) {
            throw exception
        } catch (_: UnknownHostException) {
            throw SyncOperationException(
                "No hay conexión con Supabase. Revise Internet e intente de nuevo.",
            )
        } catch (_: SocketTimeoutException) {
            throw SyncOperationException(
                "La solicitud tardó demasiado. Puede reintentar la sincronización.",
            )
        } catch (_: SecurityException) {
            throw SyncOperationException(
                "Android no permitió leer la grabación. Revise el permiso de audio.",
            )
        } catch (_: IOException) {
            throw SyncOperationException(
                "Falló la lectura o transferencia del archivo. Puede reintentar.",
            )
        } catch (_: Exception) {
            throw SyncOperationException(
                "Falló la $operation. Puede reintentar la sincronización.",
            )
        }
    }

    private fun storageError(statusCode: Int, responseBody: String): SyncOperationException {
        if (statusCode == 401 || statusCode == 403) {
            return SyncOperationException(
                "Supabase rechazó la subida (HTTP $statusCode). " +
                    "Revise la policy INSERT del bucket.",
            )
        }
        if (statusCode == 413) {
            return SyncOperationException(
                "Supabase rechazó el archivo por su tamaño (HTTP 413).",
            )
        }
        return apiError("subida de audio", statusCode)
    }

    private fun apiError(operation: String, statusCode: Int) =
        SyncOperationException(
            when (statusCode) {
                401, 403 ->
                    "Supabase rechazó la solicitud (HTTP $statusCode). " +
                        "Revise la publishable key y las policies."
                413 -> "El archivo supera el límite aceptado por Supabase (HTTP 413)."
                429, in 500..599 ->
                    "Supabase no pudo completar la operación (HTTP $statusCode). " +
                        "Puede reintentar."
                else -> "Falló la $operation en Supabase (HTTP $statusCode)."
            },
        )

    private fun postgrestError(
        statusCode: Int,
        responseBody: String,
    ): SyncOperationException {
        val errorJson = runCatching { JSONObject(responseBody) }.getOrNull()
        fun field(name: String): String =
            errorJson
                ?.opt(name)
                ?.takeUnless { it == JSONObject.NULL }
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: "(no informado)"

        val completeBody = responseBody.ifBlank { "(cuerpo vacío)" }
        return SyncOperationException(
            buildString {
                appendLine("Falló el registro de metadatos en Supabase.")
                appendLine("HTTP status: $statusCode")
                appendLine("code: ${field("code")}")
                appendLine("message: ${field("message")}")
                appendLine("details: ${field("details")}")
                appendLine("hint: ${field("hint")}")
                appendLine("Cuerpo completo de PostgREST:")
                append(completeBody)
            },
        )
    }

    private class ContentUriRequestBody(
        private val resolver: ContentResolver,
        private val contentUri: Uri,
        private val contentLengthBytes: Long?,
        mimeType: String?,
        private val expectedSha256: String,
    ) : RequestBody() {
        private val mediaType = mimeType
            ?.toMediaTypeOrNull()
            ?: "audio/mp4".toMediaType()

        override fun contentType() = mediaType

        override fun contentLength(): Long = contentLengthBytes ?: -1L

        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(contentUri)
                ?: throw IOException("No se pudo abrir el Content URI.")
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            input.use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead < 0) break
                    digest.update(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    sink.write(buffer, 0, bytesRead)
                }
            }
            if (contentLengthBytes != null && totalBytes != contentLengthBytes) {
                throw IOException("El tamaño de la grabación cambió durante la subida.")
            }
            val actualSha256 = digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
            if (actualSha256 != expectedSha256) {
                throw IOException("El contenido cambió entre la lectura y la subida.")
            }
        }
    }

    private companion object {
        const val BUCKET = "call-recordings"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class SyncOperationException(message: String) : IOException(message)

internal fun isExistingMetadataConflict(statusCode: Int, responseBody: String): Boolean {
    if (statusCode != 409) return false
    val error = runCatching { JSONObject(responseBody) }.getOrNull() ?: return false
    return error.opt("code") == "23505" &&
        error.opt("message") ==
            "duplicate key value violates unique constraint " +
                "\"call_recordings_storage_path_key\""
}