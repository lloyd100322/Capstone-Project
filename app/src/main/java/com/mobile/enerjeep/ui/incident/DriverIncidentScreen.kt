package com.mobile.enerjeep.ui.incident

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.SuccessGreen
import com.mobile.enerjeep.ui.theme.WarningOrange
import com.mobile.enerjeep.ui.theme.WarningOrangeLight
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverIncidentScreen(viewModel: DriverIncidentViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val isResolvedTab = uiState.selectedTab == IncidentTabType.NAAYOS
    val visibleIncidents = if (isResolvedTab) filterHistoryRecords(
        uiState.filteredIncidents,
        { it.date },
        historyFilter,
        { it.createdAtMillis.takeIf { millis -> millis > 0 } }
    ) else uiState.filteredIncidents

    Column(Modifier.fillMaxSize().background(LightGrayBackground).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep App Logo", modifier = Modifier.size(60.dp))
            Spacer(Modifier.width(12.dp))
            Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.height(32.dp))
        Text("Insidente", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            IncidentSummaryCard(Modifier.weight(1f), uiState.openCount, "Bukas", WarningOrange, WarningOrange) { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            IncidentSummaryCard(Modifier.weight(1f), uiState.resolvedCount, "Naayos", SuccessGreen, SuccessGreen) { Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.searchQuery, onValueChange = viewModel::onSearchQueryChanged, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) }, shape = RoundedCornerShape(24.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, unfocusedBorderColor = Color.LightGray, focusedBorderColor = EnerjeepBlue)
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverTabButton(Modifier.weight(1f), "Di-Naayos", uiState.selectedTab == IncidentTabType.DI_NAAYOS) { viewModel.onTabSelected(IncidentTabType.DI_NAAYOS) }
            DriverTabButton(Modifier.weight(1f), "Naayos", isResolvedTab) { viewModel.onTabSelected(IncidentTabType.NAAYOS) }
        }
        if (isResolvedTab) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        if (uiState.errorMessage != null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(uiState.errorMessage ?: "", color = Color(0xFFB91C1C), fontSize = 14.sp)
            }
        } else if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EnerjeepBlue)
            }
        } else if (visibleIncidents.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(if (isResolvedTab) "Walang naayos na insidente sa napiling filter." else "Walang insidenteng hindi pa naayos.", color = Color.Gray, fontSize = 14.sp) }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visibleIncidents, key = { it.id }) { incident -> DriverIncidentCard(incident) { viewModel.selectIncident(incident) } }
            }
        }
    }
    uiState.selectedIncident?.let { selected ->
        IncidentDetailBottomSheet(
            selected,
            officeName = uiState.officeName,
            officePhone = uiState.officePhone
        ) { viewModel.clearSelectedIncident() }
    }
}

@Composable
fun IncidentSummaryCard(modifier: Modifier = Modifier, count: Int, label: String, iconColor: Color, borderColor: Color, icon: @Composable () -> Unit) {
    Card(modifier.height(100.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().width(4.dp).background(borderColor))
            Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = iconColor.copy(alpha = .1f), modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { icon() } }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) { Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 3.dp)) }
            }
        }
    }
}

@Composable
fun DriverTabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier.height(40.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = if (isSelected) Color.White else Color.Transparent, border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = if (isSelected) Color.Black else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) }
    }
}

@Composable
fun DriverIncidentCard(incident: DriverIncident, onViewClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(incident.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Spacer(Modifier.height(2.dp)); Text(incident.plateNumber, fontSize = 14.sp, color = Color.Gray)
                    if (incident.location.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(incident.location, fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(2.dp)); Text(incident.description, fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccessTime, null, tint = Color.Gray, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(incident.date, fontSize = 12.sp, color = Color.Gray) }
                }
                Surface(color = if (incident.isResolved) SuccessGreen.copy(alpha = .1f) else WarningOrangeLight, shape = RoundedCornerShape(8.dp)) {
                    Text(if (incident.isResolved) "Naayos" else "Bukas", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (incident.isResolved) SuccessGreen else WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onViewClick, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) { Icon(Icons.Default.Visibility, null, tint = Color.Black, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Tingnan", color = Color.Black, fontSize = 14.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailBottomSheet(
    incident: DriverIncident,
    officeName: String = "",
    officePhone: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Detalye ng Insidente", fontSize = 20.sp, fontWeight = FontWeight.Bold); IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Close") } }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(incident.title, fontSize = 16.sp, fontWeight = FontWeight.Bold); Surface(color = if (incident.isResolved) SuccessGreen.copy(alpha = .1f) else WarningOrangeLight, shape = RoundedCornerShape(8.dp)) { Text(if (incident.isResolved) "Naayos" else "Bukas", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (incident.isResolved) SuccessGreen else WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
            Spacer(Modifier.height(24.dp)); DetailRow(Icons.Default.DirectionsBus, "Plaka ng Sasakyan", incident.plateNumber)
            Spacer(Modifier.height(16.dp)); DetailRow(Icons.Default.LocationOn, "Lokasyon", incident.location)
            Spacer(Modifier.height(16.dp)); DetailRow(Icons.Default.Description, "Deskripsyon", incident.description)
            Spacer(Modifier.height(16.dp)); DetailRow(Icons.Default.CalendarToday, "Petsa at Oras", incident.date)
            Spacer(Modifier.height(16.dp)); DetailRow(Icons.Default.AccountBalanceWallet, "Multa", "₱${String.format("%,.0f", incident.multiya)}")
            Spacer(Modifier.height(16.dp)); DetailRow(Icons.Default.Description, "Naipadala sa Driver", if (incident.isSentToDriver) "Oo" else "Hindi")
            /*
             * Fase 6: tel: call button - tawagan ang opisina tungkol
             * sa insidente. Naka-DIAL (hindi auto-call) para makita
             * muna ng driver ang numero bago tumawag.
             */
            if (officePhone.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val clean = officePhone.filter {
                            it.isDigit() || it == '+'
                        }
                        if (clean.isNotBlank()) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:$clean")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen
                    )
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tawagan ang Opisina" +
                                if (officeName.isNotBlank()) {
                                    " ($officeName)"
                                } else {
                                    ""
                                },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(32.dp)); OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(24.dp)) { Text("Isara", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    /*
     * Mas malinis na layout: naka-STACK (label sa itaas, value sa ibaba)
     * kaya hindi na nagpipisikan ang mahahabang text (hal. Deskripsyon).
     */
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}
