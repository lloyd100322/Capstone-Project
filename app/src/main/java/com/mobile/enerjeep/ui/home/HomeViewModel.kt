package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
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

    private val _activeJeepsCount =
        MutableStateFlow(0)

    val activeJeepsCount =
        _activeJeepsCount.asStateFlow()

    private val _totalJeepsCount =
        MutableStateFlow(0)

    val totalJeepsCount =
        _totalJeepsCount.asStateFlow()

    /*
     * "Kabuuang Benta" of the dashboard: the sum of every collection that
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

    init {
        startListeners()
    }

    private fun startListeners() {

        /*
         * Total registered drivers.
         */
        firestore
            .collection(
                FirestoreCollections.USERS
            )
            .whereEqualTo(
                "role",
                "driver"
            )
            .addSnapshotListener {
                    snapshot,
                    _ ->

                if (snapshot != null) {
                    _totalJeepsCount.value =
                        snapshot.size()
                }
            }

        /*
         * Active jeep count is based on the actual
         * dispatch queue, not simply the number of
         * registered driver accounts.
         */
        firestore
            .collection(
                FirestoreCollections.QUEUE
            )
            .addSnapshotListener {
                    snapshot,
                    _ ->

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val activeDriverIds =
                    snapshot
                        .documents
                        .filter {
                            isActiveQueueStatus(
                                it.getString(
                                    "status"
                                )
                            )
                        }
                        .map {
                            it.getString(
                                "driverId"
                            ).orEmpty()
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .toSet()

                /*
                 * Distinct driver IDs prevent old duplicate
                 * queue documents from inflating the count.
                 */
                _activeJeepsCount.value =
                    activeDriverIds.size
            }

        /*
         * The total money of the cooperative. Every collection that was
         * recorded in the ledger is added up.
         */
        firestore
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
        firestore
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
    }

    private fun isActiveQueueStatus(
        status: String?
    ): Boolean {

        return when (
            status?.lowercase()
        ) {

            "dispatched",
            "cancelled",
            "completed",
            "resolved" -> false

            else -> true
        }
    }

    fun isAdmin(): Boolean =
        userSession.isAdmin()

    fun isDriver(): Boolean =
        userSession.isDriver()
}