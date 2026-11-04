package com.mobile.enerjeep.ui.home

import java.text.NumberFormat
import java.util.Locale

/**
 * Pure helpers for the two numbers on the admin dashboard.
 *
 * The dashboard card "Kabuuang Kita" shows how much money the cooperative
 * earned, and the card "Insidente" shows how many incidents are still
 * open. Both numbers used to be written by hand in the screen (a peso sign
 * with a zero, and a zero), so they never changed. They are built here from
 * the records of Firestore instead.
 *
 * These are plain functions on purpose: they can be checked with unit tests
 * without a device or a Firestore connection.
 */

/**
 * A row of the "ledger" collection, the way the dashboard needs it.
 *
 * `otherFees` is the dispatch deduction ("bawas") that was taken from the
 * driver - that money is what goes to the cooperative.
 */
data class DashboardLedgerEntry(
    val amount: Double? = null,
    val netAmount: Double? = null,
    val otherFees: Double? = null
)

/** Registered driver account row used by the dashboard's denominator. */
data class DashboardDriverAccount(
    val driverId: String,
    val isVehicleActive: Boolean = true,
    val status: String = "active"
)

/** Queue row used by the dashboard's active-driver counter. */
data class DashboardQueueEntry(
    val driverId: String,
    val status: String,
    val isCancelled: Boolean
)

/** A row of the "incidents" collection, the way the dashboard needs it. */
data class DashboardIncidentEntry(
    val isResolved: Boolean = false,
    val status: String = "open"
)

object DashboardSummary {

    /**
     * Count distinct drivers who remain active in the queue.
     * Cancelled entries remain stored at the bottom so they can be restored,
     * but are paused until the cancellation flag is cleared.
     */
    private val inactiveQueueStatuses =
        setOf("dispatched", "cancelled", "completed", "resolved")

    private fun DashboardQueueEntry.isActiveAndNotCancelled(): Boolean =
        driverId.isNotBlank() &&
                !isCancelled &&
                status.trim().lowercase(Locale.ROOT) !in inactiveQueueStatuses

    /**
     * Numerator: distinct drivers with a current, non-cancelled queue entry.
     */
    fun activeDriverCount(entries: List<DashboardQueueEntry>): Int =
        entries.asSequence()
            .filter { it.isActiveAndNotCancelled() }
            .map { it.driverId.trim() }
            .distinct()
            .count()

    /**
     * Denominator: distinct registered driver accounts that are enabled,
     * excluding drivers whose queue participation is currently paused by
     * cancellation. A driver with another valid active queue entry remains
     * counted, which protects against legacy duplicate queue documents.
     */
    fun totalActiveDriverCount(
        drivers: List<DashboardDriverAccount>,
        queueEntries: List<DashboardQueueEntry>
    ): Int {
        val cancelledDriverIds = queueEntries.asSequence()
            .filter { it.driverId.isNotBlank() }
            .filter {
                it.isCancelled ||
                        it.status.trim().equals("cancelled", ignoreCase = true)
            }
            .map { it.driverId.trim() }
            .toSet()

        val activeQueueDriverIds = queueEntries.asSequence()
            .filter { it.isActiveAndNotCancelled() }
            .map { it.driverId.trim() }
            .toSet()

        return drivers.asSequence()
            .filter { it.driverId.isNotBlank() }
            .filter { it.isVehicleActive }
            .filter { !it.status.trim().equals("inactive", ignoreCase = true) }
            .map { it.driverId.trim() }
            .filter { driverId ->
                driverId !in cancelledDriverIds ||
                        driverId in activeQueueDriverIds
            }
            .distinct()
            .count()
    }

    /**
     * "Kabuuang Kita": ang kita ng KOOPERATIBA/Terminal = ang bawas
     * (cut) sa bawat dispatch. Hindi ang kita ng driver - nasa Kita tab
     * ng driver iyon. Ang mga ledger row na walang bawas (hal. lumang
     * "Kita" record) ay walang ambag dito.
     */
    fun totalSales(entries: List<DashboardLedgerEntry>): Double =
        entries.sumOf { it.otherFees ?: 0.0 }

    /**
     * "Insidente": the number of the incidents that are still open. An
     * incident is closed when it is marked as resolved, which is the same
     * rule the Insidente screen uses to split "Di-naayos" from "Naayos".
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
