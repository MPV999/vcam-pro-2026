package com.vcampro.ultimate.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vcampro.ultimate.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Security Centre backing store.
 *
 * The app lock PIN is salted, hashed and then encrypted with an AES-GCM key that
 * lives in the Android Keystore, so the stored value is useless on another
 * device and the key material is not exportable.
 */
class SecurityRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {

    suspend fun setPin(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pin.length >= 4) { "PIN phải có ít nhất 4 số" }
            val encrypted = encrypt(hashPin(pin))
            settings.setAppLockPinHash(encrypted)
            settings.setAppLockEnabled(true)
        }
    }

    suspend fun verifyPin(pin: String, stored: String): Boolean = withContext(Dispatchers.IO) {
        if (stored.isBlank()) return@withContext false
        val decrypted = runCatching { decrypt(stored) }.getOrNull() ?: return@withContext false
        constantTimeEquals(decrypted, hashPin(pin))
    }

    suspend fun disableLock() {
        settings.setAppLockEnabled(false)
        settings.setAppLockPinHash("")
    }

    /** Writes a JSON export of everything the app stores about the user. */
    suspend fun exportPersonalData(mediaSummary: String): File = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("application", "VCAM PRO ULTIMATE VIP 2026")
            put("exportedAt", System.currentTimeMillis())
            put("mediaSummary", mediaSummary)
            put(
                "note",
                "Ứng dụng không lưu tài khoản, không có backend và không tải media lên máy chủ."
            )
        }
        val file = File(context.cacheDir, "vcam_personal_data_${System.currentTimeMillis()}.json")
        file.writeText(payload.toString(2))
        file
    }

    suspend fun deletePersonalData() = withContext(Dispatchers.IO) {
        settings.resetAll()
        runCatching { context.getDatabasePath("vcam_pro.db").delete() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    /** Removes temporary exports and imported copies. Returns freed bytes. */
    fun cleanTemporaryFiles(): Long {
        var freed = 0L
        runCatching {
            context.cacheDir.listFiles()?.forEach { file ->
                val size = file.length()
                if (file.deleteRecursively()) freed += size
            }
        }
        runCatching {
            context.externalCacheDir?.listFiles()?.forEach { file ->
                val size = file.length()
                if (file.deleteRecursively()) freed += size
            }
        }
        return freed
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "vcam-pro-2026:$pin".toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(digest.digest(salted), Base64.NO_WRAP)
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].code xor right[index].code)
        }
        return difference == 0
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":")
        require(parts.size == 2) { "Dữ liệu mã hóa không hợp lệ" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val payload = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vcam_app_lock_key"
    }
}
