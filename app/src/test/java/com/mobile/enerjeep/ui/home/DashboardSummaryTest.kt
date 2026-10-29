package com.mobile.enerjeep.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the two numbers of the admin dashboard:
 *
 *  - "Kabuuang Kita" adds up the DISPATCH DEDUCTIONS (bawas) of the
 *    ledger - that is the income of the cooperative, not the earnings
 *    of the drivers (those stay in the driver's Kita tab)
 *  - "Insidente" counts the incidents that are still open
 */
class DashboardSummaryTest {

    @Test
    fun anEmptyLedgerGivesAZeroTotal() {
        assertEquals(0.0, DashboardSummary.totalSales(emptyList()), 0.001)
    }

    @Test
    fun theTotalIsTheSumOfEveryDispatchDeduction() {
        val entries = listOf(
            DashboardLedgerEntry(amount = 50.0, netAmount = 20.0, otherFees = 30.0),
            DashboardLedgerEntry(amount = 75.0, netAmount = 45.0, otherFees = 30.0),
            DashboardLedgerEntry(amount = 25.0, netAmount = 5.0, otherFees = 20.0)
        )

        assertEquals(80.0, DashboardSummary.totalSales(entries), 0.001)
    }

    @Test
    fun theDriverEarningsAreNotCountedAsCooperativeIncome() {
        // A dispatch row has a big net amount (the money of the driver)
        // and a small deduction (the money of the cooperative). Only the
        // deduction belongs to "Kabuuang Kita".
        val entries = listOf(
            DashboardLedgerEntry(amount = 100.0, netAmount = 80.0, otherFees = 20.0)
        )

        assertEquals(20.0, DashboardSummary.totalSales(entries), 0.001)
    }

    @Test
    fun aCollectionWithoutADeductionCountsAsZero() {
        // Old "Kita" records only carry the driver income - they add
        // nothing to the cooperative income.
        val entries = listOf(
            DashboardLedgerEntry(),
            DashboardLedgerEntry(amount = 30.0, netAmount = 30.0),
            DashboardLedgerEntry(amount = 50.0, netAmount = 40.0, otherFees = 10.0)
        )

        assertEquals(10.0, DashboardSummary.totalSales(entries), 0.001)
    }

    @Test
    fun anEmptyIncidentListGivesZeroOpenIncidents() {
        assertEquals(0, DashboardSummary.openIncidentCount(emptyList()))
    }

    @Test
    fun onlyTheIncidentsThatAreStillOpenAreCounted() {
        val entries = listOf(
            DashboardIncidentEntry(isResolved = false, status = "open"),
            DashboardIncidentEntry(isResolved = true, status = "resolved"),
            DashboardIncidentEntry(isResolved = false, status = "open")
        )

        assertEquals(2, DashboardSummary.openIncidentCount(entries))
    }

    @Test
    fun anIncidentThatIsMarkedAsResolvedIsNotOpen() {
        // The flag and the status can both be there, and both mean the
        // same thing: the incident is already closed.
        val entries = listOf(
            DashboardIncidentEntry(isResolved = true, status = "open")
        )

        assertEquals(0, DashboardSummary.openIncidentCount(entries))
    }

    @Test
    fun theStatusIsReadWithoutCaringAboutTheCase() {
        val entries = listOf(
            DashboardIncidentEntry(isResolved = false, status = "Resolved"),
            DashboardIncidentEntry(isResolved = false, status = "RESOLVED")
        )

        assertEquals(0, DashboardSummary.openIncidentCount(entries))
    }

    @Test
    fun theMoneyIsWrittenWithThePesoSign() {
        assertEquals("₱0", DashboardSummary.formatPeso(0.0))
        assertEquals("₱1,500", DashboardSummary.formatPeso(1500.0))
        assertEquals("₱50", DashboardSummary.formatPeso(50.0))
    }

    @Test
    fun theMoneyIsWrittenTheSameWayAsTheRestOfTheApp() {
        // The cards of the app write the amount with a peso sign and
        // without the decimals when they are whole pesos.
        assertTrue(DashboardSummary.formatPeso(1234.5).startsWith("₱1,234"))
    }
}
