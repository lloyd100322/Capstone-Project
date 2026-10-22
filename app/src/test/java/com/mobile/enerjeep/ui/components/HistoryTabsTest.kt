package com.mobile.enerjeep.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the rule of the "Kasaysayan" (history) tabs:
 *
 *  - "Ngayon" and "Kasalukuyan" keep their own narrow meaning
 *  - "Kasaysayan" is the complete list, so a record that was made today
 *    is written in the history as well
 */
class HistoryTabsTest {

    private data class Record(
        val name: String,
        val isToday: Boolean
    )

    @Test
    fun theCurrentTabShowsOnlyTheRecordsOfTheMoment() {
        val records = listOf(
            Record("today", isToday = true),
            Record("older", isToday = false)
        )

        val visible = HistoryTabs.visible(
            items = records,
            isCurrent = { it.isToday },
            showHistory = false
        )

        assertEquals(1, visible.size)
        assertEquals("today", visible[0].name)
    }

    @Test
    fun theHistoryTabShowsEveryRecord() {
        val records = listOf(
            Record("today", isToday = true),
            Record("older", isToday = false)
        )

        val visible = HistoryTabs.visible(
            items = records,
            isCurrent = { it.isToday },
            showHistory = true
        )

        assertEquals(2, visible.size)
    }

    @Test
    fun aRecordThatWasMadeTodayIsInTheHistoryAsWell() {
        // This is the change the user asked for: what is in "Ngayon" or
        // "Kasalukuyan" is also written in "Kasaysayan".
        val records = listOf(
            Record("today", isToday = true)
        )

        val visible = HistoryTabs.visible(
            items = records,
            isCurrent = { it.isToday },
            showHistory = true
        )

        assertEquals(1, visible.size)
        assertEquals("today", visible[0].name)
    }

    @Test
    fun anEmptyListGivesAnEmptyList() {
        val visible = HistoryTabs.visible(
            items = emptyList<Record>(),
            isCurrent = { it.isToday },
            showHistory = true
        )

        assertEquals(0, visible.size)
    }
}
