package com.mobile.enerjeep.ui.kita

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.ui.components.ToastFeedback
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
    val createdAtMillis: Long = 0L,
    // The ledger document this record was derived from, when there is one.
    // Used so the same earning is never counted twice.
    val sourceId: String? = null
)

enum class KitaTabType {
    NGAYON,
    KASAYSAYAN
}

data class KitaUiState(
    val netEarnings: Double = 0.0,
    val totalKita: Double = 0.0,
    val totalBayad: Double = 0.0,
    val totalMultiya: Double = 0.0,
    val selectedTab: KitaTabType = KitaTabType.NGAYON,
    val searchQuery: String = "",
    val records: List<KitaRecord> = emptyList(),
    val filteredRecords: List<KitaRecord> = emptyList()
)

@HiltViewModel
class KitaViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(KitaUiState())
    val uiState: StateFlow<KitaUiState> = _uiState.asStateFlow()

    private var kitaListener: ListenerRegistration? = null
    private var ledgerListener: ListenerRegistration? = null
    private val ledgerPlateListeners = mutableListOf<ListenerRegistration>()

    private var kitaDocuments: List<DocumentSnapshot> = emptyList()
    private var driverLedgerDocuments: List<DocumentSnapshot> = emptyList()
    private val plateLedgerSnapshots = mutableMapOf<String, List<DocumentSnapshot>>()
    private var dispatchLedgerDocuments: List<DocumentSnapshot> = emptyList()

    // users.userId the listeners are currently attached for.
    private var listeningUserId: String = ""
    private var listenerErrorShown = false

    init {
        startListeners()
    }

    /**
     * The ViewModel can outlive a login (or be created before the profile is
     * set). Re-attach the listeners whenever the signed-in driver changed so a
     * driver never sees another driver's earnings, or an empty list.
     */
    fun refreshIdentity() {
        val currentUserId = userSession.userProfile?.userId?.trim().orEmpty()
        if (currentUserId != listeningUserId) {
            startListeners()
        }
    }

    private fun stopListeners() {
        kitaListener?.remove()
        ledgerListener?.remove()
        ledgerPlateListeners.forEach { it.remove() }
        ledgerPlateListeners.clear()
        kitaListener = null
        ledgerListener = null
        kitaDocuments = emptyList()
        driverLedgerDocuments = emptyList()
        plateLedgerSnapshots.clear()
        dispatchLedgerDocuments = emptyList()
    }

    private fun reportListenerError(error: Exception?) {
        if (error == null || listenerErrorShown) return
        listenerErrorShown = true
        ToastFeedback.show(
            "Hindi ma-load ang kita: ${error.message ?: "may problema sa Firestore"}"
        )
    }

    private fun startListeners() {
        stopListeners()
        listenerErrorShown = false

        val profile = userSession.userProfile
        val userId = profile?.userId?.trim().orEmpty()
        listeningUserId = userId

        if (userId.isBlank()) {
            _uiState.value = KitaUiState()
            return
        }

        // Existing manual Kita/Bayad/Multiya entries for this driver.
        kitaListener = firestore
            .collection(FirestoreCollections.KITA)
            .whereEqualTo("driverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    reportListenerError(error)
                    return@addSnapshotListener
                }

                kitaDocuments = snapshot.documents
                rebuild()
            }

        // Primary lookup: dispatch ledger records linked by the driver's users.userId.
        ledgerListener = firestore
            .collection(FirestoreCollections.LEDGER)
            .whereEqualTo("driverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    reportListenerError(error)
                    return@addSnapshotListener
                }

                driverLedgerDocuments = snapshot.documents
                refreshDispatchLedgerDocuments()
            }

        // Fallback for existing ledger records linked by plate instead of driverId.
        // Query both the stored and uppercase form because older records may differ in case.
        val storedPlate = profile?.plateNumber?.trim().orEmpty()
        val plateValues = listOf(storedPlate, storedPlate.uppercase(Locale.ROOT))
            .filter { it.isNotBlank() }
            .distinct()

        plateValues.forEach { plateValue ->
            val listener = firestore
                .collection(FirestoreCollections.LEDGER)
                .whereEqualTo("plateNumber", plateValue)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        reportListenerError(error)
                        return@addSnapshotListener
                    }

                    plateLedgerSnapshots[plateValue] = snapshot.documents
                    refreshDispatchLedgerDocuments()
                }

            ledgerPlateListeners.add(listener)
        }
    }

    private fun refreshDispatchLedgerDocuments() {
        // A document can match both the driverId and plate queries; keep it once.
        dispatchLedgerDocuments = (driverLedgerDocuments + plateLedgerSnapshots.values.flatten())
            .distinctBy { it.id }
            .filter { document ->
                // The plate fallback may match a record of another driver who
                // used the same jeepney. Only keep records that belong to this
                // driver (users.userId) or have no driver recorded at all.
                val owner = document.getString("driverId").orEmpty().trim()
                owner.isBlank() || owner == listeningUserId
            }
            .filter { document ->
                val source = document.getString("source").orEmpty().trim()
                val feeType = document.getString("feeType").orEmpty().trim()

                source.equals("queue_dispatch", ignoreCase = true) ||
                        feeType.equals("Dispatch", ignoreCase = true)
            }
            // One earning per dispatched queue entry, even if older retries
            // already created several ledger documents for the same dispatch.
            .distinctBy { document ->
                document.getString("queueId")?.trim()?.takeIf { it.isNotBlank() }
                    ?: document.id
            }

        rebuild()
    }

    private fun rebuild() {
        val manualRecords = kitaDocuments.map { mapKitaDocument(it) }
        val dispatchRecords = dispatchLedgerDocuments.map { mapDispatchLedger(it) }

        // The same earning can be described by more than one document. The
        // sourceId of a record is the identity of the transaction, so the
        // merge keeps one row per earning. A retry, a reopened screen or a
        // listener that fires again therefore never adds the same earning
        // twice and never changes a valid balance.
        val records = KitaHistory.merge(
            dispatchRecords = dispatchRecords,
            manualRecords = manualRecords
        )

        updateMetricsAndFilter(
            allRecords = records,
            tab = _uiState.value.selectedTab,
            query = _uiState.value.searchQuery
        )
    }

    private fun mapKitaDocument(document: DocumentSnapshot): KitaRecord {
        val millis = getCreatedAtMillis(document)
        return KitaRecord(
            id = document.id,
            title = document.getString("title") ?: "Record",
            date = document.getString("date")?.takeIf { it.isNotBlank() }
                ?: formatMillis(millis),
            amount = getNumericValue(document, "amount") ?: 0.0,
            type = document.getString("type")
                ?.let { if (it.equals("Multiya", ignoreCase = true)) "Multa" else it }
                ?: "Kita",
            isHistory = !isToday(millis),
            createdAtMillis = millis,
            sourceId = "kita:${document.id}"
        )
    }

    private fun mapDispatchLedger(document: DocumentSnapshot): KitaRecord {
        val millis = getCreatedAtMillis(document)
        val plate = document.getString("plateNumber").orEmpty().trim()
        val displayDate = document.getString("date")?.takeIf { it.isNotBlank() }
            ?: formatMillis(millis)

        // Driver earnings are the net amount after dispatch fees.
        val amount = getNumericValue(document, "netAmount")
            ?: getNumericValue(document, "amount")
            ?: 0.0

        return KitaRecord(
            id = "ledger_${document.id}",
            title = if (plate.isBlank()) "Dispatch" else "Dispatch • $plate",
            date = displayDate,
            amount = amount,
            type = "Kita",
            isHistory = !isToday(millis),
            createdAtMillis = millis,
            sourceId = "ledger:${document.id}"
        )
    }

    fun addKita(
        driverId: String,
        title: String,
        amount: Double,
        type: String,
        date: String
    ) {
        if (driverId.isBlank() || title.isBlank() || amount <= 0.0) {
            return
        }

        viewModelScope.launch {
            try {
                val id = FirestoreIds.kita()
                val cleanType = type.trim().ifBlank { "Kita" }

                val data = mapOf<String, Any>(
                    "id" to id,
                    "driverId" to driverId.trim(),
                    "title" to title.trim(),
                    "amount" to amount,
                    "type" to cleanType,
                    "date" to date.trim().ifBlank {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                    },
                    "isHistory" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firestore
                    .collection(FirestoreCollections.KITA)
                    .document(id)
                    .set(data)
                    .await()
            } catch (e: Exception) {
                // Never swallow a failed save: the user must know the
                // record was not written so they can try again.
                ToastFeedback.show(
                    e.message ?: "Hindi na-save ang kita. Subukan muli."
                )
            }
        }
    }

    fun onTabSelected(tab: KitaTabType) {
        updateMetricsAndFilter(
            allRecords = _uiState.value.records,
            tab = tab,
            query = _uiState.value.searchQuery
        )
    }

    fun onSearchQueryChanged(query: String) {
        updateMetricsAndFilter(
            allRecords = _uiState.value.records,
            tab = _uiState.value.selectedTab,
            query = query
        )
    }

    private fun updateMetricsAndFilter(
        allRecords: List<KitaRecord>,
        tab: KitaTabType,
        query: String
    ) {
        val showHistory = tab == KitaTabType.KASAYSAYAN

        /*
         * "Ngayon" keeps showing the earnings of today only.
         * "Kasaysayan" is the complete history: every earning that was
         * saved, including the ones of today, so a driver who just
         * earned money sees it in both views. Once the day has passed,
         * the record automatically stays only in the history.
         */
        /*
         * The "today" check is redone here every time the list is
         * rebuilt, so records regroup correctly even when the app was
         * left open past midnight.
         */
        val refreshedRecords = allRecords.map { record ->
            record.copy(isHistory = !isToday(record.createdAtMillis))
        }

        val currentTabRecords = KitaHistory.visible(
            records = refreshedRecords,
            showHistory = showHistory
        )

        val totals = KitaHistory.totals(currentTabRecords)

        val filtered = currentTabRecords.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.date.contains(query, ignoreCase = true)
        }

        _uiState.value = _uiState.value.copy(
            netEarnings = totals.net,
            totalKita = totals.kita,
            totalBayad = totals.bayad,
            totalMultiya = totals.multa,
            selectedTab = tab,
            searchQuery = query,
            records = allRecords,
            filteredRecords = filtered
        )
    }

    /**
     * Supports Firestore Timestamp values and numeric epoch values
     * (seconds or milliseconds), plus date strings used by older records.
     */
    private fun getCreatedAtMillis(document: DocumentSnapshot): Long {
        document.safeTimestamp("createdAt")?.let {
            return it.toDate().time
        }

        val rawCreatedAt = document.get("createdAt")
        if (rawCreatedAt is Number) {
            val rawValue = rawCreatedAt.toLong()
            return if (rawValue in 1 until 100_000_000_000L) {
                rawValue * 1000L
            } else {
                rawValue
            }
        }

        val dateText = document.getString("date")?.trim().orEmpty()
        if (dateText.isBlank()) return 0L

        val patterns = listOf("MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "MM/dd/yyyy")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    isLenient = false
                }
                val parsed = parser.parse(dateText)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try the next supported legacy date format.
            }
        }

        return 0L
    }

    private fun getNumericValue(document: DocumentSnapshot, field: String): Double? {
        return (document.get(field) as? Number)?.toDouble()
    }

    private fun isToday(createdAtMillis: Long): Boolean {
        if (createdAtMillis <= 0L) return false

        val dayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val currentDay = dayKey.format(Date())
        val recordDay = dayKey.format(Date(createdAtMillis))
        return currentDay == recordDay
    }

    private fun formatMillis(millis: Long): String {
        if (millis <= 0L) return ""

        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(millis))
    }

    override fun onCleared() {
        kitaListener?.remove()
        ledgerListener?.remove()
        ledgerPlateListeners.forEach { it.remove() }
        super.onCleared()
    }
}
