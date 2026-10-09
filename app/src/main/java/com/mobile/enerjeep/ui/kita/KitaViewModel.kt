package com.mobile.enerjeep.ui.kita

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class KitaRecord(
    val id: String,
    val title: String,
    val date: String,
    val amount: Double,
    val type: String,
    val isHistory: Boolean,
    val createdAtMillis: Long = 0L
)

enum class KitaTabType {
    KASALUKUYAN,
    KASAYSAYAN
}

data class KitaUiState(
    val netEarnings: Double = 0.0,
    val totalKita: Double = 0.0,
    val totalBayad: Double = 0.0,
    val totalMultiya: Double = 0.0,
    val selectedTab: KitaTabType =
        KitaTabType.KASALUKUYAN,
    val searchQuery: String = "",
    val records: List<KitaRecord> =
        emptyList(),
    val filteredRecords: List<KitaRecord> =
        emptyList()
)

@HiltViewModel
class KitaViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            KitaUiState()
        )

    val uiState:
            StateFlow<KitaUiState> =
        _uiState.asStateFlow()

    private var kitaListener:
            ListenerRegistration? = null

    private var ledgerListener:
            ListenerRegistration? = null

    private var kitaDocuments:
            List<DocumentSnapshot> =
        emptyList()

    private var dispatchLedgerDocuments:
            List<DocumentSnapshot> =
        emptyList()

    init {
        startListeners()
    }

    private fun startListeners() {

        val userId =
            userSession.userProfile
                ?.userId

        if (userId.isNullOrBlank()) {

            _uiState.value =
                KitaUiState()

            return
        }

        /*
         * Manual Kita/Bayad/Multiya records.
         */
        kitaListener =
            firestore
                .collection(
                    FirestoreCollections.KITA
                )
                .whereEqualTo(
                    "driverId",
                    userId
                )
                .addSnapshotListener {
                        snapshot,
                        error ->

                    if (
                        error != null
                    ) {
                        return@addSnapshotListener
                    }

                    kitaDocuments =
                        snapshot
                            ?.documents
                            .orEmpty()

                    rebuild()
                }

        /*
         * Dispatch earnings are stored in LEDGER with:
         *
         * source = queue_dispatch
         *
         * These are the actual earnings produced when
         * the Admin dispatches the driver.
         */
        ledgerListener =
            firestore
                .collection(
                    FirestoreCollections.LEDGER
                )
                .whereEqualTo(
                    "driverId",
                    userId
                )
                .addSnapshotListener {
                        snapshot,
                        error ->

                    if (
                        error != null
                    ) {
                        return@addSnapshotListener
                    }

                    dispatchLedgerDocuments =
                        snapshot
                            ?.documents
                            .orEmpty()
                            .filter {
                                it.getString(
                                    "source"
                                )?.equals(
                                    "queue_dispatch",
                                    ignoreCase = true
                                ) == true
                            }

                    rebuild()
                }
    }

    private fun rebuild() {

        val manualRecords =
            kitaDocuments.map {
                mapKitaDocument(it)
            }

        val dispatchRecords =
            dispatchLedgerDocuments.map {
                mapDispatchLedger(it)
            }

        val records =
            (
                    manualRecords +
                            dispatchRecords
                    )
                .sortedByDescending {
                    it.createdAtMillis
                }

        updateMetricsAndFilter(
            allRecords = records,
            tab = _uiState.value.selectedTab,
            query = _uiState.value.searchQuery
        )
    }

    private fun mapKitaDocument(
        document: DocumentSnapshot
    ): KitaRecord {

        val createdAt =
            document.getTimestamp(
                "createdAt"
            )

        val millis =
            createdAt
                ?.toDate()
                ?.time
                ?: 0L

        val isHistory =
            !isCurrentMonth(
                createdAt
            )

        return KitaRecord(
            id =
                document.id,

            title =
                document
                    .getString(
                        "title"
                    )
                    ?: "Record",

            date =
                document
                    .getString(
                        "date"
                    )
                    ?: formatTimestamp(
                        createdAt
                    ),

            amount =
                document
                    .getDouble(
                        "amount"
                    )
                    ?: 0.0,

            type =
                document
                    .getString(
                        "type"
                    )
                    ?: "Kita",

            isHistory =
                isHistory,

            createdAtMillis =
                millis
        )
    }

    private fun mapDispatchLedger(
        document: DocumentSnapshot
    ): KitaRecord {

        val createdAt =
            document.getTimestamp(
                "createdAt"
            )

        val millis =
            createdAt
                ?.toDate()
                ?.time
                ?: 0L

        val plate =
            document
                .getString(
                    "plateNumber"
                )
                .orEmpty()

        val date =
            document
                .getString(
                    "date"
                )
                ?: formatTimestamp(
                    createdAt
                )

        /*
         * NET amount is the amount the driver earned
         * after other dispatch fees.
         */
        val amount =
            document
                .getDouble(
                    "netAmount"
                )
                ?: document
                    .getDouble(
                        "amount"
                    )
                ?: 0.0

        return KitaRecord(
            id =
                "ledger_${document.id}",

            title =
                if (plate.isBlank()) {
                    "Dispatch"
                } else {
                    "Dispatch • $plate"
                },

            date =
                date,

            amount =
                amount,

            type =
                "Kita",

            isHistory =
                !isCurrentMonth(
                    createdAt
                ),

            createdAtMillis =
                millis
        )
    }

    fun addKita(
        driverId: String,
        title: String,
        amount: Double,
        type: String,
        date: String
    ) {

        if (driverId.isBlank()) {
            return
        }

        if (title.isBlank()) {
            return
        }

        if (amount <= 0.0) {
            return
        }

        viewModelScope.launch {

            try {

                val id =
                    FirestoreIds.kita()

                val cleanType =
                    type
                        .trim()
                        .ifBlank {
                            "Kita"
                        }

                val data =
                    mapOf<String, Any>(

                        "id" to
                                id,

                        "driverId" to
                                driverId.trim(),

                        "title" to
                                title.trim(),

                        "amount" to
                                amount,

                        "type" to
                                cleanType,

                        "date" to
                                date.trim()
                                    .ifBlank {
                                        SimpleDateFormat(
                                            "MMM dd, yyyy",
                                            Locale.getDefault()
                                        ).format(
                                            Date()
                                        )
                                    },

                        "isHistory" to
                                false,

                        "createdAt" to
                                FieldValue
                                    .serverTimestamp()
                    )

                firestore
                    .collection(
                        FirestoreCollections.KITA
                    )
                    .document(id)
                    .set(data)
                    .await()

            } catch (_: Exception) {
                /*
                 * Listener continues to provide
                 * existing Firestore data.
                 */
            }
        }
    }

    fun onTabSelected(
        tab: KitaTabType
    ) {

        updateMetricsAndFilter(
            allRecords =
                _uiState.value.records,

            tab =
                tab,

            query =
                _uiState.value.searchQuery
        )
    }

    fun onSearchQueryChanged(
        query: String
    ) {

        updateMetricsAndFilter(
            allRecords =
                _uiState.value.records,

            tab =
                _uiState.value.selectedTab,

            query =
                query
        )
    }

    private fun updateMetricsAndFilter(
        allRecords: List<KitaRecord>,
        tab: KitaTabType,
        query: String
    ) {

        val showHistory =
            tab == KitaTabType.KASAYSAYAN

        val currentTabRecords =
            allRecords.filter {
                it.isHistory == showHistory
            }

        val kitaSum =
            currentTabRecords
                .filter {
                    it.type.equals(
                        "Kita",
                        ignoreCase = true
                    )
                }
                .sumOf {
                    it.amount
                }

        val bayadSum =
            currentTabRecords
                .filter {
                    it.type.equals(
                        "Bayad",
                        ignoreCase = true
                    )
                }
                .sumOf {
                    it.amount
                }

        val multiyaSum =
            currentTabRecords
                .filter {
                    it.type.equals(
                        "Multiya",
                        ignoreCase = true
                    )
                }
                .sumOf {
                    it.amount
                }

        val net =
            kitaSum -
                    (
                            bayadSum +
                                    multiyaSum
                            )

        val filtered =
            currentTabRecords.filter {

                it.title.contains(
                    query,
                    ignoreCase = true
                ) ||
                        it.date.contains(
                            query,
                            ignoreCase = true
                        )
            }

        _uiState.value =
            _uiState.value.copy(

                netEarnings =
                    net,

                totalKita =
                    kitaSum,

                totalBayad =
                    bayadSum,

                totalMultiya =
                    multiyaSum,

                selectedTab =
                    tab,

                searchQuery =
                    query,

                records =
                    allRecords,

                filteredRecords =
                    filtered
            )
    }

    private fun isCurrentMonth(
        timestamp:
        com.google.firebase.Timestamp?
    ): Boolean {

        if (timestamp == null) {
            return false
        }

        val monthKey =
            SimpleDateFormat(
                "yyyyMM",
                Locale.getDefault()
            )

        val current =
            monthKey.format(
                Date()
            )

        val record =
            monthKey.format(
                timestamp.toDate()
            )

        return current == record
    }

    private fun formatTimestamp(
        timestamp:
        com.google.firebase.Timestamp?
    ): String {

        return timestamp
            ?.toDate()
            ?.let {
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).format(it)
            }
            .orEmpty()
    }

    override fun onCleared() {

        kitaListener?.remove()

        ledgerListener?.remove()

        super.onCleared()
    }
}