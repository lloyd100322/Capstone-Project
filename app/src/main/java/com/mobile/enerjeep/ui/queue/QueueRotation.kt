package com.mobile.enerjeep.ui.queue

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mobile.enerjeep.data.FirestoreCollections
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AWTOMATIKONG RESET NG PILA (cut-off) - shared engine.
 *
 * PANGUNAHIN: isang NAKA-SCHEDULE na Cloud Function (functions/index.js
 * - dailyQueueReset, bawat oras, Asia/Manila) ang nagre-reset ng pila
 * kahit SARADO ang app. Pareho ng logic nito.
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
        val calendar = Calendar.getInstance()
        calendar.time = now
        if (calendar.get(Calendar.HOUR_OF_DAY) >= cutoffHour) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
    }

    /** Millis hanggang sa susunod na cut-off hour (hindi bababa sa 1 minuto). */
    fun millisUntilNextCutoff(cutoffHour: Int): Long {
        val now = Calendar.getInstance()
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
     * Isang tseke ng araw: kung lampas na sa cut-off at hindi pa
     * na-ikot ngayong araw, ini-ikot ang pila (#1 ng bawat ruta ay
     * napupunta sa huli). Ang basa ng queue ay SA LOOB ng transaction
     * kaya walang stale na listahan kahit sabay-sabay na admin ang
     * magbukas ng app.
     */
    suspend fun rotateOnce(firestore: FirebaseFirestore): Result {
        val queueCollection = firestore.collection(FirestoreCollections.QUEUE)

        /*
         * Kolektahin muna ang mga ID ng pila (isang simpleng basa). Ang
         * bawat dokumento ay binabasa SA LOOB ng transaction gamit ang
         * transaction.get(document) - per document ang basa dahil walang
         * transaction.get(query) ang mobile SDK.
         */
        val queueIds = queueCollection
            .get()
            .await()
            .documents
            .map { it.id }

        return firestore.runTransaction { transaction ->
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
                        .sortedBy {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }

                    if (active.size > 1) {
                        /*
                         * Rotation: today's first goes to the last spot,
                         * everyone else moves up one position.
                         */
                        val rotated = active.drop(1) + active.first()
                        rotated.forEachIndexed { index, document ->
                            transaction.update(
                                document.reference,
                                mapOf(
                                    "position" to index + 1,
                                    "updatedAt" to
                                            FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }

            transaction.set(
                metaReference,
                mapOf(
                    "lastQueueDay" to today,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            Result(true, hour)
        }.await()
    }
}
