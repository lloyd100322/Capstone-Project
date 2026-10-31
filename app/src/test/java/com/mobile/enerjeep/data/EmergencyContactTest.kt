package com.mobile.enerjeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the two emergency contact fields and the reading of the old
 * accounts that only have one combined value.
 */
class EmergencyContactTest {

    @Test
    fun nameIsKeptInsideTheAllowedSize() {
        val longName = "A".repeat(80)
        assertEquals(
            EmergencyContact.NAME_MAX_LENGTH,
            EmergencyContact.sanitizeName(longName).length
        )
    }

    @Test
    fun nameCannotHideAFullPhoneNumber() {
        val nameWithNumbers = "Ana" + "1".repeat(30)
        val cleaned = EmergencyContact.sanitizeName(nameWithNumbers)

        assertTrue(cleaned.count { it.isDigit() } <= EmergencyContact.NAME_MAX_DIGITS)
    }

    @Test
    fun numberKeepsOnlyDigitsAndThePlusSign() {
        assertEquals(
            "09171234567",
            EmergencyContact.sanitizeNumber("0917 123 4567")
        )
        assertEquals(
            "+639171234567",
            EmergencyContact.sanitizeNumber("+63 917 123 4567")
        )
    }

    @Test
    fun validNameIsAccepted() {
        assertTrue(EmergencyContact.isValidName("Maria Santos"))
        assertTrue(EmergencyContact.isValidName("Maria D. Santos"))
    }

    @Test
    fun emptyOrNumberOnlyNameIsRejected() {
        assertFalse(EmergencyContact.isValidName(""))
        assertFalse(EmergencyContact.isValidName("09171234567"))
    }

    @Test
    fun validNumberIsAccepted() {
        // Only 09XXXXXXXXX or +639XXXXXXXXX are accepted.
        assertTrue(EmergencyContact.isValidNumber("09171234567"))
        assertTrue(EmergencyContact.isValidNumber("+639171234567"))
    }

    @Test
    fun invalidNumberIsRejected() {
        assertFalse(EmergencyContact.isValidNumber(""))
        assertFalse(EmergencyContact.isValidNumber("12"))
        assertFalse(EmergencyContact.isValidNumber("Maria"))

        // Landline-style numbers must never pass (regression: "03..."
        // used to be accepted through the landline fallback).
        assertFalse(EmergencyContact.isValidNumber("0312345678"))
        assertFalse(EmergencyContact.isValidNumber("03123456789"))

        // Without the +63 prefix the number must start with 09.
        assertFalse(EmergencyContact.isValidNumber("9171234567"))
        assertFalse(EmergencyContact.isValidNumber("639171234567"))
    }

    @Test
    fun oldCombinedNumberIsReadAsTheNumberField() {
        val legacy = EmergencyContact.splitLegacy("09171234567")

        assertEquals("", legacy.name)
        assertEquals("09171234567", legacy.number)
    }

    @Test
    fun oldCombinedNameIsReadAsTheNameField() {
        val legacy = EmergencyContact.splitLegacy("Maria Santos")

        assertEquals("Maria Santos", legacy.name)
        assertEquals("", legacy.number)
    }

    @Test
    fun emptyOldValueGivesTwoEmptyFields() {
        val legacy = EmergencyContact.splitLegacy("")

        assertEquals("", legacy.name)
        assertEquals("", legacy.number)
    }

    @Test
    fun oldValueIsNeverErasedWhenTheFieldsAreCombinedAgain() {
        assertEquals(
            "Maria Santos (09171234567)",
            EmergencyContact.combine("Maria Santos", "09171234567")
        )
        assertEquals(
            "Maria Santos",
            EmergencyContact.combine("Maria Santos", "")
        )
        assertEquals(
            "09171234567",
            EmergencyContact.combine("", "09171234567")
        )
    }
}
