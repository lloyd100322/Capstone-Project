package com.mobile.enerjeep.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The accountability trail: every admin action is written to the
 * /activityLogs collection so the superadmin can always see WHO did
 * WHAT and WHEN. Entries can never be edited or deleted (firestore
 * rules) - the trail must stay honest.
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
        ).filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Hindi kilala" }

        log(
            firestore = firestore,
            actorName = actorName,
            actorRole = profile?.role?.name ?: "",
            action = action,
            detail = detail
        )
    }

    fun log(
        firestore: FirebaseFirestore,
        actorName: String,
        actorRole: String,
        action: String,
        detail: String
    ) {
        try {
            firestore
                .collection(FirestoreCollections.ACTIVITY_LOGS)
                .document()
                .set(
                    mapOf(
                        "actorName" to actorName,
                        "actorRole" to actorRole,
                        "action" to action,
                        "detail" to detail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (_: Exception) {
            // The audit trail must never break the actual action.
        }
    }
}
