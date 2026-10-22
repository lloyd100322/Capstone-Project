package com.mobile.enerjeep.data

import java.util.Locale

/**
 * Validation helpers for the Philippine identifiers the app stores:
 * plate number, driver's license ID number and phone numbers.
 *
 * Everything here is a plain function so the same rule is used by every
 * screen and every ViewModel. The rules are intentionally forgiving for
 * values that were saved by older versions of the app: the app never
 * rewrites an existing record just to make it fit a pattern.
 */
object PhilippineIdentifiers {

    /** Longest driver license number the app accepts. */
    const val LICENSE_MAX_LENGTH = 20

    /** Longest emergency contact name the app accepts. */
    const val EMERGENCY_NAME_MAX_LENGTH = 50

    /*
     * -------------------------
     * Plate number
     * -------------------------
     * The current LTO new-series format for a four-wheeled motor vehicle
     * (jeepney / for-hire) is three letters followed by four digits, for
     * example ABC1234. Older records in this app use the legacy
     * two-or-three letter series with up to four digits (TX 1234, AB 123),
     * so those are accepted as well.
     */

    /**
     * Removes the presentation separators and upper-cases the letters.
     * ABC-1234, "ABC 1234" and abc1234 all become ABC1234.
     * The value that is actually stored in Firestore is NOT changed by
     * this helper; it is only used to compare two plates safely.
     */
    fun normalizePlateNumber(raw: String?): String =
        raw.orEmpty()
            .trim()
            .uppercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    /** True when both plates are the same plate, whatever separators were used. */
    fun platesMatch(first: String?, second: String?): Boolean {
        val left = normalizePlateNumber(first)
        val right = normalizePlateNumber(second)
        return left.isNotEmpty() && left == right
    }

    /**
     * Checks the plate number without assuming a single official pattern.
     * A plate must have letters first and digits last, and must fit one of
     * the LTO series that are actually in use.
     */
    fun isValidPlateNumber(raw: String?): Boolean {
        val normalized = normalizePlateNumber(raw)
        if (normalized.isEmpty()) return false

        val letters = normalized.takeWhile { it.isLetter() }
        val digits = normalized.drop(letters.length)

        if (letters.isEmpty() || digits.isEmpty()) return false
        if (!letters.all { it in 'A'..'Z' }) return false
        if (!digits.all { it.isDigit() }) return false

        // Current new-series format: ABC1234.
        if (letters.length == 3 && digits.length == 4) return true

        // Legacy series that older records still use: TX1234, AB123, ...
        if (letters.length in 2..3 && digits.length in 1..4) return true

        return false
    }

    fun plateNumberError(raw: String?): String? {
        if (raw.isNullOrBlank()) {
            return "Pakilagay ang plate number ng driver."
        }

        if (!isValidPlateNumber(raw)) {
            return "Ang plate number ay dapat titik na sinusundan ng " +
                    "numero, halimbawa ABC1234."
        }

        return null
    }

    /*
     * -------------------------
     * Driver's license ID number
     * -------------------------
     * LTO does not publish one single universal pattern for the driver's
     * license ID number, and the numbers already saved in this app were
     * entered by hand. The validator therefore only checks what can be
     * checked safely: the characters that are allowed, the length, and the
     * fact that the value contains at least one digit. It is NOT a check
     * against an official LTO list.
     */

    fun normalizeLicenseNumber(raw: String?): String =
        raw.orEmpty()
            .trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    fun isValidLicenseNumber(raw: String?): Boolean {
        val clean = raw.orEmpty().trim()
        if (clean.isEmpty()) return false
        if (clean.length > LICENSE_MAX_LENGTH) return false
        if (!clean.all { it.isLetterOrDigit() || it == '-' || it == ' ' }) return false
        if (!clean.any { it.isDigit() }) return false
        return true
    }

    fun licenseNumberError(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        if (!isValidLicenseNumber(raw)) {
            return "Ang Driver License ID Number ay dapat may titik at " +
                    "numero lamang (halimbawa N01-12-345678)."
        }

        return null
    }

    /*
     * -------------------------
     * Phone numbers
     * -------------------------
     * One shared rule for every phone number the app accepts, so the app
     * never ends up with two different definitions of a valid number.
     */

    /**
     * True for a Philippine mobile number in any of the usual shapes:
     * 9XXXXXXXXX, 09XXXXXXXXX, 639XXXXXXXXX or +639XXXXXXXXX.
     */
    fun isValidPhilippineMobileNumber(raw: String?): Boolean {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when (digits.length) {
            10 -> digits.startsWith("9")
            11 -> digits.startsWith("09")
            12 -> digits.startsWith("639")
            else -> false
        }
    }

    /**
     * True for a mobile number or for a landline-style number
     * (7 to 11 digits, no international prefix).
     */
    fun isValidPhilippinePhoneNumber(raw: String?): Boolean {
        val clean = raw.orEmpty().trim()
        if (clean.isEmpty()) return false

        if (isValidPhilippineMobileNumber(clean)) return true

        return !clean.startsWith("+") &&
                clean.length in 7..11 &&
                clean.all { it.isDigit() }
    }

    /** Turns any accepted mobile shape into +639XXXXXXXXX. */
    fun normalizePhilippineMobileNumber(raw: String?): String? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (!isValidPhilippineMobileNumber(digits)) return null

        val local = when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("09") -> digits.drop(1)
            digits.length == 12 && digits.startsWith("639") -> digits.drop(2)
            else -> return null
        }

        return "+63$local"
    }
}
