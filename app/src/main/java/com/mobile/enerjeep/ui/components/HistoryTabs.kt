package com.mobile.enerjeep.ui.components

/**
 * The rule of the "Kasaysayan" (history) tabs.
 *
 * The "Ngayon" and "Kasalukuyan" tabs show the records of the moment. The
 * "Kasaysayan" tab is the complete list, so a record that was made today
 * is written in the history as well. The Kita screen already worked this
 * way; the same rule is used by the incidents, the ledger and the reports.
 *
 * These helpers are plain functions on purpose: they can be checked with
 * unit tests without a device or a Firestore connection.
 */
object HistoryTabs {

    /**
     * The records a tab has to show.
     *
     * @param items every record that was loaded
     * @param isCurrent tells whether a record belongs to the current tab
     * @param showHistory true for the "Kasaysayan" tab
     */
    fun <T> visible(
        items: List<T>,
        isCurrent: (T) -> Boolean,
        showHistory: Boolean
    ): List<T> =
        if (showHistory) {
            // The history is the complete list, including the records of today.
            items
        } else {
            items.filter(isCurrent)
        }
}
