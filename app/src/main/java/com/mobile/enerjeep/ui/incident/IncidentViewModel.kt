package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.ActivityLogger
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

    /*
     * Fase 6: ang dropdown ay "dispatched only" - mga jeep na may
     * dispatch record lang (aktibong bumibiyahe). Kasama ang phone
     * map para sa tel: call button ng admin.
     */
    private val _driverPhones =
        MutableStateFlow<Map<String, String>>(emptyMap())

    val driverPhones = _driverPhones.asStateFlow()

    private var allDriverOptions: List<IncidentDriverOption> =
        emptyList()

    private var dispatchedPlates: Set<String> = emptySet()

    private fun refreshDriverOptions() {
        _driverOptions.value = allDriverOptions.filter {
            it.plateNumber in dispatchedPlates
        }
    }

    private var incidentListener: ListenerRegistration? = null
    private var driverListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null

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

                    allDriverOptions =
                        snapshot.documents
                            .mapNotNull {
                                mapDriverOption(it)
                            }
                            .sortedBy {
                                it.plateNumber.lowercase()
                            }

                    _driverPhones.value = snapshot.documents
                        .mapNotNull { document ->
                            val plate =
                                document.getString("plateNumber")
                                    .orEmpty()
                                    .trim()

                            val phone = document.id.ifBlank {
                                document.getString("phoneNumber")
                                    .orEmpty()
                            }

                            if (plate.isBlank() ||
                                phone.isBlank()
                            ) {
                                null
                            } else {
                                plate to phone
                            }
                        }
                        .toMap()

                    refreshDriverOptions()
                }

        /*
         * "dispatched only": sinasala ng queue listener kung aling
         * plaka ang may dispatch record (status = "dispatched").
         */
        queueListener =
            firestore.collection(FirestoreCollections.QUEUE)
                .whereEqualTo("status", "dispatched")
                .addSnapshotListener { snapshot, _ ->
                    dispatchedPlates = snapshot?.documents
                        ?.mapNotNull {
                            it.getString("plateNumber")
                                ?.trim()
                                ?.takeIf { plate -> plate.isNotBlank() }
                        }
                        ?.toSet()
                        .orEmpty()

                    refreshDriverOptions()
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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Ipinadala sa Driver",
                    incidentId
                )

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

                /*
                 * Numero ng driver na itinatago SA mismong incident record.
                 * Ang /users ay hindi mababasa ng mga driver (rules), kaya
                 * para sa "Tawag" button ng driver screen, ang numero ay
                 * kinukuha dito. Ang doc ID ng user ay ang phone number.
                 */
                val driverPhone =
                    driverDocument
                        .id
                        .trim()
                        .ifBlank {
                            driverDocument
                                .getString("phoneNumber")
                                .orEmpty()
                                .trim()
                        }

                val data =
                    mapOf<String, Any>(

                        "id" to id,

                        "driverId" to
                                driverId,

                        "driverName" to
                                driverName,

                        "driverPhone" to
                                driverPhone,

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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Bagong Insidente",
                    "${plateNumber.trim()} • $type • $location"
                )

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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Naayos na Insidente",
                    incidentId
                )

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
        queueListener?.remove()
        super.onCleared()
    }
}