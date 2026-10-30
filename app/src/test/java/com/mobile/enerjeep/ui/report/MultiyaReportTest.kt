package com.mobile.enerjeep.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Checks the "Ulat ng Multa" (Multa report):
 *
 *  - a fine recorded in the "ledger" (Talaan) is in the report
 *  - a normal dispatch earning or terminal fee is NOT a fine
 *  - a fine that is written twice is only counted once
 */
class MultiyaReportTest {

    private fun today(): String =
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())

    private fun earlier(): String =
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis() - 40L * 24L * 60L * 60L * 1000L))

    private fun item(
        plate: String = "ABC1234",
        name: String = "Juan Dela Cruz",
        date: String = today(),
        amount: Double = 500.0
    ) = ReportMultiyaItem(
        plateNumber = plate,
        driverName = name,
        date = date,
        amount = amount
    )

    @Test
    fun onlyTheMultaFeeTypeOfTheLedgerIsAFine() {
        assertTrue(MultiyaReport.isLedgerMultiya("Multa"))
        assertFalse(MultiyaReport.isLedgerMultiya("Terminal Fee"))
        assertFalse(MultiyaReport.isLedgerMultiya("Dispatch"))
        assertFalse(MultiyaReport.isLedgerMultiya(null))
    }

    @Test
    fun aFineFromTheLedgerIsInTheReport() {
        val summary = MultiyaReport.build(
            listOf(item(amount = 250.0))
        )

        assertEquals(1, summary.current.size)
        assertEquals(250.0, summary.total, 0.001)
        assertEquals(250.0, summary.currentTotal, 0.001)
    }

    @Test
    fun aFineThatIsWrittenTwiceIsCountedOnce() {
        val summary = MultiyaReport.build(
            listOf(
                item(amount = 500.0),
                item(amount = 500.0)
            )
        )

        assertEquals(1, summary.current.size)
        assertEquals(500.0, summary.total, 0.001)
    }

    @Test
    fun twoDifferentFinesAreBothCounted() {
        val summary = MultiyaReport.build(
            listOf(
                item(amount = 500.0),
                item(plate = "XYZ9876", name = "Pedro Santos", amount = 300.0)
            )
        )

        assertEquals(2, summary.current.size)
        assertEquals(800.0, summary.total, 0.001)
    }

    @Test
    fun anOlderFineGoesToTheHistory() {
        val summary = MultiyaReport.build(
            listOf(item(date = earlier(), amount = 500.0))
        )

        assertEquals(0, summary.current.size)
        assertEquals(1, summary.history.size)
        assertEquals(500.0, summary.total, 0.001)
    }

    @Test
    fun theTotalOfTheReportIsTheSumOfEveryFine() {
        val summary = MultiyaReport.build(
            listOf(
                item(date = today(), amount = 100.0),
                item(date = earlier(), amount = 200.0),
                item(plate = "XYZ9876", name = "Pedro Santos", date = today(), amount = 300.0)
            )
        )

        assertEquals(600.0, summary.total, 0.001)
        assertEquals(400.0, summary.currentTotal, 0.001)
    }

    @Test
    fun theCompleteListHasEveryFineAndCountsAFineThatIsWrittenTwiceOnlyOnce() {
        val summary = MultiyaReport.build(
            listOf(
                item(date = today(), amount = 100.0),
                item(date = earlier(), amount = 200.0),
                item(date = today(), amount = 100.0)
            )
        )

        // The fine of today is in the list, the older fine is in the
        // list, and the fine that is written twice is there once.
        assertEquals(2, summary.all.size)
        assertEquals(300.0, summary.total, 0.001)
    }

    @Test
    fun theHistoryIsSortedNewestFirstByRealDateNotByText() {
        /*
         * String order of "MMM dd, yyyy" is not chronological ("Sep"
         * sorts after "May" even when May is later). The list must be
         * ordered by the saved time of each fine.
         */
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        val summary = MultiyaReport.build(
            listOf(
                ReportMultiyaItem(
                    plateNumber = "ABC1234",
                    driverName = "Juan Dela Cruz",
                    date = "Sep 15, 2025",
                    amount = 100.0,
                    createdAtMillis = now - 30L * day
                ),
                ReportMultiyaItem(
                    plateNumber = "ABC1234",
                    driverName = "Juan Dela Cruz",
                    date = "May 01, 2026",
                    amount = 200.0,
                    createdAtMillis = now - 2L * day
                )
            )
        )

        assertEquals(2, summary.history.size)
        assertEquals(200.0, summary.history[0].amount, 0.001)
        assertEquals(100.0, summary.history[1].amount, 0.001)
    }
}
