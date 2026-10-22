package com.mobile.enerjeep.ui.kita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the Kita (earnings) list:
 *
 *  - the same earning is never listed twice
 *  - the history keeps every earning that was saved
 *  - the totals are the same as before the change
 */
class KitaHistoryTest {

    private fun dispatch(
        id: String,
        amount: Double,
        millis: Long
    ) = KitaRecord(
        id = "ledger_$id",
        title = "Dispatch • ABC1234",
        date = "Oct 21, 2026",
        amount = amount,
        type = "Kita",
        isHistory = false,
        createdAtMillis = millis,
        sourceId = "ledger:$id"
    )

    private fun manual(
        id: String,
        amount: Double,
        type: String = "Kita",
        millis: Long
    ) = KitaRecord(
        id = id,
        title = "Record",
        date = "Oct 21, 2026",
        amount = amount,
        type = type,
        isHistory = false,
        createdAtMillis = millis,
        sourceId = "kita:$id"
    )

    @Test
    fun theSameEarningIsOnlyListedOnce() {
        // The same ledger document can reach the list twice, for example
        // when it matches the driver lookup and the plate lookup. Both
        // carry the same sourceId, so only one row is kept.
        val records = KitaHistory.merge(
            dispatchRecords = listOf(
                dispatch("abc", 500.0, 100L),
                dispatch("abc", 500.0, 100L)
            ),
            manualRecords = emptyList()
        )

        assertEquals(1, records.size)
        assertEquals(500.0, records[0].amount, 0.001)
    }

    @Test
    fun aManualRecordAndADispatchRecordAreBothListed() {
        // They come from two different collections, so they are two
        // different rows and both have to be visible.
        val records = KitaHistory.merge(
            dispatchRecords = listOf(dispatch("abc", 500.0, 100L)),
            manualRecords = listOf(manual("xyz", 500.0, millis = 100L))
        )

        assertEquals(2, records.size)
    }

    @Test
    fun differentEarningsAreAllListed() {
        val records = KitaHistory.merge(
            dispatchRecords = listOf(
                dispatch("abc", 500.0, 200L),
                dispatch("def", 300.0, 100L)
            ),
            manualRecords = listOf(manual("ghi", 100.0, millis = 150L))
        )

        assertEquals(3, records.size)
    }

    @Test
    fun theNewestEarningComesFirst() {
        val records = KitaHistory.merge(
            dispatchRecords = listOf(
                dispatch("old", 100.0, 100L),
                dispatch("new", 200.0, 300L)
            ),
            manualRecords = listOf(manual("mid", 150.0, millis = 200L))
        )

        assertEquals(200.0, records[0].amount, 0.001)
        assertEquals(150.0, records[1].amount, 0.001)
        assertEquals(100.0, records[2].amount, 0.001)
    }

    @Test
    fun theSameEarningIsNotAddedTwiceWhenTheListenerFiresAgain() {
        val first = KitaHistory.merge(
            dispatchRecords = listOf(dispatch("abc", 500.0, 100L)),
            manualRecords = emptyList()
        )

        val second = KitaHistory.merge(
            dispatchRecords = listOf(dispatch("abc", 500.0, 100L)),
            manualRecords = emptyList()
        )

        assertEquals(first.size, second.size)
        assertEquals(
            first.sumOf { it.amount },
            second.sumOf { it.amount },
            0.001
        )
    }

    @Test
    fun theHistoryTabShowsEveryEarning() {
        val records = listOf(
            manual("old", 100.0, millis = 100L),
            manual("new", 200.0, millis = 200L)
        )

        val history = KitaHistory.visible(records, showHistory = true)

        assertEquals(2, history.size)
    }

    @Test
    fun theCurrentTabShowsTheEarningsOfTheCurrentMonthOnly() {
        val current = manual("new", 200.0, millis = 200L).copy(isHistory = false)
        val old = manual("old", 100.0, millis = 100L).copy(isHistory = true)

        val currentList = KitaHistory.visible(
            listOf(current, old),
            showHistory = false
        )

        assertEquals(1, currentList.size)
        assertEquals(200.0, currentList[0].amount, 0.001)
    }

    @Test
    fun theTotalsAreKitaMinusBayadMinusMulta() {
        val records = listOf(
            manual("a", 1000.0, type = "Kita", millis = 100L),
            manual("b", 200.0, type = "Bayad", millis = 100L),
            manual("c", 50.0, type = "Multa", millis = 100L)
        )

        val totals = KitaHistory.totals(records)

        assertEquals(1000.0, totals.kita, 0.001)
        assertEquals(200.0, totals.bayad, 0.001)
        assertEquals(50.0, totals.multa, 0.001)
        assertEquals(750.0, totals.net, 0.001)
    }

    @Test
    fun theOldWordingOfMultaIsStillCounted() {
        val records = listOf(
            manual("a", 100.0, type = "Multiya", millis = 100L)
        )

        assertEquals(100.0, KitaHistory.totals(records).multa, 0.001)
    }

    @Test
    fun aRecordWithoutASourceIdIsStillListed() {
        val withoutSource = KitaRecord(
            id = "old_record",
            title = "Record",
            date = "Oct 21, 2026",
            amount = 100.0,
            type = "Kita",
            isHistory = false,
            createdAtMillis = 100L
        )

        val records = KitaHistory.merge(
            dispatchRecords = emptyList(),
            manualRecords = listOf(withoutSource)
        )

        assertEquals(1, records.size)
        assertTrue(records[0].id == "old_record")
    }
}
