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
     * Isang tseke ng araw: kapag lampas na sa cut-off, umiikot ang bawat
     * ruta nang isang puwesto sa permanenteng master order ayon sa account
     * creation date. Ang dating #1 ay inililipat sa dulo at ang kasunod
     * ang nagiging bagong #1. Nasa settings/queueRotation ang cursor para
     * magpatuloy ang rotation sa susunod na araw. Ang basa ng queue ay SA
     * LOOB ng transaction kaya walang stale na listahan.
     */
    suspend fun rotateOnce(firestore: FirebaseFirestore): Result {
        val queueCollection = firestore.collection(FirestoreCollections.QUEUE)
        val usersCollection = firestore.collection(FirestoreCollections.USERS)

        /*
         * Kuhanin ang IDs bago ang transaction; sariwang dokumento naman ang
         * babasahin sa loob nito. Superadmin-only ang rotation, kaya puwedeng
         * basahin ang buong driver roster nang hindi binibigyan ng list access
         * ang mga driver account.
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
        val driverAccountIds = usersCollection
            .whereEqualTo("role", "driver")
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
            val hour = (meta.getLong("cutoffHour")?.toInt()
                ?: QueueViewModel.CUTOFF_HOUR).coerceIn(0, 23)
            val today = queueDayId(Date(), hour)
            if (lastReset == today) {
                return@runTransaction Result(false, hour)
            }

            // Fresh queue and driver reads are part of this transaction.
            val queueDocs = queueIds.map { id ->
                transaction.get(queueCollection.document(id))
            }.filter { it.exists() }
            val driverAccounts = driverAccountIds.map { id ->
                transaction.get(usersCollection.document(id))
            }.filter {
                it.exists() &&
                        it.getString("role")?.equals("driver", ignoreCase = true) == true &&
                        (it.getString("status").isNullOrBlank() ||
                                it.getString("status")?.equals("active", ignoreCase = true) == true)
            }

            // The cycle is built from ALL active driver accounts, not just
            // today's active queue rows. A dispatched/completed driver may be
            // absent from /queue temporarily; that must not shorten the cycle.
            val earliestQueueCreatedAtByDriver = queueDocs
                .filter { !it.getString("driverId").isNullOrBlank() }
                .groupBy { it.getString("driverId").orEmpty() }
                .mapValues { (_, entries) ->
                    entries.mapNotNull { document ->
                        document.getTimestamp("accountCreatedAt")?.toDate()?.time
                            ?: document.getTimestamp("createdAt")?.toDate()?.time
                    }.minOrNull()
                }

            data class PermanentDriver(
                val driverId: String,
                val createdAtMillis: Long
            )

            val permanentDrivers = driverAccounts.map { account ->
                val driverId = account.getString("userId").orEmpty().ifBlank { account.id }
                val createdAtMillis = account.getTimestamp("createdAt")?.toDate()?.time
                    ?: earliestQueueCreatedAtByDriver[driverId]
                    ?: Long.MAX_VALUE
                PermanentDriver(driverId, createdAtMillis)
            }.ifEmpty {
                // Legacy-only fallback for orphaned queue history when no
                // driver account documents are available.
                earliestQueueCreatedAtByDriver.map { (driverId, createdAtMillis) ->
                    PermanentDriver(driverId, createdAtMillis ?: Long.MAX_VALUE)
                }
            }.sortedWith(
                compareBy<PermanentDriver> { it.createdAtMillis }
                    .thenBy { it.driverId }
            )

            val rotationCursorUpdates = mutableMapOf<String, Any>()

            listOf(QueueViewModel.ROUTE_MA, QueueViewModel.ROUTE_AM)
                .forEach { route ->
                    val routeName = if (route == QueueViewModel.ROUTE_MA) {
                        "Marilao"
                    } else {
                        "Monumento"
                    }
                    val cursorDriverField = "rotationStart${routeName}DriverId"
                    val cursorCreatedAtField =
                        "rotationStart${routeName}CreatedAtMillis"
                    val previousLeaderId = meta.getString(cursorDriverField).orEmpty()
                    val previousLeaderCreatedAt = meta.getLong(cursorCreatedAtField)

                    // Advance against the FULL permanent roster. Never use
                    // active.size here: that would wrap to A prematurely if
                    // some drivers are dispatched/completed and absent today.
                    val startIndex = when {
                        permanentDrivers.size <= 1 -> 0
                        previousLeaderId.isNotBlank() -> {
                            val previousIndex = permanentDrivers.indexOfFirst {
                                it.driverId == previousLeaderId
                            }
                            if (previousIndex >= 0) {
                                (previousIndex + 1) % permanentDrivers.size
                            } else if (previousLeaderCreatedAt != null) {
                                val nextIndex = permanentDrivers.indexOfFirst { driver ->
                                    driver.createdAtMillis > previousLeaderCreatedAt ||
                                            (driver.createdAtMillis == previousLeaderCreatedAt &&
                                                    driver.driverId > previousLeaderId)
                                }
                                if (nextIndex >= 0) nextIndex else 0
                            } else {
                                1
                            }
                        }
                        previousLeaderCreatedAt != null -> {
                            val nextIndex = permanentDrivers.indexOfFirst { driver ->
                                driver.createdAtMillis > previousLeaderCreatedAt
                            }
                            if (nextIndex >= 0) nextIndex else 0
                        }
                        // First reset: permanent #1 goes to the end and #2 leads.
                        else -> 1
                    }

                    val rotatedDrivers = permanentDrivers.drop(startIndex) +
                            permanentDrivers.take(startIndex)
                    val permanentOrder = rotatedDrivers
                        .mapIndexed { index, driver -> driver.driverId to index }
                        .toMap()

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
                            compareBy<DocumentSnapshot> {
                                val driverId = it.getString("driverId")
                                    .orEmpty().ifBlank { it.id }
                                permanentOrder[driverId] ?: Int.MAX_VALUE
                            }.thenBy {
                                it.getLong("position") ?: Long.MAX_VALUE
                            }.thenBy {
                                it.safeTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
                            }
                        )

                    active.forEachIndexed { index, document ->
                        transaction.update(
                            document.reference,
                            mapOf(
                                "position" to index + 1,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    // Save the next leader from the FULL roster even when that
                    // driver has no active queue row today. This preserves the
                    // entire A -> B -> ... -> last driver -> A cycle.
                    rotatedDrivers.firstOrNull()?.let { newLeader ->
                        rotationCursorUpdates[cursorDriverField] = newLeader.driverId
                        rotationCursorUpdates[cursorCreatedAtField] =
                            newLeader.createdAtMillis
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
