package com.mobile.enerjeep.data

import java.util.UUID

object FirestoreCollections {
    const val USERS = "users"

    /**
     * Salted password hashes. Readable only by their owner (see
     * firestore.rules). Never put anything here that a screen needs.
     */
    const val USER_SECRETS = "userSecrets"

    const val QUEUE = "queue"
    const val INCIDENTS = "incidents"
    const val LEDGER = "ledger"
    const val KITA = "kita"
    const val DONATIONS = "donations"

    /** App settings (queue rotation cutoff, deduction tiers, ...). */
    const val SETTINGS = "settings"

    /** Accountability trail - superadmin-only reads (see rules). */
    const val ACTIVITY_LOGS = "activityLogs"

    /** Superadmin-managed "available admin" emergency list. */
    const val EMERGENCY_CONTACTS = "emergencyContacts"
}

object FirestoreIds {
    /*
     * IDs use random UUIDs, never the wall clock. Two devices writing in
     * the same millisecond used to produce the same id, and `set()` then
     * silently overwrote the other record (a lost incident or earning).
     */
    fun queue(driverId: String): String =
        "queue_${driverId}_${UUID.randomUUID()}"

    fun incident(): String =
        "incident_${UUID.randomUUID()}"

    fun ledger(): String =
        "ledger_${UUID.randomUUID()}"

    fun kita(): String =
        "kita_${UUID.randomUUID()}"

    fun donation(): String =
        "donation_${UUID.randomUUID()}"
}
