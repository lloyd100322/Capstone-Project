package com.mobile.enerjeep.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes the account password before it is ever saved to Firestore.
 *
 * The app must NEVER store a readable password. Only a salted PBKDF2
 * hash is written (see FirestoreCollections.USER_SECRETS), so a leaked
 * database, a backup or a debug log never exposes the password itself.
 *
 * The password is a second factor on top of the SMS OTP: the OTP proves
 * the user owns the phone number, the password proves they own the
 * account. Both checks happen against trusted sources (Firebase Auth
 * for the OTP, a one-way hash for the password).
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Work factor for NEW hashes - OWASP guidance for
     * PBKDF2-HmacSHA256 starts at 210,000 iterations. Older
     * /userSecrets documents were written with LEGACY_ITERATIONS; they
     * keep verifying through their stored `iterations` field and are
     * silently rewritten at the current work factor after a successful
     * login (see LoginViewModel.verifyPassword).
     */
    const val ITERATIONS = 210_000

    /** Pre-upgrade default (documents without an `iterations` field). */
    const val LEGACY_ITERATIONS = 12_000

    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    private const val HEX_DIGITS = "0123456789abcdef"

    /**
     * A fresh random salt together with the hash of the password and
     * the work factor it was computed with. Persist `iterations` next
     * to the hash so the value can be changed later without breaking
     * older documents.
     */
    data class SaltedHash(
        val salt: String,
        val hash: String,
        val iterations: Int = ITERATIONS
    )

    /** Hashes a password with a brand-new random salt. */
    fun hash(password: String): SaltedHash {
        val saltBytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = toHex(saltBytes)
        return SaltedHash(
            salt = saltHex,
            hash = hashWithSalt(password, saltHex, ITERATIONS),
            iterations = ITERATIONS
        )
    }

    /**
     * Hashes a password with an existing salt (hex encoded) at the
     * given work factor.
     */
    fun hashWithSalt(
        password: String,
        saltHex: String,
        iterations: Int = ITERATIONS
    ): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            fromHex(saltHex),
            iterations,
            HASH_BITS
        )
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return toHex(factory.generateSecret(spec).encoded)
    }

    /**
     * True when the password matches the stored hash. Compares in
     * constant time so a timing attack cannot guess the hash.
     */
    fun verify(
        password: String,
        saltHex: String,
        hashHex: String,
        iterations: Int = ITERATIONS
    ): Boolean {
        if (saltHex.isBlank() || hashHex.isBlank()) return false

        val candidate = try {
            hashWithSalt(password, saltHex, iterations)
        } catch (_: Exception) {
            return false
        }

        return MessageDigest.isEqual(
            candidate.lowercase().toByteArray(),
            hashHex.lowercase().toByteArray()
        )
    }

    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    private fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex string" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
