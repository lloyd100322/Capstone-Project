package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class IncidentItem(
    val id: String,
    val title: String,
    val plateNumber: String,
    val location: String,
    val date: String,
    val penalty: String,
    val description: String,
    val sentToDriver: String = "Oo",
    val status: String = "Bukas"
)

sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data class Success(
        val activeIncidents: List<IncidentItem>,
        val historyIncidents: List<IncidentItem>,
        val openCount: Int,
        val resolvedCount: Int
    ) : IncidentUiState
    data class Error(val message: String) : IncidentUiState
}

@HiltViewModel
class IncidentViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow<IncidentUiState>(IncidentUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        listener = firestore.collection(FirestoreCollections.INCIDENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = IncidentUiState.Error(error.message ?: "Hindi mabasa ang mga insidente")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val active = mutableListOf<IncidentItem>()
                val history = mutableListOf<IncidentItem>()

                snapshot.documents.sortedByDescending {
                    it.getTimestamp("createdAt")?.seconds ?: 0L
                }.forEach { document ->
                    val item = mapIncident(document.id, document.data ?: emptyMap())
                    if (document.getBoolean("isResolved") == true || document.getString("status")?.lowercase() == "resolved") {
                        history += item
                    } else {
                        active += item
                    }
                }

                _uiState.value = IncidentUiState.Success(
                    activeIncidents = active,
                    historyIncidents = history,
                    openCount = active.size,
                    resolvedCount = history.size
                )
            }
    }

    fun reportIncident(
        plateNumber: String,
        type: String,
        location: String,
        penalty: String,
        description: String
    ) {
        val profile = userSession.userProfile
        val id = FirestoreIds.incident()
        val cleanPenalty = penalty.toDoubleOrNull() ?: 0.0
        val dateText = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            try {
                val driverId = findDriverIdByPlate(plateNumber)
                val driverName = findDriverNameByPlate(plateNumber)
                val data = mapOf<String, Any>(
                    "id" to id,
                    "driverId" to driverId,
                    "driverName" to driverName,
                    "plateNumber" to plateNumber.trim(),
                    "title" to type.ifBlank { "Di-tiyak na Insidente" },
                    "location" to location.ifBlank { "Di-tiyak na lokasyon" },
                    "date" to dateText,
                    "penalty" to cleanPenalty,
                    "description" to description.trim(),
                    "sentToDriver" to true,
                    "isResolved" to false,
                    "status" to "open",
                    "reportedBy" to (profile?.userId ?: ""),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection(FirestoreCollections.INCIDENTS).document(id).set(data).await()
            } catch (e: Exception) {
                _uiState.value = IncidentUiState.Error(e.message ?: "Hindi ma-save ang insidente")
            }
        }
    }

    fun resolveIncident(incidentId: String) {
        viewModelScope.launch {
            try {
                firestore.collection(FirestoreCollections.INCIDENTS)
                    .document(incidentId)
                    .update(
                        mapOf(
                            "isResolved" to true,
                            "status" to "resolved",
                            "resolvedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (e: Exception) {
                _uiState.value = IncidentUiState.Error(e.message ?: "Hindi ma-resolve ang insidente")
            }
        }
    }

    private suspend fun findDriverIdByPlate(plateNumber: String): String {
        return findDriverDocumentByPlate(plateNumber)?.getString("userId").orEmpty()
    }

    private suspend fun findDriverNameByPlate(plateNumber: String): String {
        val document = findDriverDocumentByPlate(plateNumber) ?: return ""
        return listOfNotNull(document.getString("firstName"), document.getString("lastName"))
            .joinToString(" ")
    }

    private suspend fun findDriverDocumentByPlate(plateNumber: String): com.google.firebase.firestore.DocumentSnapshot? {
        return firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("plateNumber", plateNumber.trim())
            .limit(1)
            .get().await().documents.firstOrNull()
    }



    private fun mapIncident(id: String, data: Map<String, Any>): IncidentItem {
        val driverName = data["driverName"] as? String ?: ""
        val plate = data["plateNumber"] as? String ?: ""
        return IncidentItem(
            id = id,
            title = data["title"] as? String ?: "Insidente",
            plateNumber = if (driverName.isBlank()) plate else "$plate — $driverName",
            location = data["location"] as? String ?: "",
            date = data["date"] as? String ?: "",
            penalty = "₱${(data["penalty"] as? Number)?.toDouble()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "0"}",
            description = data["description"] as? String ?: "",
            sentToDriver = if (data["sentToDriver"] == true) "Oo" else "Hindi",
            status = if (data["isResolved"] == true || (data["status"] as? String)?.lowercase() == "resolved") "Naayos" else "Bukas"
        )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
