package com.icanttalk.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

class SecureSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePreferences = context.applicationContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val installationId = preferences.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { preferences.edit().putString(KEY_INSTALLATION_ID, it).apply() }
        val avatarId = preferences.getString(KEY_AVATAR_ID, null)?.takeIf { it in AvatarIds.all }
            ?: AvatarIds.all[Random.nextInt(AvatarIds.all.size)].also { preferences.edit().putString(KEY_AVATAR_ID, it).apply() }
        val legacyNoise = preferences.getBoolean(KEY_NOISE_SUPPRESSION, true)
        val noiseMode = enumValueOrDefault(preferences.getString(KEY_NOISE_MODE, null), if (legacyNoise) NoiseMode.STANDARD else NoiseMode.OFF)
        return AppSettings(
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            tokenEndpoint = preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)?.takeIf { it.isNotBlank() } ?: DEFAULT_ENDPOINT,
            installationId = installationId,
            avatarId = avatarId,
            voiceMode = enumValueOrDefault(preferences.getString(KEY_VOICE_MODE, null), VoiceMode.VOICE_ACTIVITY),
            noiseMode = noiseMode,
            cameraQuality = enumValueOrDefault(preferences.getString(KEY_CAMERA_QUALITY, null), VideoQuality.P720),
            screenQuality = enumValueOrDefault(preferences.getString(KEY_SCREEN_QUALITY, null), VideoQuality.P720),
            screenFps = preferences.getInt(KEY_SCREEN_FPS, 30).let { if (it == 60) 60 else 30 },
            echoCancellation = preferences.getBoolean(KEY_ECHO_CANCELLATION, true),
            autoGainControl = preferences.getBoolean(KEY_AUTO_GAIN, true),
        )
    }

    fun save(settings: AppSettings, accessKey: String?) {
        preferences.edit()
            .putString(KEY_USERNAME, settings.username.trim())
            .putString(KEY_ENDPOINT, settings.tokenEndpoint.trim())
            .putString(KEY_INSTALLATION_ID, settings.installationId)
            .putString(KEY_AVATAR_ID, settings.avatarId)
            .putString(KEY_VOICE_MODE, settings.voiceMode.name)
            .putString(KEY_NOISE_MODE, settings.noiseMode.name)
            .putString(KEY_CAMERA_QUALITY, settings.cameraQuality.name)
            .putString(KEY_SCREEN_QUALITY, settings.screenQuality.name)
            .putInt(KEY_SCREEN_FPS, if (settings.screenFps == 60) 60 else 30)
            .putBoolean(KEY_ECHO_CANCELLATION, settings.echoCancellation)
            .putBoolean(KEY_AUTO_GAIN, settings.autoGainControl)
            .apply()
        if (accessKey != null) {
            if (accessKey.isBlank()) securePreferences.edit().remove(KEY_ACCESS_KEY).apply()
            else securePreferences.edit().putString(KEY_ACCESS_KEY, encrypt(accessKey.trim())).apply()
        }
    }

    fun accessKey(): String {
        val encrypted = securePreferences.getString(KEY_ACCESS_KEY, null) ?: return ""
        return runCatching { decrypt(encrypted) }.getOrElse { securePreferences.edit().remove(KEY_ACCESS_KEY).apply(); "" }
    }
    fun hasAccessKey(): Boolean = accessKey().isNotBlank()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_SIZE)))
        return String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), StandardCharsets.UTF_8)
    }
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val SECURE_PREFS_NAME = "secure_settings"
        private const val DEFAULT_ENDPOINT = ""
        private const val KEY_USERNAME = "username"
        private const val KEY_ENDPOINT = "token_endpoint"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_NOISE_MODE = "noise_mode"
        private const val KEY_CAMERA_QUALITY = "camera_quality"
        private const val KEY_SCREEN_QUALITY = "screen_quality"
        private const val KEY_SCREEN_FPS = "screen_fps"
        private const val KEY_NOISE_SUPPRESSION = "noise_suppression"
        private const val KEY_ECHO_CANCELLATION = "echo_cancellation"
        private const val KEY_AUTO_GAIN = "auto_gain_control"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "icanttalk_access_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
