package com.maipu.callsync.monitoring

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.maipu.callsync.data.DeviceIdentityStore
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Independent local identity and credential storage for monitoring.
 *
 * The monitoring credential is 32 random bytes rendered as lowercase hex. The hex
 * string itself is encrypted with an Android Keystore AES/GCM key; only its SHA-256
 * fingerprint is exposed for administrative enrollment.
 */
class DeviceProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val identityStore = DeviceIdentityStore(appContext)

    fun getDeviceId(): String = identityStore.getOrCreate()

    fun save(name: String, line: String) {
        val normalizedName = name.trim()
        val normalizedLine = line.trim()
        require(normalizedName.isNotEmpty()) { "El nombre del vendedor es obligatorio." }
        require(normalizedLine.isNotEmpty()) { "La línea corporativa es obligatoria." }
        synchronized(this) {
            val existingName = preferences.getString(NAME_KEY, null)
            val existingLine = preferences.getString(LINE_KEY, null)
            require(existingName == null && existingLine == null ||
                (existingName == normalizedName && existingLine == normalizedLine)
            ) { "La identidad ya está configurada y no puede modificarse desde esta instalación." }
            check(
                preferences.edit()
                    .putString(NAME_KEY, normalizedName)
                    .putString(LINE_KEY, normalizedLine)
                    .commit(),
            ) { "No se pudo persistir la identidad del dispositivo." }
        }
    }

    fun getName(): String? = preferences.getString(NAME_KEY, null)

    fun getLine(): String? = preferences.getString(LINE_KEY, null)

    fun getMonitoringTokenHash(): String = monitoringSha256Hex(getOrCreateMonitoringToken())

    /** Display-safe identifier; this value is never an authentication credential. */
    fun getPublicMonitoringTokenHash(): String = getMonitoringTokenHash()

    fun isProfileConfigured(): Boolean = !getName().isNullOrBlank() && !getLine().isNullOrBlank()

    fun isBindingPending(): Boolean = isProfileConfigured() &&
        !preferences.getBoolean(BOUND_KEY, false)

    fun getBindingStatus(): MonitoringBindingStatus = when {
        !isProfileConfigured() -> MonitoringBindingStatus.PROFILE_REQUIRED
        preferences.getBoolean(BOUND_KEY, false) -> MonitoringBindingStatus.BOUND
        else -> MonitoringBindingStatus.PENDING_ADMIN
    }

    internal fun getOrCreateMonitoringToken(): String = synchronized(this) {
        preferences.getString(ENCRYPTED_TOKEN_KEY, null)?.let { encrypted ->
            return@synchronized decrypt(encrypted)
        }

        val bytes = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString(separator = "") {
            byte -> "%02x".format(byte.toInt() and 0xff)
        }
        val encrypted = encrypt(token)
        check(
            preferences.edit()
                .putString(ENCRYPTED_TOKEN_KEY, encrypted)
                .commit(),
        ) { "No se pudo persistir la credencial de monitoreo." }
        token
    }

    internal fun canCheckBinding(nowMillis: Long): Boolean =
        preferences.getBoolean(BOUND_KEY, false) ||
            nowMillis - preferences.getLong(LAST_BINDING_CHECK_KEY, 0L) >=
            BINDING_RECHECK_INTERVAL_MILLIS

    internal fun recordBindingCheck(nowMillis: Long) {
        preferences.edit()
            .putLong(LAST_BINDING_CHECK_KEY, nowMillis)
            .putBoolean(BOUND_KEY, false)
            .apply()
    }

    internal fun applyAssignedIdentity(name: String?, line: String?) {
        val editor = preferences.edit()
        name?.trim()?.takeIf(String::isNotEmpty)?.let { editor.putString(NAME_KEY, it) }
        line?.trim()?.takeIf(String::isNotEmpty)?.let { editor.putString(LINE_KEY, it) }
        editor.putBoolean(BOUND_KEY, true).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > GCM_IV_BYTES) { "La credencial de monitoreo almacenada está dañada." }
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKeystoreKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "call_sync_monitoring_profile"
        const val NAME_KEY = "seller_name"
        const val LINE_KEY = "corporate_line"
        const val ENCRYPTED_TOKEN_KEY = "encrypted_monitoring_token"
        const val BOUND_KEY = "server_bound"
        const val LAST_BINDING_CHECK_KEY = "last_binding_check"
        const val KEY_ALIAS = "maipu_call_sync_monitoring_aes"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val BINDING_RECHECK_INTERVAL_MILLIS = 12 * 60 * 60 * 1000L
    }
}

enum class MonitoringBindingStatus {
    PROFILE_REQUIRED,
    PENDING_ADMIN,
    BOUND,
}

internal fun monitoringSha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }