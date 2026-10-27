package com.mobile.enerjeep.ui.kita

/**
 * Pure helpers for the Kita (earnings) list.
 *
 * The list is built from the Firestore collections "kita" and "ledger",
 * so the earnings are saved in the database and they are still there after
 * the app is closed or the driver signs out and signs in again.
 *
 * These helpers are plain functions on purpose: they can be checked with
 * unit tests without a device or a Firestore connection.
 */
object KitaHistory {

    /**
     * Merges the two sources of earnings into one list.
     *
     * A record can be described twice: once in "kita" and once in the
     * "ledger" document of the same dispatch. The sourceId of a record
     * is the identity of the transaction, so the same earning is only
     * listed once. This keeps the totals correct when a listener fires
     * again, when the screen is reopened, or when a dispatch was retried.
     */
    fun merge(
        dispatchRecords: List<KitaRecord>,
        manualRecords: List<KitaRecord>
    ): List<KitaRecord> {
        val merged = LinkedHashMap<String, KitaRecord>()

        (dispatchRecords + manualRecords).forEach { record ->
            val key = record.sourceId?.takeIf { it.isNotBlank() } ?: record.id
            if (!merged.containsKey(key)) {
                merged[key] = record
            }
        }

        // Newest earnings first.
        return merged.values.sortedByDescending { it.createdAtMillis }
    }

    /**
     * The "Kasaysayan" tab shows the complete history, so an earning that
     * was saved today is visible in the history as well as in "Ngayon".
     * The "Ngayon" tab keeps showing the earnings of today only.
     */
    fun visible(
        records: List<KitaRecord>,
        showHistory: Boolean
    ): List<KitaRecord> =
        if (showHistory) {
            records
        } else {
            records.filter { !it.isHistory }
        }

    /** The three totals of the summary card and the net amount. */
    data class Totals(
        val kita: Double = 0.0,
        val bayad: Double = 0.0,
        val multa: Double = 0.0
    ) {
        /**
         * Ang donasyon ay HIWALAY sa kita (boluntaryo) - hindi ito
         * kasama dito. Ang "malinis na kita" ay kita bawas ang
         * bayad at multa.
         */
        val net: Double get() = kita - (bayad + multa)
    }

    /**
     * Adds up the earnings, the payments and the fines of the given
     * records. The way the amounts are added is the way it was before,
     * so the numbers on the screen do not change.
     */
    fun totals(records: List<KitaRecord>): Totals {
        val kita = records
            .filter { it.type.equals("Kita", ignoreCase = true) }
            .sumOf { it.amount }

        val bayad = records
            .filter { it.type.equals("Bayad", ignoreCase = true) }
            .sumOf { it.amount }

        val multa = records
            .filter {
                // Records saved before the wording change use "Multiya".
                it.type.equals("Multa", ignoreCase = true) ||
                        it.type.equals("Multiya", ignoreCase = true)
            }
            .sumOf { it.amount }

        return Totals(
            kita = kita,
            bayad = bayad,
            multa = multa
        )
    }
}
