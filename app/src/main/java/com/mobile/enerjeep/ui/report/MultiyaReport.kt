package com.mobile.enerjeep.ui.report

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure helpers for the "Ulat ng Multa" (Multa report).
 *
 * A fine can be saved in two places in this app:
 *
 *  - in the "kita" collection, with type = "Multa" (or the old wording
 *    "Multiya")
 *  - in the "ledger" collection, with feeType = "Multa" (this is what the
 *    "Talaan" screen writes when the admin picks "Multa" as the type of
 *    the collection)
 *
 * Both are real fines and both belong in the report. Everything else in
 * the ledger (a dispatch earning, a terminal fee, a donation) must NOT be
 * counted as a fine.
 *
 * The helpers are plain functions so they can be checked with unit tests
 * without a device or a Firestore connection.
 */
object MultiyaReport {

    /** The wording used by the ledger screen for a fine. */
    const val LEDGER_FEE_TYPE = "Multa"

    /** The wording used by the "kita" collection. */
    const val KITA_TYPE = "Multa"

    /** The old wording still found in older records. */
    const val KITA_TYPE_OLD = "Multiya"

    /**
     * True when the "kita" record is a fine.
     */
    fun isKitaMultiya(type: String?): Boolean {
        val clean = type?.trim() ?: return false

        return clean.equals(KITA_TYPE, ignoreCase = true) ||
                clean.equals(KITA_TYPE_OLD, ignoreCase = true)
    }

    /**
     * True when the ledger transaction is a fine. Only the feeType is
     * checked, so a dispatch earning or a terminal fee is never counted.
     */
    fun isLedgerMultiya(feeType: String?): Boolean {
        val clean = feeType?.trim() ?: return false

        return clean.equals(LEDGER_FEE_TYPE, ignoreCase = true)
    }

    /**
     * The identity of a fine that is written in both the "kita" and the
     * "ledger" collection. The driver, the amount and the day are used
     * because a fine does not have its own number in the app.
     *
     * When the same fine is written twice, the key is the same, so the
     * report counts it only once.
     */
    fun multiyaKey(item: ReportMultiyaItem): String {
        val amountKey = String.format(Locale.US, "%.2f", item.amount)

        return listOf(
            item.plateNumber.trim().uppercase(Locale.ROOT),
            item.driverName.trim().lowercase(Locale.ROOT),
            amountKey,
            dayKey(item)
        ).joinToString("|")
    }

    /**
     * The day of the fine, used for the "Ngayon" split and for the
     * duplicate check.
     */
    fun dayKey(item: ReportMultiyaItem): String {
        if (item.createdAtMillis > 0L) {
            return SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(Date(item.createdAtMillis))
        }

        val parsed = parseDate(item.date) ?: return item.date.trim()

        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(parsed)
    }

    /**
     * True when the fine belongs to today ("Ngayon").
     */
    fun isToday(item: ReportMultiyaItem): Boolean {
        if (item.createdAtMillis > 0L) {
            val key = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

            return key.format(Date(item.createdAtMillis)) ==
                    key.format(Date())
        }

        val parsed = parseDate(item.date) ?: return false

        val key = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        return key.format(parsed) == key.format(Date())
    }

    /**
     * The result of the Multa report.
     */
    data class Summary(
        val current: List<ReportMultiyaItem> = emptyList(),
        val history: List<ReportMultiyaItem> = emptyList(),
        // Every fine, without a fine counted twice. The "Kasaysayan" tab
        // shows this list, so a fine that was made today is written in the
        // history as well.
        val all: List<ReportMultiyaItem> = emptyList(),
        val total: Double = 0.0
    ) {
        val currentTotal: Double get() = current.sumOf { it.amount }
    }

    /**
     * Builds the report from the fines of the "kita" collection and the
     * fines of the "ledger" collection.
     *
     * A fine that exists in both collections is only counted once, so the
     * total of the report is not inflated.
     */
    fun build(
        kitaMultiya: List<ReportMultiyaItem>,
        ledgerMultiya: List<ReportMultiyaItem>
    ): Summary {
        val seen = LinkedHashSet<String>()
        val all = mutableListOf<ReportMultiyaItem>()

        (kitaMultiya + ledgerMultiya).forEach { item ->
            if (seen.add(multiyaKey(item))) {
                all += item
            }
        }

        /*
         * Newest first. The display text ("MMM dd, yyyy") must NOT be
         * used for sorting: string order is not chronological
         * ("Sep" > "May" alphabetically but not by date). Sort by the
         * saved time, falling back to the parsed date for old records.
         */
        val sorted = all.sortedByDescending {
            if (it.createdAtMillis > 0L) {
                it.createdAtMillis
            } else {
                parseDate(it.date)?.time ?: 0L
            }
        }

        return Summary(
            current = sorted.filter { isToday(it) },
            history = sorted.filter { !isToday(it) },
            all = sorted,
            total = all.sumOf { it.amount }
        )
    }

    /**
     * Reads the date text of a record. The app stores the date as
     * "MMM dd, yyyy"; the other formats are the ones older records use.
     */
    fun parseDate(dateText: String?): Date? {
        val clean = dateText?.trim().orEmpty()

        if (clean.isEmpty()) return null

        val patterns = listOf(
            "MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "MM/dd/yyyy"
        )

        for (locale in listOf(Locale.getDefault(), Locale.ENGLISH).distinct()) {
            for (pattern in patterns) {
                try {
                    val formatter = SimpleDateFormat(pattern, locale).apply {
                        isLenient = false
                    }
                    return formatter.parse(clean)
                } catch (_: Exception) {
                    // Try the next supported legacy date format.
                }
            }
        }

        return null
    }
}
