package com.mobile.enerjeep.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.data.FirestoreIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MonthlyIncome(
    val period: String,
    val trips: Int,
    val amount: Double,
    val growthPercentage: Double? = null
)

data class YearlyIncome(
    val year: String,
    val activeMonths: Int,
    val amount: Double
)

data class IncomeHistory(
    val plateNumber: String,
    val driverName: String,
    val netAmount: Double,
    val grossAmount: Double,
    val date: String
)

data class ReportIncidentItem(
    val title: String,
    val plateNumber: String,
    val location: String,
    val date: String,
    val penalty: Double
)

data class ReportMultiyaItem(
    val plateNumber: String,
    val driverName: String,
    val date: String,
    val amount: Double,
    // When the record was saved. Used to split "Ngayon" from the history
    // and to spot a fine that is written in two collections.
    val createdAtMillis: Long = 0L
)

data class DonasyonItem(
    val donor: String,
    val amount: Double,
    val date: String,
    val source: String,
    val note: String
)

sealed interface ReportUiState {

    data object Loading :
        ReportUiState

    data class Success(
        val monthlyData: List<MonthlyIncome>,
        val yearlyData: List<YearlyIncome>,
        val historyData: List<IncomeHistory>,
        val currentIncidents: List<ReportIncidentItem>,
        val historyIncidents: List<ReportIncidentItem>,
        // Every incident, open and already resolved. The "Kasaysayan" tab
        // shows this list, so an incident that was made today is written
        // in the history as well.
        val allIncidents: List<ReportIncidentItem>,
        val totalIncidentPenalty: Double,
        val currentMultiya: List<ReportMultiyaItem>,
        val historyMultiya: List<ReportMultiyaItem>,
        // Every fine. The "Kasaysayan" tab shows this list, so a fine that
        // was made today is written in the history as well.
        val allMultiya: List<ReportMultiyaItem>,
        val multiyaNgayon: Double,
        val kabuuangMultiya: Double,
        val currentDonasyon: List<DonasyonItem>,
        val historyDonasyon: List<DonasyonItem>,
        // Every donation. The "Kasaysayan" tab shows this list, so a
        // donation that was made today is written in the history as well.
        val allDonasyon: List<DonasyonItem>,
        val donasyonNgayon: Double,
        val kabuuangDonasyon: Double
    ) : ReportUiState

    data class Error(
        val message: String
    ) : ReportUiState
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: com.mobile.enerjeep.data.UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ReportUiState>(
            ReportUiState.Loading
        )

    val uiState =
        _uiState.asStateFlow()

    private var ledgerDocs =
        listOf<DocumentSnapshot>()

    private var incidentDocs =
        listOf<DocumentSnapshot>()

    private var kitaDocs =
        listOf<DocumentSnapshot>()

    private var donationDocs =
        listOf<DocumentSnapshot>()

    private val listeners =
        mutableListOf<ListenerRegistration>()

    init {
        attachListeners()
    }

    private fun attachListeners() {

        listeners +=
            firestore
                .collection(
                    FirestoreCollections.LEDGER
                )
                .addSnapshotListener {
                        snapshot,
                        error ->

                    if (error != null) {

                        _uiState.value =
                            ReportUiState.Error(
                                error.message
                                    ?: "Hindi mabasa ang ledger report"
                            )

                        return@addSnapshotListener
                    }

                    ledgerDocs =
                        snapshot
                            ?.documents
                            .orEmpty()

                    rebuild()
                }

        listeners +=
            firestore
                .collection(
                    FirestoreCollections.INCIDENTS
                )
                .addSnapshotListener {
                        snapshot,
                        _ ->

                    incidentDocs =
                        snapshot
                            ?.documents
                            .orEmpty()

                    rebuild()
                }

        listeners +=
            firestore
                .collection(
                    FirestoreCollections.KITA
                )
                .addSnapshotListener {
                        snapshot,
                        _ ->

                    kitaDocs =
                        snapshot
                            ?.documents
                            .orEmpty()

                    rebuild()
                }

        listeners +=
            firestore
                .collection(
                    FirestoreCollections.DONATIONS
                )
                .addSnapshotListener {
                        snapshot,
                        _ ->

                    donationDocs =
                        snapshot
                            ?.documents
                            .orEmpty()

                    rebuild()
                }
    }

    private fun rebuild() {

        /*
         * -----------------------------
         * INCOME REPORT
         * -----------------------------
         */

        val monthly =
            ledgerDocs
                .mapNotNull { document ->

                    val key =
                        monthKey(document)

                    if (key.isBlank()) {
                        null
                    } else {
                        key to
                                amountForLedger(
                                    document
                                )
                    }
                }
                .groupBy(
                    {
                        it.first
                    },
                    {
                        it.second
                    }
                )
                .toSortedMap(
                    compareByDescending {
                        it
                    }
                )
                .map { (key, amounts) ->

                    val current =
                        amounts.sum()

                    val previousKey =
                        try {
                            YearMonth
                                .parse(key)
                                .minusMonths(1)
                                .toString()
                        } catch (_: Exception) {
                            null
                        }

                    val previous =
                        if (
                            previousKey != null
                        ) {
                            ledgerDocs
                                .filter {
                                    monthKey(
                                        it
                                    ) ==
                                            previousKey
                                }
                                .sumOf(
                                    ::amountForLedger
                                )
                        } else {
                            0.0
                        }

                    MonthlyIncome(
                        period =
                            formatMonth(
                                key
                            ),

                        trips =
                            ledgerDocs.count {
                                monthKey(
                                    it
                                ) == key
                            },

                        amount =
                            current,

                        growthPercentage =
                            if (
                                previous > 0.0
                            ) {
                                (
                                        (
                                                current -
                                                        previous
                                                ) /
                                                previous
                                        ) * 100.0
                            } else {
                                null
                            }
                    )
                }

        val yearly =
            ledgerDocs
                .mapNotNull {
                    yearKey(it)
                }
                .distinct()
                .sortedDescending()
                .map { year ->

                    val yearDocs =
                        ledgerDocs.filter {
                            yearKey(it) == year
                        }

                    YearlyIncome(
                        year =
                            year,

                        activeMonths =
                            yearDocs
                                .mapNotNull(
                                    ::monthKey
                                )
                                .distinct()
                                .size,

                        amount =
                            yearDocs
                                .sumOf(
                                    ::amountForLedger
                                )
                    )
                }

        val incomeHistory =
            ledgerDocs
                .sortedByDescending {
                    timestampSeconds(
                        it
                    )
                }
                .map {

                    IncomeHistory(
                        plateNumber =
                            it.getString(
                                "plateNumber"
                            )
                                .orEmpty(),

                        driverName =
                            it.getString(
                                "driverName"
                            )
                                .orEmpty(),

                        netAmount =
                            it.getDouble(
                                "netAmount"
                            )
                                ?: it.getDouble(
                                    "amount"
                                )
                                ?: 0.0,

                        grossAmount =
                            it.getDouble(
                                "grossAmount"
                            )
                                ?: it.getDouble(
                                    "amount"
                                )
                                ?: 0.0,

                        date =
                            it.getString(
                                "date"
                            )
                                ?: formatDate(it)
                    )
                }

        /*
         * -----------------------------
         * INCIDENT REPORT
         * -----------------------------
         */

        val allIncidents =
            incidentDocs
                .map(
                    ::mapReportIncident
                )

        val currentIncidents =
            incidentDocs
                .filterNot(
                    ::isResolved
                )
                .map(
                    ::mapReportIncident
                )

        val historyIncidents =
            incidentDocs
                .filter(
                    ::isResolved
                )
                .map(
                    ::mapReportIncident
                )

        val totalIncidentPenalty =
            incidentDocs.sumOf {
                it.getDouble(
                    "penalty"
                ) ?: 0.0
            }

        /*
         * -----------------------------
         * MULTIYA REPORT
         * -----------------------------
         *
         * "Ngayon" means today's records.
         *
         * A fine can be saved in two places in this app. The "kita"
         * collection keeps records entered with the "Add collection"
         * button (type = "Multa"); the "ledger" collection is the source
         * of truth for dispatch earnings, so the ledger also holds a
         * row when the fee type is "Multa". Both rows describe the same
         * fine and both must be counted once.
         */
        val kitaMultiya =
            kitaDocs
                .filter {
                    MultiyaReport.isKitaMultiya(
                        it.getString("type")
                    )
                }
                .map(
                    ::mapReportMultiya
                )

        // Fines written through the "ledger" collection. Only the
        // feeType matters, so a terminal fee or a dispatch earning is
        // never counted as a fine.
        val ledgerMultiya =
            ledgerDocs
                .filter {
                    MultiyaReport.isLedgerMultiya(
                        it.getString("feeType")
                    )
                }
                .map(
                    ::mapReportMultiya
                )

        // A fine that is written in both collections is only counted once.
        val multiyaSummary =
            MultiyaReport.build(
                kitaMultiya,
                ledgerMultiya
            )

        val currentMultiya = multiyaSummary.current

        val historyMultiya = multiyaSummary.history

        val allMultiya = multiyaSummary.all

        val multiyaNgayon =
            multiyaSummary.currentTotal

        val kabuuangMultiya =
            multiyaSummary.total

        /*
         * -----------------------------
         * DONATION REPORT
         * -----------------------------
         */

        val allDonasyon =
            donationDocs
                .map(
                    ::mapDonation
                )

        val currentDonasyon =
            donationDocs
                .filter {
                    isTodayDocument(it)
                }
                .map(
                    ::mapDonation
                )

        val historyDonasyon =
            donationDocs
                .filter {
                    !isTodayDocument(it)
                }
                .map(
                    ::mapDonation
                )

        val donasyonNgayon =
            currentDonasyon.sumOf {
                it.amount
            }

        val kabuuangDonasyon =
            donationDocs.sumOf {
                it.getDouble(
                    "amount"
                ) ?: 0.0
            }

        _uiState.value =
            ReportUiState.Success(

                monthlyData =
                    monthly,

                yearlyData =
                    yearly,

                historyData =
                    incomeHistory,

                currentIncidents =
                    currentIncidents,

                historyIncidents =
                    historyIncidents,

                allIncidents =
                    allIncidents,

                totalIncidentPenalty =
                    totalIncidentPenalty,

                currentMultiya =
                    currentMultiya,

                historyMultiya =
                    historyMultiya,

                allMultiya =
                    allMultiya,

                multiyaNgayon =
                    multiyaNgayon,

                kabuuangMultiya =
                    kabuuangMultiya,

                currentDonasyon =
                    currentDonasyon,

                historyDonasyon =
                    historyDonasyon,

                allDonasyon =
                    allDonasyon,

                donasyonNgayon =
                    donasyonNgayon,

                kabuuangDonasyon =
                    kabuuangDonasyon
            )
    }

    fun addDonasyon(
        donasyon: DonasyonItem
    ) {

        val id =
            FirestoreIds.donation()

        val data =
            mapOf(

                "id" to
                        id,

                "donor" to
                        donasyon
                            .donor
                            .trim(),

                "amount" to
                        donasyon.amount,

                "date" to
                        donasyon.date,

                "source" to
                        donasyon.source,

                "note" to
                        donasyon.note,

                "isHistory" to
                        false,

                "createdAt" to
                        FieldValue
                            .serverTimestamp()
            )

        viewModelScope.launch {

            try {

                firestore
                    .collection(
                        FirestoreCollections
                            .DONATIONS
                    )
                    .document(id)
                    .set(data)
                    .await()

                com.mobile.enerjeep.data.ActivityLogger.log(
                    firestore,
                    userSession,
                    "Donasyon",
                    "₱" + (donasyon.amount ?: 0.0).toString()
                )

            } catch (e: Exception) {

                _uiState.value =
                    ReportUiState.Error(
                        e.message
                            ?: "Hindi ma-save ang donasyon"
                    )
            }
        }
    }

    private fun mapReportIncident(
        document: DocumentSnapshot
    ): ReportIncidentItem {

        return ReportIncidentItem(

            title =
                document
                    .getString(
                        "title"
                    )
                    .orEmpty(),

            plateNumber =
                document
                    .getString(
                        "plateNumber"
                    )
                    .orEmpty(),

            location =
                document
                    .getString(
                        "location"
                    )
                    .orEmpty(),

            date =
                document
                    .getString(
                        "date"
                    )
                    ?: formatDate(
                        document
                    ),

            penalty =
                document
                    .getDouble(
                        "penalty"
                    )
                    ?: 0.0
        )
    }

    private fun mapReportMultiya(
        document: DocumentSnapshot
    ): ReportMultiyaItem {

        return ReportMultiyaItem(

            plateNumber =
                document
                    .getString(
                        "plateNumber"
                    )
                    .orEmpty(),

            driverName =
                document
                    .getString(
                        "driverName"
                    )
                    .orEmpty(),

            date =
                document
                    .getString(
                        "date"
                    )
                    ?.takeIf { it.isNotBlank() }
                    ?: formatDate(
                        document
                    ),

            amount =
                (
                        document.get("amount")
                            as? Number
                        )
                    ?.toDouble()
                    ?: 0.0,

            createdAtMillis =
                document
                    .safeTimestamp("createdAt")
                    ?.toDate()
                    ?.time
                    ?: 0L
        )
    }

    private fun mapDonation(
        document: DocumentSnapshot
    ): DonasyonItem {

        return DonasyonItem(

            donor =
                document
                    .getString(
                        "donor"
                    )
                    .orEmpty(),

            amount =
                document
                    .getDouble(
                        "amount"
                    )
                    ?: 0.0,

            date =
                document
                    .getString(
                        "date"
                    )
                    ?: formatDate(
                        document
                    ),

            source =
                document
                    .getString(
                        "source"
                    )
                    .orEmpty(),

            note =
                document
                    .getString(
                        "note"
                    )
                    .orEmpty()
        )
    }

    private fun isResolved(
        document: DocumentSnapshot
    ): Boolean {

        return document
            .getBoolean(
                "isResolved"
            ) == true ||

                document
                    .getString(
                        "status"
                    )
                    ?.equals(
                        "resolved",
                        ignoreCase = true
                    ) == true
    }

    private fun isTodayDocument(
        document: DocumentSnapshot
    ): Boolean {

        val timestamp =
            document.safeTimestamp(
                "createdAt"
            )

        if (timestamp != null) {
            return isToday(
                timestamp
            )
        }

        val date =
            document.getString(
                "date"
            )

        if (date.isNullOrBlank()) {
            return false
        }

        return try {

            val parsed =
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).parse(date)

            if (parsed == null) {
                false
            } else {

                val key =
                    SimpleDateFormat(
                        "yyyyMMdd",
                        Locale.getDefault()
                    )

                key.format(
                    parsed
                ) ==
                        key.format(
                            Date()
                        )
            }

        } catch (_: Exception) {
            false
        }
    }

    private fun isToday(
        timestamp:
        com.google.firebase.Timestamp
    ): Boolean {

        val key =
            SimpleDateFormat(
                "yyyyMMdd",
                Locale.getDefault()
            )

        return key.format(
            timestamp.toDate()
        ) ==
                key.format(
                    Date()
                )
    }

    private fun amountForLedger(
        document: DocumentSnapshot
    ): Double {

        return document
            .getDouble(
                "netAmount"
            )
            ?: document
                .getDouble(
                    "amount"
                )
            ?: 0.0
    }

    private fun timestampSeconds(
        document: DocumentSnapshot
    ): Long {

        return document
            .safeTimestamp(
                "createdAt"
            )
            ?.seconds
            ?: 0L
    }

    private fun monthKey(
        document: DocumentSnapshot
    ): String {

        val timestamp =
            document.safeTimestamp(
                "createdAt"
            )

        if (timestamp != null) {

            return LocalDateTime
                .ofInstant(
                    timestamp
                        .toDate()
                        .toInstant(),
                    ZoneId
                        .systemDefault()
                )
                .toLocalDate()
                .toString()
                .substring(
                    0,
                    7
                )
        }

        val date =
            document
                .getString(
                    "date"
                )
                ?: return ""

        return try {

            val parsed =
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                )
                    .parse(date)
                    ?: return ""

            SimpleDateFormat(
                "yyyy-MM",
                Locale.getDefault()
            ).format(
                parsed
            )

        } catch (_: Exception) {
            ""
        }
    }

    private fun yearKey(
        document: DocumentSnapshot
    ): String {

        return monthKey(
            document
        )
            .take(4)
            .takeIf {
                it.length == 4
            }
            .orEmpty()
    }

    private fun formatMonth(
        key: String
    ): String {

        return try {

            val ym =
                YearMonth.parse(
                    key
                )

            ym.month.getDisplayName(
                java.time.format.TextStyle.FULL,
                Locale.getDefault()
            ) +
                    " " +
                    ym.year

        } catch (_: Exception) {
            key
        }
    }

    private fun formatDate(
        document: DocumentSnapshot
    ): String {

        return document
            .safeTimestamp(
                "createdAt"
            )
            ?.toDate()
            ?.let {

                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).format(it)

            }
            .orEmpty()
    }

    override fun onCleared() {

        listeners.forEach {
            it.remove()
        }

        super.onCleared()
    }
}