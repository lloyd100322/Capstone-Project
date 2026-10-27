package com.mobile.enerjeep.ui.home

import java.text.NumberFormat
import java.util.Locale

/**
 * Pure helpers for the two numbers on the admin dashboard.
 *
 * The dashboard card "Kabuuang Kita" shows how much money the cooperative
 * collected, and the card "Insidente" shows how many incidents are still
 * open. Both numbers used to be written by hand in the screen (a peso sign
 * with a zero, and a zero), so they never changed. They are built here from
 * the records of Firestore instead.
 *
 * These are plain functions on purpose: they can be checked with unit tests
 * without a device or a Firestore connection.
 */

/** A row of the "ledger" collection, the way the dashboard needs it. */
data class DashboardLedgerEntry(
    val amount: Double? = null,
    val netAmount: Double? = null
)

/** A row of the "incidents" collection, the way the dashboard needs it. */
data class DashboardIncidentEntry(
    val isResolved: Boolean = false,
    val status: String = "open"
)

object DashboardSummary {

    /**
     * The money of one ledger row. The net amount is used when it is there,
     * because that is the amount the other reports of the app use as well.
     */
    fun amount(entry: DashboardLedgerEntry): Double =
        entry.netAmount ?: entry.amount ?: 0.0

    /**
     * "Kabuuang Kita": the sum of every collection of the ledger.
     */
    fun totalSales(entries: List<DashboardLedgerEntry>): Double =
        entries.sumOf { amount(it) }

    /**
     * "Insidente": the number of the incidents that are still open. An
     * incident is closed when it is marked as resolved, which is the same
     * rule the Insidente screen uses to split "Bukas" from "Naayos".
     */
    fun openIncidentCount(entries: List<DashboardIncidentEntry>): Int =
        entries.count { entry ->
            !entry.isResolved &&
                    !entry.status.equals("resolved", ignoreCase = true)
        }

    /**
     * The peso sign and the amount, written the same way the rest of the
     * app writes money.
     */
    fun formatPeso(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = if ((amount % 1.0) == 0.0) 0 else 2
        }
        return "₱" + formatter.format(amount)
    }
}
