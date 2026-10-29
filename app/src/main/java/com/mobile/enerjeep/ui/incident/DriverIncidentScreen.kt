package com.mobile.enerjeep.ui.incident

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.TextSecondary
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

    Column(Modifier.fillMaxSize().background(LightGrayBackground).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
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
            IncidentSummaryCard(Modifier.weight(1f), uiState.openCount, "Di-naayos", WarningOrange, WarningOrange) { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            IncidentSummaryCard(Modifier.weight(1f), uiState.resolvedCount, "Naayos", SuccessGreen, SuccessGreen) { Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.searchQuery, onValueChange = viewModel::onSearchQueryChanged, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon", color = TextSecondary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) }, shape = RoundedCornerShape(24.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue)
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriverTabButton(Modifier.weight(1f), "Di-naayos", uiState.selectedTab == IncidentTabType.DI_NAAYOS) { viewModel.onTabSelected(IncidentTabType.DI_NAAYOS) }
            DriverTabButton(Modifier.weight(1f), "Naayos", isResolvedTab) { viewModel.onTabSelected(IncidentTabType.NAAYOS) }
        }
        if (isResolvedTab) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        if (uiState.errorMessage != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(uiState.errorMessage ?: "", color = Color(0xFFB91C1C), fontSize = 14.sp)
            }
        } else if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EnerjeepBlue)
            }
        } else if (visibleIncidents.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text(if (isResolvedTab) "Walang naayos na insidente sa napiling filter." else "Walang insidenteng hindi pa naayos.", color = TextSecondary, fontSize = 14.sp) }
        } else {
            /*
             * Buong page ang scrollable (hindi lang ang listahan) kaya
             * hindi nacucut ang mga card sa ilalim ng navigation bar.
             */
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                visibleIncidents.forEach { incident ->
                    DriverIncidentCard(
                        incident = incident,
                        fallbackPhone = uiState.officePhone
                    )
                }
            }
        }
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
                Row(verticalAlignment = Alignment.Bottom) { Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 3.dp)) }
            }
        }
    }
}

@Composable
fun DriverTabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier.height(40.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = if (isSelected) Color.White else Color.Transparent, border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = if (isSelected) Color.Black else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) }
    }
}

@Composable
fun DriverIncidentCard(
    incident: DriverIncident,
    fallbackPhone: String = ""
) {
    val context = LocalContext.current
    /*
     * Ang buong detalye ay nasa mismong card na (walang hiwalay na
     * sheet) kaya hindi na rin ito clickable. Ang button ay CALL lang
     * (tawag sa numero ng driver; opisina kung walang numero).
     */
    val callNumber = incident.driverPhone.ifBlank { fallbackPhone }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(incident.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Spacer(Modifier.height(2.dp)); Text(incident.plateNumber, fontSize = 14.sp, color = TextSecondary)
                    if (incident.location.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(incident.location, fontSize = 14.sp, color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(2.dp)); Text(incident.description, fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccessTime, null, tint = TextSecondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(incident.date, fontSize = 12.sp, color = TextSecondary) }
                }
                Surface(color = if (incident.isResolved) SuccessGreen.copy(alpha = .1f) else WarningOrangeLight, shape = RoundedCornerShape(8.dp)) {
                    Text(if (incident.isResolved) "Naayos" else "Di-naayos", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (incident.isResolved) SuccessGreen else WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (callNumber.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                /*
                 * Tawag: naka-DIAL (hindi auto-call) para makita muna
                 * ang numero bago tumawag.
                 */
                OutlinedButton(
                    onClick = {
                        val clean = callNumber.filter {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                ) {
                    Icon(Icons.Default.Call, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tawag", color = SuccessGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}
