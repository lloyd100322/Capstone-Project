package com.mobile.enerjeep.ui.kita

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the Kita (earnings) list:
 *
 *  - the same earning is never listed twice
 *  - the newest earning comes first
 *  - the total is the plain sum - WALANG BAWAS (no expenses, fines
 *    or donations are ever subtracted from a driver's earnings)
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
        isHistory = false,
        createdAtMillis = millis,
        sourceId = "ledger:$id"
    )

    @Test
    fun theSameEarningIsOnlyListedOnce() {
        // The same ledger document can reach the list twice, for example
        // when it matches the driver lookup and the plate lookup. Both
        // carry the same sourceId, so only one row is kept.
        val records = KitaHistory.merge(
            listOf(
                dispatch("abc", 500.0, 100L),
                dispatch("abc", 500.0, 100L)
            )
        )

        assertEquals(1, records.size)
        assertEquals(500.0, records[0].amount, 0.001)
    }

    @Test
    fun differentEarningsAreAllListed() {
        val records = KitaHistory.merge(
            listOf(
                dispatch("abc", 500.0, 200L),
                dispatch("def", 300.0, 100L)
            )
        )

        assertEquals(2, records.size)
    }

    @Test
    fun theNewestEarningComesFirst() {
        val records = KitaHistory.merge(
            listOf(
                dispatch("old", 100.0, 100L),
                dispatch("new", 200.0, 300L),
                dispatch("mid", 150.0, 200L)
            )
        )

        assertEquals(200.0, records[0].amount, 0.001)
        assertEquals(150.0, records[1].amount, 0.001)
        assertEquals(100.0, records[2].amount, 0.001)
    }

    @Test
    fun theSameEarningIsNotAddedTwiceWhenTheListenerFiresAgain() {
        val first = KitaHistory.merge(
            listOf(dispatch("abc", 500.0, 100L))
        )

        val second = KitaHistory.merge(
            listOf(dispatch("abc", 500.0, 100L))
        )

        assertEquals(first.size, second.size)
        assertEquals(
            first.sumOf { it.amount },
            second.sumOf { it.amount },
            0.001
        )
    }

    @Test
    fun aRecordWithoutASourceIdIsStillListed() {
        val withoutSource = KitaRecord(
            id = "old_record",
            title = "Dispatch • ABC1234",
            date = "Oct 21, 2026",
            amount = 100.0,
            isHistory = false,
            createdAtMillis = 100L
        )

        val records = KitaHistory.merge(listOf(withoutSource))

        assertEquals(1, records.size)
        assertTrue(records[0].id == "old_record")
    }

    @Test
    fun theTotalIsThePlainSumOfEveryEarning() {
        val records = listOf(
            dispatch("a", 1000.0, 100L),
            dispatch("b", 450.0, 200L),
            dispatch("c", 1320.0, 300L)
        )

        assertEquals(2770.0, KitaHistory.total(records), 0.001)
    }

    @Test
    fun anEmptyListTotalsToZero() {
        assertEquals(0.0, KitaHistory.total(emptyList()), 0.001)
    }
}
