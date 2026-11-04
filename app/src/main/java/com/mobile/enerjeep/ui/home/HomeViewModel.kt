package com.mobile.enerjeep.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserProfile
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userSession: UserSession,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    val userProfile: UserProfile?
        get() = userSession.userProfile

    /**
     * Reactive profile. The Home screen collects this so the emergency
     * dialog always shows the CURRENT contact (it used to keep showing
     * the values from login even after the profile was edited).
     */
    val userProfileFlow = userSession.userProfileFlow

    private val _activeJeepsCount =
        MutableStateFlow(0)

    val activeJeepsCount =
        _activeJeepsCount.asStateFlow()

    private val _totalJeepsCount =
        MutableStateFlow(0)

    val totalJeepsCount =
        _totalJeepsCount.asStateFlow()

    /*
     * "Kabuuang Kita" of the dashboard: the sum of every collection that
     * was recorded in the ledger. It used to be a hardcoded "₱0" in the
     * screen, so the card never showed anything.
     */
    private val _totalSales =
        MutableStateFlow(0.0)

    val totalSales =
        _totalSales.asStateFlow()

    /*
     * "Insidente" of the dashboard: the number of the incidents that are
     * still open. It used to be a hardcoded "0" in the screen, so the card
     * showed nothing even when there were open incidents.
     */
    private val _openIncidentCount =
        MutableStateFlow(0)

    val openIncidentCount =
        _openIncidentCount.asStateFlow()

    /*
     * "Donasyon" ng dashboard: kabuuang halaga ng lahat ng donasyon
     * na naitala. Kapareho ng "Kabuuang Kita" - clickable ang card at
     * dumidiretso sa mismong Donasyon screen (hindi lang sa Mga Ulat).
     */
    private val _totalDonasyon =
        MutableStateFlow(0.0)

    val totalDonasyon =
        _totalDonasyon.asStateFlow()

    /*
     * The superadmin-managed "available admin" emergency list. Drivers
     * see these contacts in the Emergency dialog.
     */
    private val _emergencyContacts =
        MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val emergencyContacts =
        _emergencyContacts.asStateFlow()

    /* 
     * Every listener is registered here and removed in onCleared().
     * Without this the app leaked four full-collection listeners on
     * every visit to the Home screen.
     */
    private val listeners =
        mutableListOf<ListenerRegistration>()

    /*
     * Keep the latest snapshots together so both dashboard counts are
     * recalculated whenever either the driver accounts or queue changes.
     */
    private var latestDriverAccounts: List<DashboardDriverAccount>? = null
    private var latestQueueEntries: List<DashboardQueueEntry>? = null

    init {
        startListeners()
    }

    private fun startListeners() {

        listeners += firestore
            .collection(FirestoreCollections.EMERGENCY_CONTACTS)
            .addSnapshotListener { snapshot, _ ->
                _emergencyContacts.value = snapshot
                    ?.documents
                    ?.filter {
                        it.getBoolean("isActive") != false
                    }
                    ?.map { document ->
                        document.getString("name").orEmpty() to
                                document.getString("number").orEmpty()
                    }
                    .orEmpty()
            }

        // Dashboard-wide data is admin-only. Driver screens load their
        // own authorized datasets through DriverViewModel.
        if (!userSession.isAdmin()) return

        /*
         * Registered driver accounts. The account document ID is the
         * phone number, so use the stable userId field to match queue rows.
         * Legacy accounts without userId fall back to their document ID,
         * matching the queue-recovery logic.
         */
        listeners += firestore
            .collection(FirestoreCollections.USERS)
            .whereEqualTo("role", "driver")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HomeViewModel", "Unable to listen for driver accounts", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                latestDriverAccounts = snapshot.documents.map { document ->
                    DashboardDriverAccount(
                        driverId = document.getString("userId")
                            .orEmpty()
                            .ifBlank { document.id },
                        isVehicleActive = document.getBoolean("isVehicleActive") != false,
                        status = document.getString("status").orEmpty()
                    )
                }
                updateDashboardDriverCounts()
            }

        /*
         * Queue rows are the source of truth for cancellation/pause.
         * A cancelled row remains stored so it can be restored, but it
         * must be excluded from BOTH dashboard numbers until restored.
         */
        listeners += firestore
            .collection(FirestoreCollections.QUEUE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HomeViewModel", "Unable to listen for queue changes", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                latestQueueEntries = snapshot.documents.map { document ->
                    DashboardQueueEntry(
                        driverId = document.getString("driverId")
                            .orEmpty()
                            .ifBlank { document.getString("userId").orEmpty() },
                        status = document.getString("status").orEmpty(),
                        isCancelled = document.getBoolean("isCancelled") == true
                    )
                }
                updateDashboardDriverCounts()
            }

        /*
         * The total money of the cooperative. Every collection that was
         * recorded in the ledger is added up.
         */
        listeners += firestore
            .collection(
                FirestoreCollections.LEDGER
            )
            .addSnapshotListener {
                    snapshot,
                    _ ->

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val entries =
                    snapshot.documents.map {
                        document ->
                        DashboardLedgerEntry(
                            amount =
                                (
                                        document.get(
                                            "amount"
                                        )
                                            as? Number
                                    )
                                        ?.toDouble(),
                            netAmount =
                                (
                                        document.get(
                                            "netAmount"
                                        )
                                            as? Number
                                    )
                                        ?.toDouble(),
                            otherFees =
                                (
                                        document.get(
                                            "otherFees"
                                        )
                                            as? Number
                                    )
                                        ?.toDouble()
                        )
                    }

                _totalSales.value =
                    DashboardSummary.totalSales(
                        entries
                    )
            }

        /*
         * The number of the incidents that are still open. The same rule
         * as the Insidente screen: an incident is closed when it is
         * marked as resolved.
         */
        listeners += firestore
            .collection(
                FirestoreCollections.INCIDENTS
            )
            .addSnapshotListener {
                    snapshot,
                    _ ->

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val entries =
                    snapshot.documents.map {
                        document ->
                        DashboardIncidentEntry(
                            isResolved =
                                document.getBoolean(
                                    "isResolved"
                                ) == true,
                            status =
                                document.getString(
                                    "status"
                                ) ?: "open"
                        )
                    }

                _openIncidentCount.value =
                    DashboardSummary.openIncidentCount(
                        entries
                    )
            }

        /*
         * The total donations of the cooperative ("Donasyon" card).
         */
        listeners += firestore
            .collection(
                FirestoreCollections.DONATIONS
            )
            .addSnapshotListener {
                    snapshot,
                    _ ->

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                _totalDonasyon.value =
                    snapshot.documents.sumOf {
                        document ->
                        val amount =
                            (
                                    document.get(
                                        "amount"
                                    )
                                        as? Number
                                )
                                ?.toDouble()
                                ?: 0.0
                        val type =
                            document
                                .getString("type")
                                ?.lowercase()
                                ?: "donate"
                        /*
                         * Ang card ay nagpapakita ng NATITIRANG pondo:
                         * nakalap (donate) minus nagamit (gamit).
                         */
                        if (type == "gamit") -amount else amount
                    }
            }
    }

    /**
     * Recalculate the numerator and denominator together. This prevents
     * the denominator from continuing to count a driver whose queue entry
     * is currently cancelled/paused. Restoring that same queue entry
     * (isCancelled=false) automatically adds the driver back.
     */
    private fun updateDashboardDriverCounts() {
        val driverAccounts = latestDriverAccounts ?: return
        val queueEntries = latestQueueEntries ?: return

        _activeJeepsCount.value =
            DashboardSummary.activeDriverCount(queueEntries)

        _totalJeepsCount.value =
            DashboardSummary.totalActiveDriverCount(
                driverAccounts,
                queueEntries
            )
    }

    fun isAdmin(): Boolean =
        userSession.isAdmin()

    fun isDriver(): Boolean =
        userSession.isDriver()


    override fun onCleared() {
        listeners.forEach { it.remove() }
        listeners.clear()
        super.onCleared()
    }
}
