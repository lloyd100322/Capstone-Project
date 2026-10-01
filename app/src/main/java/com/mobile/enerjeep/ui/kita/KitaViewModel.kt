package com.mobile.enerjeep.ui.kita

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isHistory: Boolean
)

enum class KitaTabType {
    KASALUKUYAN, KASAYSAYAN
}

data class KitaUiState(
    val netEarnings: Double = 0.0,
    val totalKita: Double = 0.0,
    val totalBayad: Double = 0.0,
    val totalMultiya: Double = 0.0,
    val selectedTab: KitaTabType = KitaTabType.KASALUKUYAN,
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
    private var listener: ListenerRegistration? = null

    init {
        startListener()
    }

    private fun startListener() {
        val userId = userSession.userProfile?.userId

        // "Aking Kita" is driver-specific. The driverId stored in kita must
        // match users.userId, so never use the users document ID (phone number) here.
        if (userId.isNullOrBlank()) {
            _uiState.value = KitaUiState()
            return
        }

        listener = firestore.collection(FirestoreCollections.KITA)
            .whereEqualTo("driverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val records = snapshot.documents.map { document ->
                    val isHistory = document.getBoolean("isHistory")
                        ?: !isToday(document.getTimestamp("createdAt"))
                    KitaRecord(
                        id = document.id,
                        title = document.getString("title") ?: document.getString("description") ?: "Record",
                        date = document.getString("date") ?: formatTimestamp(document.getTimestamp("createdAt")),
                        amount = document.getDouble("amount") ?: 0.0,
                        type = document.getString("type") ?: "Kita",
                        isHistory = isHistory
                    )
                }.sortedByDescending { it.id }

                updateMetricsAndFilter(records, _uiState.value.selectedTab, _uiState.value.searchQuery)
            }
    }

    fun onTabSelected(tab: KitaTabType) {
        updateMetricsAndFilter(_uiState.value.records, tab, _uiState.value.searchQuery)
    }

    fun onSearchQueryChanged(query: String) {
        updateMetricsAndFilter(_uiState.value.records, _uiState.value.selectedTab, query)
    }

    private fun updateMetricsAndFilter(allRecords: List<KitaRecord>, tab: KitaTabType, query: String) {
        val isHistory = tab == KitaTabType.KASAYSAYAN
        val currentTabRecords = allRecords.filter { it.isHistory == isHistory }

        val kitaSum = currentTabRecords.filter { it.type.equals("Kita", true) }.sumOf { it.amount }
        val bayadSum = currentTabRecords.filter { it.type.equals("Bayad", true) }.sumOf { it.amount }
        val multiyaSum = currentTabRecords.filter { it.type.equals("Multiya", true) }.sumOf { it.amount }
        val net = kitaSum - (bayadSum + multiyaSum)

        val filtered = currentTabRecords.filter { record ->
            record.title.contains(query, ignoreCase = true) ||
                    record.date.contains(query, ignoreCase = true)
        }

        _uiState.value = _uiState.value.copy(
            netEarnings = net,
            totalKita = kitaSum,
            totalBayad = bayadSum,
            totalMultiya = multiyaSum,
            selectedTab = tab,
            searchQuery = query,
            records = allRecords,
            filteredRecords = filtered
        )
    }

    private fun isToday(timestamp: com.google.firebase.Timestamp?): Boolean {
        if (timestamp == null) return false
        val key = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(timestamp.toDate()) == key
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        }.orEmpty()
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
