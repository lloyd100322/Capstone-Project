package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.mobile.enerjeep.data.ActivityLogger
import com.mobile.enerjeep.data.FirestoreCollections
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

data class EmergencyContactItem(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val addedBy: String = ""
)

data class ActivityLogItem(
    val id: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val detail: String = "",
    val time: String = ""
)

data class DeductionTier(
    val seater: String = "",
    val amount: String = ""
)

/**
 * The SUPERADMIN toolbox: emergency list ("available admin"), the
 * audit log (Talaan ng Aktibidad) and the app settings (queue cut-off
 * and the deduction tiers per seater).
 */
@HiltViewModel
class SuperadminViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _emergencyContacts =
        MutableStateFlow<List<EmergencyContactItem>>(emptyList())
    val emergencyContacts = _emergencyContacts.asStateFlow()

    private val _activityLogs =
        MutableStateFlow<List<ActivityLogItem>>(emptyList())
    val activityLogs = _activityLogs.asStateFlow()

    private val _adminAccounts =
        MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val adminAccounts = _adminAccounts.asStateFlow()

    private val _tiers = MutableStateFlow(
        listOf(
            DeductionTier("16", "32"),
            DeductionTier("22", "44")
        )
    )
    val tiers = _tiers.asStateFlow()

    /*
     * Oras ng awtomatikong reset ng pila (0-23, default 21 = 9 PM).
     * Ito ang ginagamit ng QueueViewModel sa queue rotation.
     */
    private val _cutoffHour = MutableStateFlow("21")
    val cutoffHour = _cutoffHour.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        startListeners()
    }

    private fun startListeners() {
        listeners += firestore
            .collection(FirestoreCollections.EMERGENCY_CONTACTS)
            .addSnapshotListener { snapshot, _ ->
                _emergencyContacts.value = snapshot
                    ?.documents
                    ?.map { document ->
                        EmergencyContactItem(
                            id = document.id,
                            name = document.getString("name").orEmpty(),
                            number = document.getString("number").orEmpty(),
                            addedBy = document.getString("addedBy").orEmpty()
                        )
                    }
                    .orEmpty()
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
            }

        listeners += firestore
            .collection(FirestoreCollections.ACTIVITY_LOGS)
            .orderBy("createdAt",
                com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, _ ->
                _activityLogs.value = snapshot
                    ?.documents
                    ?.map { document ->
                        val timestamp = document.getTimestamp("createdAt")
                        ActivityLogItem(
                            id = document.id,
                            actorName = document
                                .getString("actorName").orEmpty(),
                            actorRole = document
                                .getString("actorRole").orEmpty(),
                            action = document
                                .getString("action").orEmpty(),
                            detail = document
                                .getString("detail").orEmpty(),
                            time = timestamp?.toDate()?.let {
                                SimpleDateFormat(
                                    "MMM dd, yyyy hh:mm a",
                                    Locale.getDefault()
                                ).format(it)
                            }.orEmpty()
                        )
                    }
                    .orEmpty()
            }

        // Admin accounts - the superadmin adds them to the emergency list.
        listeners += firestore
            .collection(FirestoreCollections.USERS)
            .whereIn("role", listOf("admin", "superadmin"))
            .addSnapshotListener { snapshot, _ ->
                _adminAccounts.value = snapshot
                    ?.documents
                    ?.map { document ->
                        val name = listOf(
                            document.getString("firstName"),
                            document.getString("lastName")
                        ).filter { !it.isNullOrBlank() }
                            .joinToString(" ")
                            .ifBlank { "Admin" }

                        name to
                                document.getString("phoneNumber").orEmpty()
                    }
                    .orEmpty()
            }

        viewModelScope.launch {
            try {
                val tiersDocument = firestore
                    .collection(FirestoreCollections.SETTINGS)
                    .document("deductionTiers")
                    .get()
                    .await()

                val stored = tiersDocument.data
                if (!stored.isNullOrEmpty()) {
                    _tiers.value = stored.entries
                        .filter { it.value is Number }
                        .map { entry ->
                            DeductionTier(
                                seater = entry.key,
                                amount = (entry.value as Number)
                                    .toInt()
                                    .toString()
                            )
                        }
                        .sortedBy {
                            it.seater.toIntOrNull() ?: 0
                        }
                }

                val rotationDocument = firestore
                    .collection(FirestoreCollections.SETTINGS)
                    .document("queueRotation")
                    .get()
                    .await()

                rotationDocument.getLong("cutoffHour")?.let { hour ->
                    _cutoffHour.value =
                        hour.toInt().coerceIn(0, 23).toString()
                }
            } catch (_: Exception) {
                // Defaults stay visible when the settings cannot load.
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun addEmergencyContact(name: String, number: String) {
        val cleanName = name.trim()
        val cleanNumber = number.trim()
        if (cleanName.isBlank() || cleanNumber.isBlank()) {
            _message.value = "Kumpletuhin ang pangalan at numero."
            return
        }

        viewModelScope.launch {
            try {
                val reference = firestore
                    .collection(FirestoreCollections.EMERGENCY_CONTACTS)
                    .document()

                reference.set(
                    mapOf(
                        "id" to reference.id,
                        "name" to cleanName,
                        "number" to cleanNumber,
                        "addedBy" to (userSession.userProfile?.firstName
                            ?: "Superadmin"),
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                ActivityLogger.log(
                    firestore, userSession,
                    "Emergency List",
                    "Idinagdag si $cleanName ($cleanNumber)"
                )

                _message.value = "Naidagdag si $cleanName sa listahan."
            } catch (e: Exception) {
                _message.value =
                    e.message ?: "Hindi maidagdag ang kontak."
            }
        }
    }

    fun removeEmergencyContact(item: EmergencyContactItem) {
        viewModelScope.launch {
            try {
                firestore
                    .collection(FirestoreCollections.EMERGENCY_CONTACTS)
                    .document(item.id)
                    .delete()
                    .await()

                ActivityLogger.log(
                    firestore, userSession,
                    "Emergency List",
                    "Inalis si ${item.name}"
                )

                _message.value = "Inalis si ${item.name}."
            } catch (e: Exception) {
                _message.value =
                    e.message ?: "Hindi maalis ang kontak."
            }
        }
    }

    fun updateTier(index: Int, seater: String, amount: String) {
        val current = _tiers.value.toMutableList()
        if (index !in current.indices) return
        current[index] = DeductionTier(seater, amount)
        _tiers.value = current
    }

    fun updateCutoffHour(value: String) {
        if (value.length <= 2 && value.all { it.isDigit() }) {
            _cutoffHour.value = value
        }
    }

    fun addTier() {
        _tiers.value = _tiers.value + DeductionTier("", "")
    }

    fun removeTier(index: Int) {
        val current = _tiers.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _tiers.value = current
    }

    fun saveSettings() {
        val tierMap = mutableMapOf<String, Any>()
        _tiers.value.forEach { tier ->
            val seater = tier.seater.trim().toIntOrNull()
            val amount = tier.amount.trim().toDoubleOrNull()
            if (seater != null && seater > 0 && amount != null) {
                tierMap[seater.toString()] = amount
            }
        }

        val cutoff = _cutoffHour.value.trim().toIntOrNull()
        if (cutoff == null || cutoff !in 0..23) {
            _message.value = "Pumili ng tamang oras ng reset."
            return
        }
        _cutoffHour.value = cutoff.toString()

        viewModelScope.launch {
            try {
                firestore
                    .collection(FirestoreCollections.SETTINGS)
                    .document("deductionTiers")
                    .set(tierMap)
                    .await()

                firestore
                    .collection(FirestoreCollections.SETTINGS)
                    .document("queueRotation")
                    .set(
                        mapOf(
                            "cutoffHour" to cutoff,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                ActivityLogger.log(
                    firestore, userSession,
                    "Settings",
                    "Oras ng reset: " +
                            com.mobile.enerjeep.ui.queue.QueueViewModel
                                .formatCutoffHour(cutoff) +
                            " | Bawas kada upuan: " +
                            tierMap.entries.joinToString(", ") {
                                "${it.key}=${it.value}"
                            }
                )

                _message.value = "Na-save ang settings."
            } catch (e: Exception) {
                _message.value =
                    e.message ?: "Hindi ma-save ang settings."
            }
        }
    }

    override fun onCleared() {
        listeners.forEach { it.remove() }
        super.onCleared()
    }
}
