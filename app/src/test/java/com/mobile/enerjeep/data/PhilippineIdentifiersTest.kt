package com.mobile.enerjeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the plate number, the driver's license ID number and the phone
 * number rules that every screen of the app uses.
 */
class PhilippineIdentifiersTest {

    /*
     * -------------------------
     * Plate number
     * -------------------------
     */

    @Test
    fun currentNewSeriesPlateIsAccepted() {
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("ABC1234"))
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("TXC1234"))
    }

    @Test
    fun plateWithSpaceOrHyphenIsAccepted() {
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("ABC 1234"))
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("ABC-1234"))
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("abc-1234"))
    }

    @Test
    fun legacyPlateFormatsOfExistingRecordsAreAccepted() {
        // These are the shapes that are already saved in the app.
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("TX1234"))
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("AB123"))
        assertTrue(PhilippineIdentifiers.isValidPlateNumber("TXR123"))
    }

    @Test
    fun malformedPlateIsRejected() {
        assertFalse(PhilippineIdentifiers.isValidPlateNumber(""))
        assertFalse(PhilippineIdentifiers.isValidPlateNumber("1234"))
        assertFalse(PhilippineIdentifiers.isValidPlateNumber("ABCD"))
        assertFalse(PhilippineIdentifiers.isValidPlateNumber("123ABC"))
        assertFalse(PhilippineIdentifiers.isValidPlateNumber("AB!@#"))
    }

    @Test
    fun plateIsNormalizedForComparisonOnly() {
        assertEquals(
            "ABC1234",
            PhilippineIdentifiers.normalizePlateNumber("abc-1234")
        )
        assertEquals(
            "ABC1234",
            PhilippineIdentifiers.normalizePlateNumber(" ABC 1234 ")
        )
    }

    @Test
    fun platesMatchIgnoresSeparatorsAndCase() {
        assertTrue(
            PhilippineIdentifiers.platesMatch("ABC 1234", "abc-1234")
        )
        assertTrue(
            PhilippineIdentifiers.platesMatch("ABC1234", "ABC1234")
        )
        assertFalse(
            PhilippineIdentifiers.platesMatch("ABC1234", "XYZ1234")
        )
        assertFalse(
            PhilippineIdentifiers.platesMatch("", "ABC1234")
        )
    }

    @Test
    fun plateErrorMessageIsShownOnlyWhenThePlateIsWrong() {
        assertNotNull(PhilippineIdentifiers.plateNumberError(""))
        assertNotNull(PhilippineIdentifiers.plateNumberError("1234"))
        assertNull(PhilippineIdentifiers.plateNumberError("ABC1234"))
    }

    /*
     * -------------------------
     * Driver license ID number
     * -------------------------
     */

    @Test
    fun commonLicenseNumbersAreAccepted() {
        assertTrue(PhilippineIdentifiers.isValidLicenseNumber("N01-12-345678"))
        assertTrue(PhilippineIdentifiers.isValidLicenseNumber("L12-345678"))
        assertTrue(PhilippineIdentifiers.isValidLicenseNumber("A1234567"))
    }

    @Test
    fun licenseNumberWithoutDigitsIsRejected() {
        assertFalse(PhilippineIdentifiers.isValidLicenseNumber("ABCDEF"))
        assertFalse(PhilippineIdentifiers.isValidLicenseNumber(""))
    }

    @Test
    fun licenseNumberWithWrongCharactersIsRejected() {
        assertFalse(PhilippineIdentifiers.isValidLicenseNumber("N01#12#345678"))
        assertFalse(PhilippineIdentifiers.isValidLicenseNumber("N01_12_345678"))
    }

    @Test
    fun veryLongLicenseNumberIsRejected() {
        val tooLong = "1".repeat(PhilippineIdentifiers.LICENSE_MAX_LENGTH + 1)
        assertFalse(PhilippineIdentifiers.isValidLicenseNumber(tooLong))
    }

    @Test
    fun licenseNumberErrorIsOnlyShownWhenTheValueIsWrong() {
        // An empty license number is allowed, the app never forces it.
        assertNull(PhilippineIdentifiers.licenseNumberError(""))
        assertNull(PhilippineIdentifiers.licenseNumberError("N01-12-345678"))
        assertNotNull(PhilippineIdentifiers.licenseNumberError("N01#12"))
    }

    /*
     * -------------------------
     * Phone numbers
     * -------------------------
     */

    @Test
    fun mobileNumbersAreAcceptedInEveryUsualShape() {
        assertTrue(PhilippineIdentifiers.isValidPhilippineMobileNumber("9171234567"))
        assertTrue(PhilippineIdentifiers.isValidPhilippineMobileNumber("09171234567"))
        assertTrue(PhilippineIdentifiers.isValidPhilippineMobileNumber("639171234567"))
        assertTrue(PhilippineIdentifiers.isValidPhilippineMobileNumber("+639171234567"))
    }

    @Test
    fun mobileNumberThatDoesNotStartWithNineIsRejected() {
        assertFalse(PhilippineIdentifiers.isValidPhilippineMobileNumber("8171234567"))
        assertFalse(PhilippineIdentifiers.isValidPhilippineMobileNumber("1234567890"))
    }

    @Test
    fun mobileNumberIsNormalizedToTheInternationalShape() {
        assertEquals(
            "+639171234567",
            PhilippineIdentifiers.normalizePhilippineMobileNumber("9171234567")
        )
        assertEquals(
            "+639171234567",
            PhilippineIdentifiers.normalizePhilippineMobileNumber("09171234567")
        )
        assertEquals(
            "+639171234567",
            PhilippineIdentifiers.normalizePhilippineMobileNumber("+639171234567")
        )
        assertNull(
            PhilippineIdentifiers.normalizePhilippineMobileNumber("123")
        )
    }
}
