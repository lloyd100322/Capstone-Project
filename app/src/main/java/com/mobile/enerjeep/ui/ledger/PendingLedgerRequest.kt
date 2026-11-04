package com.mobile.enerjeep.ui.ledger

import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Builds a stable, non-reversible key for the exact manual-ledger request.
 * Plate formatting differences are normalized the same way as the callable.
 */
internal object LedgerRequestFingerprint {
    fun create(
        actorPhone: String,
        plateNumber: String,
        amount: Double,
        feeType: String,
        incidentId: String?
    ): String {
        val normalizedPlate = plateNumber
            .filter { it.isLetterOrDigit() }
            .uppercase(Locale.ROOT)
        val normalizedIncidentId = incidentId?.trim().orEmpty()
        val payload = listOf(
            actorPhone.trim(),
            normalizedPlate,
            java.lang.Double.toString(amount),
            feeType.trim(),
            normalizedIncidentId
        ).joinToString("\u001F")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}

/**
 * Persists request IDs until the server confirms that the matching ledger
 * transaction exists. A retry after a timeout, ViewModel recreation, or app
 * restart therefore reuses the same server idempotency key.
 *
 * Only the SHA-256 fingerprint and a random UUID are stored in app-private
 * preferences. No plate, amount, incident detail, or personal data is stored.
 */
internal class PendingLedgerRequestStore(
    private val preferences: SharedPreferences
) {
    fun getOrCreate(fingerprint: String): String = synchronized(LOCK) {
        require(fingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "Invalid manual-ledger request fingerprint."
        }

        val key = KEY_PREFIX + fingerprint
        val existing = preferences.getString(key, null)
        if (existing != null && isUuid(existing)) {
            return@synchronized existing
        }

        val requestId = UUID.randomUUID().toString()
        check(preferences.edit().putString(key, requestId).commit()) {
            "Hindi ma-save ang retry protection sa device. Subukan muli."
        }
        requestId
    }

    /** Remove the pending key only after the server has confirmed success. */
    fun markCompleted(fingerprint: String, requestId: String) {
        synchronized(LOCK) {
            val key = KEY_PREFIX + fingerprint
            if (preferences.getString(key, null) == requestId) {
                // If this local cleanup fails, retaining the UUID is safe:
                // the server will return the already-recorded result on retry.
                preferences.edit().remove(key).commit()
            }
        }
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value).toString() == value
    } catch (_: IllegalArgumentException) {
        false
    }

    private companion object {
        const val KEY_PREFIX = "pending_manual_ledger_"
        val LOCK = Any()
    }
}
