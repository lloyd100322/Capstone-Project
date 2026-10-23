package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.PhilippineIdentifiers
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.ui.components.ToastFeedback
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
    val status: String = "Bukas",
    val createdAtMillis: Long = 0L
)

data class IncidentDriverOption(
    val userId: String,
    val plateNumber: String,
    val driverName: String
)

sealed interface IncidentUiState {
    data object Loading : IncidentUiState

    data class Success(
        val activeIncidents: List<IncidentItem>,
        val historyIncidents: List<IncidentItem>,
        // Every incident, open and already resolved. The "Naayos" tab
        // shows only the resolved ones (historyIncidents), so an incident
        // that was made today is written in the history as well.
        val allIncidents: List<IncidentItem>,
        val openCount: Int,
        val resolvedCount: Int
    ) : IncidentUiState

    data class Error(
        val message: String
    ) : IncidentUiState
}

@HiltViewModel
class IncidentViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<IncidentUiState>(
            IncidentUiState.Loading
        )

    val uiState = _uiState.asStateFlow()

    private val _driverOptions =
        MutableStateFlow<List<IncidentDriverOption>>(
            emptyList()
        )

    val driverOptions = _driverOptions.asStateFlow()

    private var incidentListener: ListenerRegistration? = null
    private var driverListener: ListenerRegistration? = null

    init {
        startIncidentListener()
        startDriverListener()
    }

    private fun startIncidentListener() {

        incidentListener =
            firestore
                .collection(FirestoreCollections.INCIDENTS)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        _uiState.value =
                            IncidentUiState.Error(
                                error.message
                                    ?: "Hindi mabasa ang mga insidente"
                            )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    val active =
                        mutableListOf<IncidentItem>()

                    val history =
                        mutableListOf<IncidentItem>()

                    val all =
                        mutableListOf<IncidentItem>()

                    snapshot.documents
                        .sortedByDescending {
                            (
                                    it.safeTimestamp("resolvedAt")
                                        ?: it.safeTimestamp("createdAt")
                                    )
                                ?.seconds
                                ?: 0L
                        }
                        .forEach { document ->

                            val item =
                                mapIncident(
                                    document.id,
                                    document.data
                                        ?: emptyMap(),
                                    document
                                        .safeTimestamp("createdAt")
                                        ?.toDate()
                                        ?.time
                                        ?: 0L
                                )

                            val resolved =
                                document.getBoolean(
                                    "isResolved"
                                ) == true ||
                                        document
                                            .getString("status")
                                            ?.equals(
                                                "resolved",
                                                true
                                            ) == true

                            all += item

                            if (resolved) {
                                history += item
                            } else {
                                active += item
                            }
                        }

                    _uiState.value =
                        IncidentUiState.Success(
                            activeIncidents = active,
                            historyIncidents = history,
                            allIncidents = all,
                            openCount = active.size,
                            resolvedCount = history.size
                        )
                }
    }

    private fun startDriverListener() {

        driverListener =
            firestore
                .collection(FirestoreCollections.USERS)
                .whereEqualTo("role", "driver")
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        _driverOptions.value =
                            emptyList()
                        return@addSnapshotListener
                    }

                    _driverOptions.value =
                        snapshot.documents
                            .mapNotNull {
                                mapDriverOption(it)
                            }
                            .sortedBy {
                                it.plateNumber.lowercase()
                            }
                }
    }

    private fun mapDriverOption(
        document: DocumentSnapshot
    ): IncidentDriverOption? {

        val userId =
            document
                .getString("userId")
                .orEmpty()
                .trim()

        val plateNumber =
            document
                .getString("plateNumber")
                .orEmpty()
                .trim()

        if (
            userId.isBlank() ||
            plateNumber.isBlank()
        ) {
            return null
        }

        val firstName =
            document
                .getString("firstName")
                .orEmpty()
                .trim()

        val lastName =
            document
                .getString("lastName")
                .orEmpty()
                .trim()

        val driverName =
            listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank {
                    "Driver"
                }

        return IncidentDriverOption(
            userId = userId,
            plateNumber = plateNumber,
            driverName = driverName
        )
    }

    /**
     * Sends a previously saved (but not sent) incident to the drivers.
     */
    fun sendIncidentToDriver(incidentId: String) {
        viewModelScope.launch {
            try {
                firestore
                    .collection(FirestoreCollections.INCIDENTS)
                    .document(incidentId)
                    .update(
                        mapOf(
                            "sentToDriver" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                ToastFeedback.show(
                    "Naipadala na sa mga driver ang insidente."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi maipadala ang insidente."
                )
            }
        }
    }

    fun reportIncident(
        plateNumber: String,
        type: String,
        location: String,
        penalty: String,
        description: String,
        sendToDriver: Boolean = true
    ) {

        val profile =
            userSession.userProfile

        val id =
            FirestoreIds.incident()

        val cleanPenalty =
            penalty.toDoubleOrNull() ?: 0.0

        val dateText =
            SimpleDateFormat(
                "MMM dd, yyyy",
                Locale.getDefault()
            ).format(Date())

        viewModelScope.launch {

            try {

                val driverDocument =
                    findDriverDocumentByPlate(
                        plateNumber
                    )

                if (driverDocument == null) {
                    throw IllegalStateException(
                        "Hindi makita ang driver para sa plaka ${plateNumber.trim()}"
                    )
                }

                val driverId =
                    driverDocument
                        .getString("userId")
                        .orEmpty()
                        .trim()

                if (driverId.isBlank()) {
                    throw IllegalStateException(
                        "Walang User ID ang driver na ito"
                    )
                }

                val firstName =
                    driverDocument
                        .getString("firstName")
                        .orEmpty()

                val lastName =
                    driverDocument
                        .getString("lastName")
                        .orEmpty()

                val driverName =
                    listOf(firstName, lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank {
                            "Driver"
                        }

                val data =
                    mapOf<String, Any>(

                        "id" to id,

                        "driverId" to
                                driverId,

                        "driverName" to
                                driverName,

                        "plateNumber" to
                                plateNumber.trim(),

                        "title" to
                                type.trim()
                                    .ifBlank {
                                        "Di-tiyak na Insidente"
                                    },

                        "location" to
                                location.trim()
                                    .ifBlank {
                                        "Di-tiyak na lokasyon"
                                    },

                        "date" to dateText,

                        "penalty" to cleanPenalty,

                        "description" to
                                description.trim(),

                        "sentToDriver" to sendToDriver,

                        "isResolved" to false,

                        "status" to "open",

                        "reportedBy" to
                                (
                                        profile
                                            ?.userId
                                            .orEmpty()
                                        ),

                        "createdAt" to
                                FieldValue.serverTimestamp()
                    )

                firestore
                    .collection(
                        FirestoreCollections.INCIDENTS
                    )
                    .document(id)
                    .set(data)
                    .await()

                ToastFeedback.show(
                    "Naitala ang insidente para sa ${plateNumber.trim()}."
                )

            } catch (e: Exception) {

                ToastFeedback.show(
                    e.message
                        ?: "Hindi ma-save ang insidente"
                )
            }
        }
    }

    fun resolveIncident(
        incidentId: String
    ) {

        viewModelScope.launch {

            try {

                firestore
                    .collection(
                        FirestoreCollections.INCIDENTS
                    )
                    .document(incidentId)
                    .update(
                        mapOf(
                            "isResolved" to true,
                            "status" to "resolved",
                            "resolvedAt" to
                                    FieldValue.serverTimestamp(),
                            "updatedAt" to
                                    FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                ToastFeedback.show(
                    "Naayos na ang insidente. Nasa Kasaysayan na ito."
                )

            } catch (e: Exception) {

                ToastFeedback.show(
                    e.message
                        ?: "Hindi ma-resolve ang insidente"
                )
            }
        }
    }

    private suspend fun findDriverDocumentByPlate(
        plateNumber: String
    ): DocumentSnapshot? {

        /*
         * The plate is compared the same way everywhere in the app, so
         * "abc 1234" and "ABC-1234" still find the same driver. The
         * stored value is never rewritten.
         */
        return firestore
            .collection(
                FirestoreCollections.USERS
            )
            .whereEqualTo(
                "role",
                "driver"
            )
            .get()
            .await()
            .documents
            .firstOrNull {
                PhilippineIdentifiers.platesMatch(
                    it.getString("plateNumber"),
                    plateNumber
                )
            }
    }

    private fun mapIncident(
        id: String,
        data: Map<String, Any>,
        createdAtMillis: Long = 0L
    ): IncidentItem {

        val driverName =
            data["driverName"] as? String
                ?: ""

        val plate =
            data["plateNumber"] as? String
                ?: ""

        val penaltyNumber =
            (data["penalty"] as? Number)
                ?.toDouble()
                ?: 0.0

        val penaltyText =
            if (penaltyNumber % 1.0 == 0.0) {
                penaltyNumber.toInt().toString()
            } else {
                penaltyNumber.toString()
            }

        return IncidentItem(
            id = id,

            title =
                data["title"] as? String
                    ?: "Insidente",

            plateNumber =
                if (driverName.isBlank()) {
                    plate
                } else {
                    "$plate — $driverName"
                },

            location =
                data["location"] as? String
                    ?: "",

            date =
                data["date"] as? String
                    ?: "",

            penalty =
                "₱$penaltyText",

            description =
                data["description"] as? String
                    ?: "",

            sentToDriver =
                if (data["sentToDriver"] as? Boolean ?: true) {
                    "Oo"
                } else {
                    "Hindi"
                },

            status =
                if (
                    data["isResolved"] == true ||
                    (data["status"] as? String)
                        ?.equals(
                            "resolved",
                            true
                        ) == true
                ) {
                    "Naayos"
                } else {
                    "Bukas"
                },

            createdAtMillis = createdAtMillis
        )
    }

    override fun onCleared() {
        incidentListener?.remove()
        driverListener?.remove()
        super.onCleared()
    }
}