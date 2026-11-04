package com.mobile.enerjeep.ui.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LedgerRequestFingerprintTest {

    @Test
    fun plateFormattingDoesNotCreateADifferentRetryKey() {
        val first = LedgerRequestFingerprint.create(
            actorPhone = "+639171000001",
            plateNumber = "ABC-1234",
            amount = 500.0,
            feeType = "Multa",
            incidentId = "incident-1"
        )
        val retry = LedgerRequestFingerprint.create(
            actorPhone = "+639171000001",
            plateNumber = "abc 1234",
            amount = 500.0,
            feeType = "Multa",
            incidentId = "incident-1"
        )

        assertEquals(first, retry)
    }

    @Test
    fun aChangedAmountCreatesADifferentRetryKey() {
        val first = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Multa", null)
        val changed = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 501.0, "Multa", null)

        assertNotEquals(first, changed)
    }

    @Test
    fun aChangedFeeTypeCreatesADifferentRetryKey() {
        val first = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Multa", null)
        val changed = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Collection", null)

        assertNotEquals(first, changed)
    }

    @Test
    fun aChangedIncidentReferenceCreatesADifferentRetryKey() {
        val first = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Multa", "incident-1")
        val changed = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Multa", "incident-2")

        assertNotEquals(first, changed)
    }

    @Test
    fun fingerprintDoesNotExposeTheOriginalPlateOrIncidentId() {
        val fingerprint = LedgerRequestFingerprint.create(
            actorPhone = "+639171000001",
            plateNumber = "ABC-1234",
            amount = 500.0,
            feeType = "Multa",
            incidentId = "incident-private-123"
        )

        assertFalse(fingerprint.contains("ABC1234"))
        assertFalse(fingerprint.contains("incident-private-123"))
        assertEquals(64, fingerprint.length)
    }

    @Test
    fun differentAdminAccountsDoNotSharePendingRequestKeys() {
        val first = LedgerRequestFingerprint.create("+639171000001", "ABC-1234", 500.0, "Multa", null)
        val second = LedgerRequestFingerprint.create("+639171000002", "ABC-1234", 500.0, "Multa", null)

        assertNotEquals(first, second)
    }
}
