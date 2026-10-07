package com.turinglabs.quota.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.turinglabs.quota.model.UsagePayload
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class DisplayMode { REMAINING, USED;

    fun value(remaining: Int): Int = if (this == REMAINING) remaining else 100 - remaining
}

/**
 * Server address, preferences and the last payload. The device token is stored encrypted with an
 * Android Keystore key that never leaves the device.
 */
class QuotaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("quota", Context.MODE_PRIVATE)
    private val cacheFile = File(context.applicationContext.filesDir, "usage.json")

    var serverUrl: String?
        get() = prefs.getString("serverUrl", null)
        set(value) = prefs.edit().putString("serverUrl", value).apply()

    var displayMode: DisplayMode
        get() = runCatching { DisplayMode.valueOf(prefs.getString("displayMode", null) ?: "") }.getOrDefault(DisplayMode.REMAINING)
        set(value) = prefs.edit().putString("displayMode", value.name).apply()

    /** Set when the app is started with the `sample` extra: app and widget show sample accounts (screenshots). */
    var showsSample: Boolean
        get() = prefs.getBoolean("sample", false)
        set(value) = prefs.edit().putBoolean("sample", value).apply()

    val isPaired: Boolean
        get() = serverUrl != null && token() != null

    fun token(): String? = prefs.getString("token", null)?.let(TokenCipher::decrypt)

    fun saveToken(token: String): Boolean {
        val encrypted = TokenCipher.encrypt(token) ?: return false
        return prefs.edit().putString("token", encrypted).commit()
    }

    fun cachedPayload(): UsagePayload? =
        runCatching { UsagePayload.parse(cacheFile.readText()) }.getOrNull()

    fun saveCache(json: String) {
        runCatching { cacheFile.writeText(json) }
    }

    fun unpair() {
        prefs.edit().remove("token").apply()
        cacheFile.delete()
    }
}

private object TokenCipher {
    private const val ALIAS = "quota-device-token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
