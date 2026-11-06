package com.mobile.enerjeep.ui.queue

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mobile.enerjeep.data.FirestoreCollections
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AWTOMATIKONG RESET NG PILA (cut-off) - shared engine.
 *
 * PANGUNAHIN: isang NAKA-SCHEDULE na Cloud Function (functions/index.js
 * - dailyQueueReset, bawat oras, Asia/Manila) ang nagre-reset ng pila
 * ayon sa permanenteng account-creation order kahit SARADO ang app.
 *
 * PANTULONG (fallback) sa Android - dalawang lugar ang tumatawag:
 *   1. ContentViewModel - AGAD sa pag-login (kahit anong screen ang
 *      buksan) + isang watch habang naka-bukas ang app.
 *   2. QueueViewModel (Pila tab) - catch-up + live reset habang nakabukas.
 *
 * Idempotent ito: ang unang makaka-transaction ang nagre-reset, ang
 * iba ay nakikita na "tapos na ang araw" (lastQueueDay) kaya umuurong.
 */
object QueueRotation {

    data class Result(val rotated: Boolean, val cutoffHour: Int)

    /** Default na cut-off (21 = 9 PM) - pareho ng QueueViewModel.CUTOFF_HOUR. */
    const val CUTOFF_HOUR = QueueViewModel.CUTOFF_HOUR

    fun metaReference(firestore: FirebaseFirestore) =
        firestore
            .collection(FirestoreCollections.SETTINGS)
            .document("queueRotation")

    /**
     * The queue "day" flips at the cut-off hour (default 21:00) - after
     * the cut-off the queue already belongs to the next operating day.
     */
    fun queueDayId(
        now: Date = Date(),
        cutoffHour: Int = QueueViewModel.CUTOFF_HOUR
    ): String {
        val manila = TimeZone.getTimeZone("Asia/Manila")
        val calendar = Calendar.getInstance(manila)
        calendar.time = now
        if (calendar.get(Calendar.HOUR_OF_DAY) >= cutoffHour) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = manila
        }.format(calendar.time)
    }

    /** Millis hanggang sa susunod na cut-off hour (hindi bababa sa 1 minuto). */
    fun millisUntilNextCutoff(cutoffHour: Int): Long {
        val manila = TimeZone.getTimeZone("Asia/Manila")
        val now = Calendar.getInstance(manila)
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, cutoffHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    /**
     * Isang tseke ng araw: kapag lampas na sa cut-off, ibinabalik ang
     * aktibong pila sa permanenteng roster order ayon sa account creation
     * date (pinakamatagal na account muna). Ang basa ng queue ay SA LOOB
     * ng transaction kaya walang stale na listahan.
     */
    suspend fun rotateOnce(firestore: FirebaseFirestore): Result {
        val queueCollection = firestore.collection(FirestoreCollections.QUEUE)

        /*
         * Kolektahin muna ang mga ID ng pila (isang simpleng basa). Ang
         * bawat dokumento ay binabasa SA LOOB ng transaction gamit ang
         * transaction.get(document) - per document ang basa dahil walang
         * transaction.get(query) ang mobile SDK.
         */
        val queueControlReference = firestore
            .collection(FirestoreCollections.SETTINGS)
            .document("queueControl")
        val observedQueueRevision = queueControlReference.get().await()
            .getLong("revision") ?: 0L
        val queueIds = queueCollection
            .get()
            .await()
            .documents
            .map { it.id }

        return firestore.runTransaction { transaction ->
            val queueControl = transaction.get(queueControlReference)
            val currentRevision = queueControl.getLong("revision") ?: 0L
            if (currentRevision != observedQueueRevision) {
                throw IllegalStateException("Nagbago ang pila habang nagre-reset. Ulitin ang rotation.")
            }
            val metaReference = metaReference(firestore)
            val meta = transaction.get(metaReference)
            val lastReset = meta.getString("lastQueueDay")

            // Ang cut-off hour ay settable ng superadmin (default 21 = 9 PM).
            val hour = (meta.getLong("cutoffHour")?.toInt()
                ?: QueueViewModel.CUTOFF_HOUR)
                .coerceIn(0, 23)

            val today = queueDayId(Date(), hour)

            if (lastReset == today) {
                return@runTransaction Result(false, hour)
            }

            // Basahin ang bawat entry SA LOOB ng transaction (laging sariwa).
            val queueDocs = queueIds.map { id ->
                transaction.get(queueCollection.document(id))
            }.filter { it.exists() }

            // The first queue record is created with the driver account.
            // Keeping this mapping within queue history avoids requiring drivers
            // to list /users (which Firestore rules deliberately prohibit).
            val accountCreatedAtByDriver = queueDocs
                .filter { !it.getString("driverId").isNullOrBlank() }
                .groupBy { it.getString("driverId").orEmpty() }
                .mapValues { (_, entries) ->
                    entries.mapNotNull { document ->
                        document.getTimestamp("accountCreatedAt")?.toDate()?.time
                            ?: document.getTimestamp("createdAt")?.toDate()?.time
                    }.minOrNull()
                }

            fun accountCreationMillis(document: DocumentSnapshot): Long =
                document.getTimestamp("accountCreatedAt")?.toDate()?.time
                    ?: accountCreatedAtByDriver[
                        document.getString("driverId").orEmpty()
                    ]
                    ?: document.getTimestamp("createdAt")?.toDate()?.time
                    ?: Long.MAX_VALUE

            val rotationCursorUpdates = mutableMapOf<String, Any>()

            listOf(QueueViewModel.ROUTE_MA, QueueViewModel.ROUTE_AM)
                .forEach { route ->
                    val active = queueDocs
                        .filter {
                            QueueViewModel.isActiveStatus(it.getString("status")) &&
                                    it.getBoolean("isCancelled") != true &&
                                    QueueViewModel.sameRoute(
                                        it.getString("route"),
                                        route
                                    )
                        }
                        .sortedWith(
                            compareBy<DocumentSnapshot> { accountCreationMillis(it) }
                                .thenBy {
                                    it.getString("driverId").orEmpty().ifBlank { it.id }
                                }
                        )

                    val routeName = if (route == QueueViewModel.ROUTE_MA) {
                        "Marilao"
                    } else {
                        "Monumento"
                    }
                    val cursorDriverField = "rotationStart${routeName}DriverId"
                    val cursorCreatedAtField =
                        "rotationStart${routeName}CreatedAtMillis"
                    val previousLeaderId = meta.getString(cursorDriverField)
                    val previousLeaderCreatedAt =
                        meta.getLong(cursorCreatedAtField)

                    // The saved cursor is yesterday's #1. Advance to the next
                    // active account in permanent creation order, wrapping around.
                    val startIndex = when {
                        active.size <= 1 -> 0
                        !previousLeaderId.isNullOrBlank() -> {
                            val previousIndex = active.indexOfFirst {
                                it.getString("driverId").orEmpty()
                                    .ifBlank { it.id } == previousLeaderId
                            }
                            if (previousIndex >= 0) {
                                (previousIndex + 1) % active.size
                            } else if (previousLeaderCreatedAt != null) {
                                val nextIndex = active.indexOfFirst { document ->
                                    val createdAt = accountCreationMillis(document)
                                    val driverId = document.getString("driverId")
                                        .orEmpty().ifBlank { document.id }
                                    createdAt > previousLeaderCreatedAt ||
                                            (createdAt == previousLeaderCreatedAt &&
                                                    driverId > previousLeaderId)
                                }
                                if (nextIndex >= 0) nextIndex else 0
                            } else {
                                1
                            }
                        }
                        previousLeaderCreatedAt != null -> {
                            val nextIndex = active.indexOfFirst { document ->
                                val createdAt = accountCreationMillis(document)
                                val driverId = document.getString("driverId")
                                    .orEmpty().ifBlank { document.id }
                                createdAt > previousLeaderCreatedAt ||
                                        (createdAt == previousLeaderCreatedAt &&
                                                driverId > previousLeaderId.orEmpty())
                            }
                            if (nextIndex >= 0) nextIndex else 0
                        }
                        // First reset after this change: move permanent #1
                        // to the end so permanent #2 becomes the new #1.
                        else -> 1
                    }

                    val rotated = active.drop(startIndex) + active.take(startIndex)

                    rotated.forEachIndexed { index, document ->
                        transaction.update(
                            document.reference,
                            mapOf(
                                "position" to index + 1,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    rotated.firstOrNull()?.let { newLeader ->
                        rotationCursorUpdates[cursorDriverField] =
                            newLeader.getString("driverId").orEmpty()
                                .ifBlank { newLeader.id }
                        rotationCursorUpdates[cursorCreatedAtField] =
                            accountCreationMillis(newLeader)
                    }
                }

            val resetMetaUpdates = mutableMapOf<String, Any>(
                "lastQueueDay" to today,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            resetMetaUpdates.putAll(rotationCursorUpdates)
            transaction.set(
                metaReference,
                resetMetaUpdates,
                SetOptions.merge()
            )
            transaction.set(
                queueControlReference,
                mapOf(
                    "revision" to currentRevision + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            Result(true, hour)
        }.await()
    }
}
