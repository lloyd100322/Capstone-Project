package com.mobile.enerjeep.ui.incident

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.PesoIcon
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.ledger.LedgerViewModel
import com.mobile.enerjeep.ui.ledger.TerminalFeeFormContent
import com.mobile.enerjeep.ui.theme.*
import com.mobile.enerjeep.ui.theme.TextSecondary
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentScreen(onReportClick: () -> Unit, viewModel: IncidentViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    /* Ang "Talaan" tab ay tinanggal: dito na sa Insidente ang pag-record
     * ng bayad ng multa ("may incident -> nagbayad"). */
    val ledgerViewModel: LedgerViewModel = hiltViewModel()
    val ledgerDrivers by ledgerViewModel.driverOptions.collectAsStateWithLifecycle()
    val driverPhones by viewModel.driverPhones.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    var selectedIncident by remember { mutableStateOf<IncidentItem?>(null) }
    var showMultaModal by remember { mutableStateOf(false) }
    var multaPrefillPlate by remember { mutableStateOf("") }
    var multaPrefillAmount by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(containerColor = LightGrayBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep Logo", modifier = Modifier.size(52.dp)); Spacer(Modifier.width(12.dp)); Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Insidente", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            multaPrefillPlate = ""
                            multaPrefillAmount = ""
                            showMultaModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                        shape = RoundedCornerShape(22.dp)
                    ) { Text("Multa", fontSize = 12.sp, color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onReportClick, colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(22.dp)) { Text("I-report", maxLines = 1) }
                }
            }
            Spacer(Modifier.height(24.dp))
            when (val state = uiState) {
                IncidentUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EnerjeepBlue) }
                is IncidentUiState.Error -> Text(state.message, color = Color.Red, modifier = Modifier.padding(16.dp))
                is IncidentUiState.Success -> {
                    Row(Modifier.fillMaxWidth()) {
                        IncidentStatCard(Modifier.weight(1f), "Di-naayos", state.openCount.toString()) { Icon(Icons.Default.Warning, null, tint = WarningOrange) }
                        Spacer(Modifier.width(16.dp))
                        IncidentStatCard(Modifier.weight(1f), "Naayos", state.resolvedCount.toString()) { Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen) }
                    }
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp)) {
                        IncidentTabButton(Modifier.weight(1f), "Di-Naayos", selectedTab == 0) { selectedTab = 0 }
                        IncidentTabButton(Modifier.weight(1f), "Naayos", selectedTab == 1) { selectedTab = 1 }
                    }
                    if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                    Spacer(Modifier.height(24.dp))
                    if (selectedTab == 0) {
                        val incidents = state.activeIncidents.filter { it.title.contains(searchQuery, true) || it.plateNumber.contains(searchQuery, true) || it.location.contains(searchQuery, true) }
                        if (incidents.isEmpty()) Text("Walang insidenteng hindi pa naayos.", color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(48.dp))
                        else incidents.forEach { incident ->
                            IncidentItemCard(incident, onViewClick = { selectedIncident = incident }, onMarkResolvedClick = { viewModel.resolveIncident(incident.id) })
                            Spacer(Modifier.height(12.dp))
                        }
                    } else {
                        /*
                         * "Naayos" shows only the resolved incidents.
                         * The creation timestamp is passed so a record
                         * that was made today also shows under the
                         * "Ngayon" filter of this tab.
                         */
                        val searched = state.historyIncidents.filter { it.title.contains(searchQuery, true) || it.plateNumber.contains(searchQuery, true) || it.location.contains(searchQuery, true) }
                        val incidents = filterHistoryRecords(
                            searched,
                            { it.date },
                            historyFilter,
                            { it.createdAtMillis.takeIf { millis -> millis > 0 } }
                        )
                        if (incidents.isEmpty()) Text("Walang naayos na insidente sa napiling filter.", color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(48.dp))
                        else incidents.forEach { incident -> HistoryIncidentItemCard(incident) { selectedIncident = incident }; Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
    selectedIncident?.let { incident -> ModalBottomSheet(onDismissRequest = { selectedIncident = null }, sheetState = sheetState, containerColor = Color.White) {
        IncidentDetailsContent(
            incident,
            onCancel = { selectedIncident = null },
            onResolve = { viewModel.resolveIncident(incident.id); selectedIncident = null },
            onSendToDriver = {
                viewModel.sendIncidentToDriver(incident.id)
                selectedIncident = null
            },
            onRecordPayment = {
                multaPrefillPlate = incident.plateNumber
                multaPrefillAmount = incident.penalty.filter { it.isDigit() }
                selectedIncident = null
                showMultaModal = true
            },
            onCallDriver = driverPhones[incident.plateNumber]
                ?.takeIf { it.isNotBlank() }
                ?.let { phone ->
                    {
                        val clean = phone.filter {
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
                    }
                }
        )
    } }

    /* Ang dating "+ Idagdag" ng Talaan: multa payment form. */
    if (showMultaModal) {
        ModalBottomSheet(
            onDismissRequest = { showMultaModal = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            TerminalFeeFormContent(
                drivers = ledgerDrivers,
                initialAmount = multaPrefillAmount.ifBlank { "50" },
                initialPlate = multaPrefillPlate,
                onCancel = { showMultaModal = false },
                onSubmit = { driver, amount, feeType ->
                    ledgerViewModel.addCollection(
                        driver.plateNumber,
                        driver.driverName,
                        amount,
                        feeType
                    )
                    showMultaModal = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentReportFormScreen(onBack: () -> Unit, viewModel: IncidentViewModel = hiltViewModel()) {
    val driverOptions by viewModel.driverOptions.collectAsStateWithLifecycle()
    var expandedDriver by remember { mutableStateOf(false) }
    var selectedDriver by remember(driverOptions) { mutableStateOf(driverOptions.firstOrNull()) }
    var incidentType by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var penalty by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var savedSentToDriver by remember { mutableStateOf(true) }
    var showSendPrompt by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Mag-report ng Insidente", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp)); Text("Plaka", fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedDriver, onExpandedChange = { if (!submitted && driverOptions.isNotEmpty()) expandedDriver = !expandedDriver }) {
            OutlinedTextField(value = selectedDriver?.let { "${it.plateNumber} — ${it.driverName}" } ?: "Walang dispatched na jeep", onValueChange = {}, readOnly = true, enabled = !submitted && driverOptions.isNotEmpty(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDriver) }, modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = expandedDriver, onDismissRequest = { expandedDriver = false }) { driverOptions.forEach { driver -> DropdownMenuItem(text = { Text("${driver.plateNumber} — ${driver.driverName}") }, onClick = { selectedDriver = driver; expandedDriver = false }) } }
        }
        if (driverOptions.isEmpty()) {
            Text(
                "Walang jeep na may dispatch record. Dispatch muna ang sasakyan bago mag-report ng insidente.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (driverOptions.isEmpty()) Text("Walang driver account na may plaka.", color = Color.Red, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        IncidentFormTextField("Uri ng Insidente", incidentType, { if (!submitted) incidentType = it }, "Ilagay ang uri ng insidente")
        Spacer(Modifier.height(16.dp)); IncidentFormTextField("Lokasyon", location, { if (!submitted) location = it }, "Ilagay ang lokasyon")
        Spacer(Modifier.height(16.dp)); IncidentFormTextField("Multa", penalty, { if (!submitted && (it.isEmpty() || it.all(Char::isDigit))) penalty = it }, "0", leadingIcon = { PesoIcon(size = 18.dp, color = TextSecondary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(Modifier.height(16.dp)); Text("Paglalarawan", fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
        OutlinedTextField(description, { if (!submitted) description = it }, modifier = Modifier.fillMaxWidth().height(120.dp), placeholder = { Text("Ilarawan ang nangyari...") })
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp)) { Text("Kanselahin") }; Spacer(Modifier.width(16.dp))
            Button(onClick = { if (!submitted && selectedDriver != null) { showSendPrompt = true } else if (submitted) onBack() }, enabled = selectedDriver != null && driverOptions.isNotEmpty(), modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text(if (submitted) "Tapos" else "Ipasa") }
        }
        if (submitted) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (savedSentToDriver) {
                    "Na-save at naipadala na sa mga driver ang report. Maaari mo nang isara ang form."
                } else {
                    "Na-save ang report nang hindi ipinapadala. Makikita mo ito sa Detalye kung nais mo itong ipadala."
                },
                color = SuccessGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }

    /*
     * Hindi "ipasa" agad - nagtatanong muna kung ipapadala ba sa mga
     * driver ang insidente o i-save lamang muna (admin-only draft).
     */
    if (showSendPrompt) {
        AlertDialog(
            onDismissRequest = { showSendPrompt = false },
            title = { Text("Ipadala sa mga driver ang insidente?") },
            text = {
                Text(
                    "Ipapakita ang insidente sa app ng mga driver. " +
                            "Pwede mo rin itong i-save muna nang hindi " +
                            "ipinapadala."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSendPrompt = false
                        selectedDriver?.let {
                            viewModel.reportIncident(
                                it.plateNumber,
                                incidentType,
                                location,
                                penalty,
                                description,
                                sendToDriver = true
                            )
                            savedSentToDriver = true
                            submitted = true
                        }
                    }
                ) {
                    Text("Ipadala", color = EnerjeepBlue, maxLines = 1)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showSendPrompt = false
                            selectedDriver?.let {
                                viewModel.reportIncident(
                                    it.plateNumber,
                                    incidentType,
                                    location,
                                    penalty,
                                    description,
                                    sendToDriver = false
                                )
                                savedSentToDriver = false
                                submitted = true
                            }
                        }
                    ) {
                        Text("I-save", maxLines = 1)
                    }
                    TextButton(
                        onClick = { showSendPrompt = false }
                    ) {
                        Text("Kanselahin", color = TextSecondary)
                    }
                }
            }
        )
    }
}

@Composable
fun IncidentStatCard(modifier: Modifier = Modifier, title: String, value: String, icon: @Composable () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { icon(); Spacer(Modifier.height(12.dp)); Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(title, fontSize = 12.sp, color = TextSecondary) } }
}

@Composable
fun IncidentItemCard(incident: IncidentItem, onViewClick: () -> Unit, onMarkResolvedClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(incident.title, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(incident.plateNumber, color = TextSecondary); Text(incident.location, color = TextSecondary); Text(incident.date, color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp)); StatusBadge(incident.status, WarningOrangeLight, WarningOrange); Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) { Button(onClick = onMarkResolvedClick, modifier = Modifier.weight(1f)) { Text("Ayusin", maxLines = 1) }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onViewClick, modifier = Modifier.weight(1f)) { Text("Tingnan", maxLines = 1) } }
        }
    }
}

@Composable
fun HistoryIncidentItemCard(incident: IncidentItem, onViewClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(incident.title, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(incident.plateNumber, color = TextSecondary); Text(incident.date, color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            // The "Naayos" tab only holds resolved incidents, but the
            // badge still follows the real status of the record.
            val isResolved = incident.status == "Naayos"
            StatusBadge(incident.status, if (isResolved) SuccessGreenLight else WarningOrangeLight, if (isResolved) SuccessGreen else WarningOrange)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onViewClick, modifier = Modifier.fillMaxWidth()) { Text("Tingnan") }
        }
    }
}

@Composable
fun IncidentDetailsContent(
    incident: IncidentItem,
    onCancel: () -> Unit,
    onResolve: () -> Unit,
    onSendToDriver: () -> Unit = {},
    onRecordPayment: (() -> Unit)? = null,
    onCallDriver: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Detalye ng Insidente", fontSize = 20.sp, fontWeight = FontWeight.Bold); IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Close") } }
        Spacer(Modifier.height(16.dp)); Text(incident.title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp))
        IncidentInfoRow("Plaka ng Sasakyan", incident.plateNumber); IncidentInfoRow("Lokasyon", incident.location); IncidentInfoRow("Petsa", incident.date); IncidentInfoRow("Multa", incident.penalty); IncidentInfoRow("Paglalarawan", incident.description); IncidentInfoRow("Naipadala sa Driver", incident.sentToDriver)
        /*
         * Fase 6: tel: call button - tawagan ang driver ng sasakyan
         * (naka-DIAL para makita muna ang numero bago tumawag).
         */
        if (onCallDriver != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCallDriver,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    SuccessGreen
                )
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Tawagan", color = SuccessGreen, maxLines = 1)
            }
        }
        /*
         * "May incident -> nagbayad": kung may multa ang insidente,
         * maaaring i-record dito ang bayad ng driver (Talaan ay wala na).
         */
        val penaltyDigits = incident.penalty.filter { it.isDigit() }
        if (onRecordPayment != null && penaltyDigits.isNotEmpty() && (penaltyDigits.toIntOrNull() ?: 0) > 0) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRecordPayment,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                shape = RoundedCornerShape(24.dp)
            ) {
                PesoIcon(modifier = Modifier.size(16.dp), color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Bayaran", color = Color.White, maxLines = 1)
            }
        }
        if (incident.sentToDriver == "Hindi") {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSendToDriver,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
            ) { Text("Ipadala", maxLines = 1) }
        }
        Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth()) { OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Isara", maxLines = 1) }; Spacer(Modifier.width(12.dp)); Button(onClick = onResolve, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text("Ayusin", maxLines = 1) } }
    }
}

@Composable
fun IncidentInfoRow(label: String, value: String) { Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, color = TextSecondary, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Medium) } }

@Composable
fun IncidentFormTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, leadingIcon: (@Composable () -> Unit)? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    Text(label, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp)); OutlinedTextField(value, onValueChange, placeholder = { Text(placeholder) }, leadingIcon = leadingIcon, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = keyboardOptions)
}

@Composable
fun IncidentTabButton(modifier: Modifier, text: String, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp), onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) Color.White else Color.Transparent), shape = RoundedCornerShape(12.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
}

@Composable
fun StatusBadge(text: String, backgroundColor: Color, textColor: Color) {
    Surface(color = backgroundColor, shape = RoundedCornerShape(12.dp)) { Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
}
