package com.mobile.enerjeep.ui.schedule

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ScheduleUiState(
    val queuePosition: Int = 0,
    val queueStatus: String = "",
    val plateNumber: String = "",
    val route: String = "",
    val totalInQueue: Int = 0
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        val profile = userSession.userProfile
        if (profile != null) {
            listener = firestore.collection(FirestoreCollections.QUEUE)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val active = snapshot.documents
                        .filter { isActive(it.getString("status")) }
                        .sortedBy { it.getLong("position") ?: Long.MAX_VALUE }

                    val driverQueue = active.firstOrNull { document ->
                        document.getString("driverId") == profile.userId ||
                                document.getString("plateNumber") == profile.plateNumber
                    }

                    _uiState.value = ScheduleUiState(
                        queuePosition = driverQueue?.getLong("position")?.toInt() ?: 0,
                        queueStatus = driverQueue?.getString("status") ?: "Wala sa pila",
                        plateNumber = driverQueue?.getString("plateNumber") ?: profile.plateNumber.orEmpty(),
                        route = driverQueue?.getString("route").orEmpty(),
                        totalInQueue = active.size
                    )
                }
        }
    }

    private fun isActive(status: String?): Boolean {
        return when (status?.lowercase()) {
            "dispatched", "cancelled", "completed", "resolved" -> false
            else -> true
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
