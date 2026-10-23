package com.mobile.enerjeep.data

/**
 * Helpers for the two emergency contact fields of a driver:
 *
 *  - emergencyContactName   : the name of the person to call
 *  - emergencyContactNumber : the phone number of that person
 *
 * Older accounts only have one combined "emergencyContact" value. The
 * helpers below only READ that legacy value, so nothing is erased and
 * nothing crashes when an old account is opened.
 */
object EmergencyContact {

    /** Longest emergency contact name the app accepts. */
    const val NAME_MAX_LENGTH = PhilippineIdentifiers.EMERGENCY_NAME_MAX_LENGTH

    /** A name can never carry more digits than a phone number. */
    const val NAME_MAX_DIGITS = 11

    /**
     * Keeps a name inside the allowed size. Digits are allowed (a name
     * like "Ana 2" is odd but harmless) but only up to the phone number
     * limit, so a full phone number can never be smuggled in as a name.
     */
    fun sanitizeName(raw: String): String {
        var digitCount = 0
        val builder = StringBuilder()

        for (character in raw) {
            if (builder.length >= NAME_MAX_LENGTH) break

            if (character.isDigit()) {
                if (digitCount >= NAME_MAX_DIGITS) continue
                digitCount++
            }

            builder.append(character)
        }

        return builder.toString().trim()
    }

    /**
     * Keeps only the characters of a phone number: an optional "+" and
     * digits. Nothing else is accepted so the field cannot hold a name.
     */
    fun sanitizeNumber(raw: String): String {
        val hasPlus = raw.trimStart().startsWith("+")
        val digits = raw.filter { it.isDigit() }

        val maxDigits = when {
            hasPlus || digits.startsWith("63") -> 12
            digits.startsWith("9") -> 10
            else -> 11
        }

        return (if (hasPlus) "+" else "") + digits.take(maxDigits)
    }

    /** A name is required to be free of characters that cannot be typed. */
    fun isValidName(value: String): Boolean {
        val clean = value.trim()

        if (clean.isEmpty()) return false
        if (clean.length > NAME_MAX_LENGTH) return false
        if (clean.count { it.isDigit() } > NAME_MAX_DIGITS) return false
        if (clean.any { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '-' || it == '\'' }) {
            return clean.any { it.isLetter() }
        }

        return false
    }

    /**
     * The emergency number must be a Philippine MOBILE number in one of
     * two shapes only:
     *
     *   09XXXXXXXXX     (local format)
     *   +639XXXXXXXXX   (international format)
     *
     * Landline-style numbers (like "0312345678") are NOT accepted.
     */
    fun isValidNumber(value: String): Boolean {
        val clean = value.trim()

        return clean.matches(Regex("^09\d{9}$")) ||
                clean.matches(Regex("^\+639\d{9}$"))
    }

    /**
     * Reads the old combined "emergencyContact" value.
     *
     * The migration is deterministic and never splits a value in half:
     *  - a value made only of digits is a phone number
     *  - anything else is a name
     *
     * The original value is never changed, so an admin can always still
     * see what the account really contains.
     */
    fun splitLegacy(value: String?): LegacyEmergencyContact {
        val clean = value?.trim().orEmpty()

        if (clean.isEmpty()) {
            return LegacyEmergencyContact(name = "", number = "")
        }

        val onlyDigitsAndSymbols = clean.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }

        return if (onlyDigitsAndSymbols && clean.any { it.isDigit() }) {
            LegacyEmergencyContact(name = "", number = clean)
        } else {
            LegacyEmergencyContact(name = clean, number = "")
        }
    }

    /**
     * Builds the value of the old combined field from the two new ones
     * so old screens and exports keep working.
     */
    fun combine(name: String?, number: String?): String {
        val cleanName = name?.trim().orEmpty()
        val cleanNumber = number?.trim().orEmpty()

        return when {
            cleanName.isNotEmpty() && cleanNumber.isNotEmpty() ->
                "$cleanName ($cleanNumber)"

            cleanName.isNotEmpty() -> cleanName
            else -> cleanNumber
        }
    }
}

/** The two values of an emergency contact. */
data class LegacyEmergencyContact(
    val name: String,
    val number: String
)
