package com.mobile.enerjeep.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Nag-iisang pasukan para sa LAHAT ng audit-log entries.
 *
 * ISANG klaseng entry para sa lahat (admin o driver): ang `actorName` at
 * `actorPhone` ay galing sa SARILING session ng nagsulat - hindi sa
 * argumento - kaya hindi ito pwedeng palitan ng kahit sinong kliyente.
 * Tine-check din ng firestore.rules na ang `actorPhone` ay laging ang
 * phone number ng naka-login, kaya hindi na mapapanggap ng sinuman ang
 * audit trail sa pangalan ng iba.
 *
 * Never throw here - the audit trail must never break the actual action
 * it is recording.
 */
object ActivityLogger {

    fun log(
        firestore: FirebaseFirestore,
        userSession: UserSession,
        action: String,
        detail: String
    ) {
        val profile = userSession.userProfile
        val actorName = listOf(
            profile?.firstName.orEmpty(),
            profile?.lastName.orEmpty()
        )
            .joinToString(" ")
            .ifBlank { "Hindi kilala" }

        try {
            firestore
                .collection(FirestoreCollections.ACTIVITY_LOGS)
                .document()
                .set(
                    mapOf(
                        "actorName" to actorName,
                        "actorPhone" to profile?.phoneNumber.orEmpty().trim(),
                        // .orEmpty() (hindi `?:`) — ang `to` infix ay
                        // mas mataas ang precedence kaysa elvis, kaya
                        // nagiging (a to b) ?: c ang "x to y ?: z" at
                        // nag-e-error sa mapOf.
                        "actorRole" to profile?.role?.name.orEmpty(),
                        "action" to action,
                        "detail" to detail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (_: Exception) {
            // Never throw here - the audit trail must never break the
            // actual action it is recording.
        }
    }
}
