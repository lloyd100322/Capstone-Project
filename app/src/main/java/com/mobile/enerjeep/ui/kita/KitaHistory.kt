package com.mobile.enerjeep.ui.kita

/**
 * Pure helpers for the Kita (earnings) list.
 *
 * ANG KITA NG DRIVER AY DISPATCH EARNINGS LAMANG - ang halagang
 * natatanggap niya tuwing dinidispatch ang biyahe. Walang gastos
 * (diesel, toll, atbp.), walang multa, at walang anumang ibang
 * record na binabawas dito ("walang bawas na kahit ano"). Ang mga
 * sariling gastos ng driver ay hindi na nire-record sa app.
 *
 * Ang listahan ay galing sa Firestore collection na "ledger" (mga
 * dispatch record), kaya nasa database ang kita at naroroon pa rin
 * kahit isara ang app o mag-sign out at mag-sign in muli ang driver.
 *
 * These helpers are plain functions on purpose: they can be checked
 * with unit tests without a device or a Firestore connection.
 */
object KitaHistory {

    /**
     * Keeps one row per earning and returns the newest first.
     *
     * The same earning can reach the list twice, for example when a
     * ledger document matches both the driver lookup and the plate
     * lookup, or when a listener fires again. The sourceId of a record
     * is the identity of the transaction, so the same earning is only
     * listed once and never counted twice.
     */
    fun merge(records: List<KitaRecord>): List<KitaRecord> {
        val merged = LinkedHashMap<String, KitaRecord>()

        records.forEach { record ->
            val key = record.sourceId?.takeIf { it.isNotBlank() } ?: record.id
            if (!merged.containsKey(key)) {
                merged[key] = record
            }
        }

        // Newest earnings first.
        return merged.values.sortedByDescending { it.createdAtMillis }
    }

    /**
     * Ang kabuuang kita = kabuuan ng mga kita. WALANG BAWAS: hindi
     * binabawas dito ang gastos, multa, o donasyon. Ang donasyon ay
     * nasa /donations lang (boluntaryo) at hindi kailanman hinahatak
     * mula sa kita ng driver.
     */
    fun total(records: List<KitaRecord>): Double = records.sumOf { it.amount }
}
