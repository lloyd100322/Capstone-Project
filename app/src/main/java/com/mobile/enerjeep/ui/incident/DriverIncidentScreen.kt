package com.mobile.enerjeep.ui.incident

import com.mobile.enerjeep.ui.theme.responsivePageHorizontalPadding
import com.mobile.enerjeep.ui.theme.responsivePageTitleSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.SearchField
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.ErrorRedText
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.SuccessGreen
import com.mobile.enerjeep.ui.theme.WarningOrange
import com.mobile.enerjeep.ui.theme.WarningOrangeLight
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.TextSecondary
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.mobile.enerjeep.ui.components.ToastFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverIncidentScreen(
    focusIncidentId: String? = null,
    onFocusHandled: (String) -> Unit = {},
    viewModel: DriverIncidentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val isResolvedTab = uiState.selectedTab == IncidentTabType.NAAYOS
    val visibleIncidents = if (isResolvedTab) filterHistoryRecords(
        uiState.filteredIncidents,
        { it.date },
        historyFilter,
        { it.createdAtMillis.takeIf { millis -> millis > 0 } }
    ) else uiState.filteredIncidents
    var highlightedIncidentId by remember { mutableStateOf<String?>(null) }
    var pendingBringIntoViewId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusIncidentId, uiState.allIncidents, uiState.isLoading, uiState.errorMessage) {
        val requestedId = focusIncidentId ?: return@LaunchedEffect
        val target = uiState.allIncidents.firstOrNull { it.id == requestedId }
        if (target != null) {
            viewModel.onSearchQueryChanged("")
            viewModel.onTabSelected(
                if (target.isResolved) IncidentTabType.NAAYOS
                else IncidentTabType.DI_NAAYOS
            )
            historyFilter = HistoryFilterState()
            highlightedIncidentId = target.id
            pendingBringIntoViewId = target.id
            onFocusHandled(requestedId)
        } else if (!uiState.isLoading && uiState.errorMessage == null) {
            onFocusHandled(requestedId)
        }
    }

    Column(Modifier.fillMaxSize().background(LightGrayBackground).verticalScroll(rememberScrollState()).imePadding().padding(start = responsivePageHorizontalPadding(), end = responsivePageHorizontalPadding(), bottom = 32.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep App Logo", modifier = Modifier.size(60.dp))
            Spacer(Modifier.width(12.dp))
            Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.height(32.dp))
        Text("Insidente", fontSize = responsivePageTitleSize(), fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            IncidentSummaryCard(Modifier.weight(1f), uiState.openCount, "Di-naayos", WarningOrange, WarningOrange) { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            IncidentSummaryCard(Modifier.weight(1f), uiState.resolvedCount, "Naayos", SuccessGreen, SuccessGreen) { Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        SearchField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            placeholder = "Hanapin: uri / plaka / lugar"
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverTabButton(Modifier.weight(1f), "Di-naayos", uiState.selectedTab == IncidentTabType.DI_NAAYOS) { viewModel.onTabSelected(IncidentTabType.DI_NAAYOS) }
            DriverTabButton(Modifier.weight(1f), "Naayos", isResolvedTab) { viewModel.onTabSelected(IncidentTabType.NAAYOS) }
        }
        if (isResolvedTab) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.errorMessage ?: "", color = ErrorRedText, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::retryLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                ) { Text("Subukang muli") }
            }
        } else if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EnerjeepBlue)
            }
        } else if (visibleIncidents.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(if (isResolvedTab) "Walang naayos na insidente sa napiling filter." else "Walang insidenteng hindi pa naayos.", color = TextSecondary, fontSize = 14.sp) }
        } else {
            /*
             * The incident cards are part of the same page scroll as
             * the header, search field, tabs, and date filter.
             */
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                visibleIncidents.forEach { incident ->
                    val bringIntoViewRequester = remember(incident.id) {
                        BringIntoViewRequester()
                    }
                    LaunchedEffect(pendingBringIntoViewId, incident.id) {
                        if (pendingBringIntoViewId == incident.id) {
                            kotlinx.coroutines.delay(150)
                            bringIntoViewRequester.bringIntoView()
                            if (pendingBringIntoViewId == incident.id) {
                                pendingBringIntoViewId = null
                            }
                        }
                    }
                    DriverIncidentCard(
                        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
                        isHighlighted = incident.id == highlightedIncidentId,
                        incident = incident,
                        isLookingUpDriverPhone =
                            incident.id in uiState.lookingUpDriverPhoneIds,
                        onCallDriver = {
                            viewModel.lookupIncidentDriverPhone(incident.id) { phone ->
                                val number = phone ?: uiState.officePhone
                                val clean = number.filter {
                                    it.isDigit() || it == '+'
                                }
                                if (clean.isNotBlank()) {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:$clean")
                                        )
                                    )
                                } else {
                                    ToastFeedback.show(
                                        "Walang numero ng driver o opisina na matatawagan."
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun IncidentSummaryCard(modifier: Modifier = Modifier, count: Int, label: String, iconColor: Color, borderColor: Color, icon: @Composable () -> Unit) {
    Card(modifier.height(100.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().width(4.dp).background(borderColor))
            Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = iconColor.copy(alpha = .1f), modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { icon() } }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) { Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 3.dp)) }
            }
        }
    }
}

@Composable
fun DriverTabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier.heightIn(min = 48.dp).clickable(role = Role.Tab, onClick = onClick), shape = RoundedCornerShape(12.dp), color = if (isSelected) Color.White else Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = if (isSelected) Color.Black else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) }
    }
}

@Composable
fun DriverIncidentCard(
    incident: DriverIncident,
    isLookingUpDriverPhone: Boolean = false,
    onCallDriver: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) EnerjeepBlue.copy(alpha = 0.06f) else Color.White
        ),
        border = if (isHighlighted) {
            androidx.compose.foundation.BorderStroke(2.dp, EnerjeepBlue)
        } else {
            null
        },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            IncidentCardHeader(
                title = incident.title,
                status = if (incident.isResolved) "Naayos" else "Di-naayos",
                isResolved = incident.isResolved
            )
            Spacer(Modifier.height(12.dp))
            IncidentInfoRow("Plaka ng Sasakyan", incident.plateNumber)
            if (incident.location.isNotBlank()) IncidentInfoRow("Lokasyon", incident.location)
            if (incident.date.isNotBlank()) IncidentInfoRow("Petsa", incident.date)
            if (incident.multiya > 0.0) {
                IncidentInfoRow(
                    "Multa",
                    String.format(java.util.Locale.getDefault(), "₱%,.2f", incident.multiya)
                )
            }
            if (incident.description.isNotBlank()) {
                IncidentInfoRow("Paglalarawan", incident.description)
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCallDriver,
                enabled = !isLookingUpDriverPhone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
            ) {
                if (isLookingUpDriverPhone) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SuccessGreen
                    )
                } else {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isLookingUpDriverPhone) "Kinukuha ang numero..." else "Tawagan",
                    color = SuccessGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
