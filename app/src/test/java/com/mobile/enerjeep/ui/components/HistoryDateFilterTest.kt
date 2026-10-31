package com.mobile.enerjeep.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Tests for the shared history date filter: a record that was created
 * today must show under the "Ngayon" filter even when its displayed
 * date cannot be parsed, and it must stay in the general history.
 */
class HistoryDateFilterTest {

    private data class Item(
        val date: String,
        val millis: Long = 0L
    )

    private val todayMillis = System.currentTimeMillis()

    private val yesterdayMillis = todayMillis - 24L * 60 * 60 * 1000

    private fun todayText(): String =
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(todayMillis))

    private fun itemsOf(vararg items: Item) = items.toList()

    private fun filter(
        records: List<Item>,
        filter: HistoryFilterState
    ): List<Item> = filterHistoryRecords(
        records = records,
        dateText = { it.date },
        filter = filter,
        createdAtMillis = { it.millis.takeIf { millis -> millis > 0 } }
    )

    @Test
    fun `record created today shows under Ngayon using its timestamp`() {
        val records = itemsOf(
            Item(date = "", millis = todayMillis),
            Item(date = "", millis = yesterdayMillis)
        )

        val result = filter(records, HistoryFilterState(period = HistoryPeriod.TODAY))

        assertEquals(1, result.size)
        assertEquals(todayMillis, result.first().millis)
    }

    @Test
    fun `record with unparseable date and no millis is excluded from Ngayon but kept in Lahat`() {
        // "3:45 PM" is the queue time format: it cannot be parsed as a date.
        val records = itemsOf(Item(date = "3:45 PM"))

        val ngayon = filter(records, HistoryFilterState(period = HistoryPeriod.TODAY))
        val lahat = filter(records, HistoryFilterState(period = HistoryPeriod.ALL))

        assertTrue(ngayon.isEmpty())
        assertEquals(1, lahat.size)
    }

    @Test
    fun `record with readable date text still matches Ngayon without a timestamp`() {
        val records = itemsOf(Item(date = todayText()))

        val result = filter(records, HistoryFilterState(period = HistoryPeriod.TODAY))

        assertEquals(1, result.size)
    }

    @Test
    fun `specific date filter matches only the chosen date`() {
        val chosen = LocalDate.now().minusDays(3)
        val records = itemsOf(
            Item(date = chosen.toString(), millis = 0L),
            Item(date = todayText(), millis = todayMillis)
        )

        val result = filter(
            records,
            HistoryFilterState(
                period = HistoryPeriod.SPECIFIC_DATE,
                selectedDate = chosen
            )
        )

        assertEquals(1, result.size)
        assertEquals(chosen.toString(), result.first().date)
    }

    @Test
    fun `parseHistoryDate reads the queue history format`() {
        val formatted = SimpleDateFormat(
            "MMM dd, yyyy • hh:mm a",
            Locale.getDefault()
        ).format(Date(todayMillis))

        assertNotNull(parseHistoryDate(formatted))
    }

    @Test
    fun `parseHistoryDate rejects blank and unknown formats`() {
        assertNull(parseHistoryDate(null))
        assertNull(parseHistoryDate("   "))
        assertNull(parseHistoryDate("hindi ito petsa"))
    }

    @Test
    fun `newest first keeps records of the same day stable`() {
        val records = itemsOf(
            Item(date = todayText(), millis = todayMillis),
            Item(date = todayText(), millis = todayMillis - 1000),
            Item(date = todayText(), millis = todayMillis - 2000)
        )

        val result = filter(records, HistoryFilterState(period = HistoryPeriod.ALL))

        assertEquals(
            listOf(todayMillis, todayMillis - 1000, todayMillis - 2000),
            result.map { it.millis }
        )
    }
}
