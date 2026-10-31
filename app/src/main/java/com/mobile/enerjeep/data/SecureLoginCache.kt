package com.mobile.enerjeep.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.mobile.enerjeep.EnerJeepApp
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.TimeUnit

/**
 * Short-lived, encrypted device-local password verifier.
 *
 * This is only a temporary pre-OTP convenience for a device that has
 * previously completed a successful password + OTP login. The server-side
 * password check after OTP remains authoritative. A new device or cleared
 * app data has no cache, so it safely falls back to the OTP-first flow.
 */
object SecureLoginCache {

    private const val PREFS_NAME = "enerjeep_secure_login_cache"
    private const val KEY_ALIAS = "enerjeep_login_verifier_aes_v1"
    private const val CACHE_VERSION = "1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
    private const val GCM_TAG_BITS = 128

    /**
     * Returns:
     * true  = cached password matches
     * false = cached password does not match
     * null  = no valid cache; caller must use the normal OTP-first flow
     */
    fun verify(phone: String, password: String): Boolean? {
        if (phone.isBlank() || password.isEmpty()) return null

        val prefs = EnerJeepApp.instance.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        val cacheKey = cacheKey(phone)
        val stored = prefs.getString(cacheKey, null) ?: return null

        return try {
            val parts = stored.split(":", limit = 2)
            if (parts.size != 2) {
                clear(phone)
                return null
            }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(phone.toByteArray(StandardCharsets.UTF_8))

            val plain = String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )
            val fields = plain.split("|")
            if (fields.size != 5 || fields[0] != CACHE_VERSION) {
                clear(phone)
                return null
            }

            val salt = fields[1]
            val hash = fields[2]
            val iterations = fields[3].toIntOrNull()
            val savedAt = fields[4].toLongOrNull()
            val now = System.currentTimeMillis()

            if (
                salt.isBlank() ||
                hash.isBlank() ||
                iterations == null ||
                iterations <= 0 ||
                savedAt == null ||
                now < savedAt ||
                now - savedAt > CACHE_TTL_MILLIS
            ) {
                clear(phone)
                return null
            }

            PasswordHasher.verify(password, salt, hash, iterations)
        } catch (_: Exception) {
            clear(phone)
            null
        }
    }

    /** Save a server-verified PBKDF2 verifier in encrypted local storage. */
    fun save(
        phone: String,
        salt: String,
        hash: String,
        iterations: Int
    ) {
        if (
            phone.isBlank() ||
            salt.isBlank() ||
            hash.isBlank() ||
            iterations <= 0
        ) return

        try {
            val plain = listOf(
                CACHE_VERSION,
                salt,
                hash,
                iterations.toString(),
                System.currentTimeMillis().toString()
            ).joinToString("|")

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(phone.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(
                plain.toByteArray(StandardCharsets.UTF_8)
            )
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)

            EnerJeepApp.instance.getSharedPreferences(
                PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            ).edit()
                .putString(cacheKey(phone), encoded)
                .apply()
        } catch (_: Exception) {
            // A cache failure must never prevent a real OTP login.
        }
    }

    /** Remove a single account's cached verifier. */
    fun clear(phone: String) {
        if (phone.isBlank()) return
        EnerJeepApp.instance.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        ).edit()
            .remove(cacheKey(phone))
            .apply()
    }

    private fun cacheKey(phone: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(phone.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
