package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.ErrorRed
import com.mobile.enerjeep.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Date?
)

data class NotificationCenterState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = true,
    val markingRead: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationCenterState())
    val uiState = _uiState.asStateFlow()

    private var notificationListener: ListenerRegistration? = null
    private var unreadCountListener: ListenerRegistration? = null
    private var activePhone: String? = null

    init {
        viewModelScope.launch {
            userSession.userProfileFlow.collectLatest { profile ->
                notificationListener?.remove()
                notificationListener = null
                unreadCountListener?.remove()
                unreadCountListener = null
                val phone = profile?.phoneNumber?.trim()?.lowercase()
                    ?.takeIf { it.isNotBlank() }
                activePhone = phone
                _uiState.value = NotificationCenterState(
                    notifications = emptyList(),
                    unreadCount = 0,
                    loading = phone != null
                )
                if (phone != null) observeNotifications(phone)
            }
        }
    }

    private fun observeNotifications(phone: String) {
        notificationListener?.remove()
        unreadCountListener?.remove()
        val notifications = firestore
            .collection(FirestoreCollections.USERS)
            .document(phone)
            .collection(FirestoreCollections.NOTIFICATIONS)

        _uiState.value = _uiState.value.copy(
            loading = true,
            errorMessage = null
        )
        notificationListener = notifications
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (activePhone != phone) return@addSnapshotListener
                if (error != null) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        errorMessage = "Hindi ma-load ang mga abiso. Suriin ang internet at subukang muli."
                    )
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().map { document ->
                    AppNotification(
                        id = document.id,
                        type = document.getString("type").orEmpty(),
                        title = document.getString("title") ?: "Bagong Abiso",
                        body = document.getString("body").orEmpty(),
                        read = document.getBoolean("read") == true,
                        createdAt = document.getTimestamp("createdAt")?.toDate()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    notifications = items,
                    loading = false
                )
            }

        // Keep the numeric badge accurate for the whole collection, not
        // just the 50 most recent notifications displayed in the sheet.
        unreadCountListener = notifications
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (activePhone != phone ||
                    error != null ||
                    _uiState.value.markingRead
                ) return@addSnapshotListener
                _uiState.value = _uiState.value.copy(
                    unreadCount = snapshot?.size() ?: 0
                )
            }
    }

    fun retryLoading() {
        activePhone?.let(::observeNotifications)
    }

    fun markAllRead() {
        val phone = activePhone ?: return
        if (_uiState.value.markingRead) return

        // Remove the unread state immediately when the bell is opened.
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map { it.copy(read = true) },
            unreadCount = 0,
            markingRead = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val notifications = firestore
                    .collection(FirestoreCollections.USERS)
                    .document(phone)
                    .collection(FirestoreCollections.NOTIFICATIONS)

                // Read every notification, not only the latest 50 displayed in the sheet.
                val unreadDocuments = notifications.get().await().documents
                    .filter { it.getBoolean("read") != true }

                unreadDocuments.chunked(450).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { item ->
                        batch.update(
                            notifications.document(item.id),
                            mapOf(
                                "read" to true,
                                "readAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().await()
                }

                // Catch notifications that arrived while the read batches ran.
                val remainingUnread = notifications.get().await().documents.count {
                    it.getBoolean("read") != true
                }
                if (activePhone == phone) {
                    _uiState.value = _uiState.value.copy(
                        unreadCount = remainingUnread
                    )
                }
            } catch (_: Exception) {
                ToastFeedback.show("Hindi ma-update ang status ng mga abiso. Subukang muli.")
                if (activePhone == phone) observeNotifications(phone)
            } finally {
                if (activePhone == phone) {
                    _uiState.value = _uiState.value.copy(markingRead = false)
                }
            }
        }
    }

    override fun onCleared() {
        notificationListener?.remove()
        notificationListener = null
        unreadCountListener?.remove()
        unreadCountListener = null
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterBell(
    viewModel: NotificationCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    // Bell only: the existing dashboard header supplies the app title.
    Box {
        BadgedBox(
            badge = {
                if (state.unreadCount > 0) {
                    Badge(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    ) {
                        Text(
                            if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
        IconButton(
            onClick = {
                viewModel.markAllRead()
                showSheet = true
            }
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Mga Abiso",
                tint = EnerjeepBlue,
                modifier = Modifier.size(27.dp)
            )
        }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mga Abiso",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showSheet = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Isara")
                }
            }

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EnerjeepBlue)
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            state.errorMessage.orEmpty(),
                            color = ErrorRed
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::retryLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                        ) {
                            Text("Subukang muli", maxLines = 1)
                        }
                    }
                }
                state.notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Wala pang abiso.",
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.notifications.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.read) {
                                        Color(0xFFF6F8FA)
                                    } else {
                                        Color(0xFFEAF2FF)
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            item.title,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF172033)
                                        )
                                        if (!item.read) {
                                            Surface(
                                                shape = CircleShape,
                                                color = ErrorRed,
                                                modifier = Modifier.size(8.dp)
                                            ) {}
                                        }
                                    }
                                    if (item.body.isNotBlank()) {
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            item.body,
                                            fontSize = 13.sp,
                                            color = Color(0xFF465368),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    item.createdAt?.let { createdAt ->
                                        Spacer(Modifier.height(7.dp))
                                        Text(
                                            formatNotificationDate(createdAt),
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun formatNotificationDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }
    return formatter.format(date)
}
