package com.mobile.enerjeep.data

object FirestoreCollections {
    const val DISPATCHES = "dispatches"
    const val USERS = "users"
    const val QUEUE = "queue"
    const val INCIDENTS = "incidents"
    const val LEDGER = "ledger"
    const val KITA = "kita"
    const val DONATIONS = "donations"
}

object FirestoreIds {
    fun queue(driverId: String): String = "queue_${driverId}_${System.currentTimeMillis()}"
    fun incident(): String = "incident_${System.currentTimeMillis()}"
    fun ledger(): String = "ledger_${System.currentTimeMillis()}"
    fun kita(): String = "kita_${System.currentTimeMillis()}"
    fun donation(): String = "donation_${System.currentTimeMillis()}"
}
