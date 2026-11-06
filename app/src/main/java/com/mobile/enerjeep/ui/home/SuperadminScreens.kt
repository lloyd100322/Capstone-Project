package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.SearchField
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.*
import com.mobile.enerjeep.ui.theme.TextSecondary

/*
 * Superadmin-only utility screens: emergency contacts and app settings.
 * ActivityLogView is retained as a reusable report component and is now
 * presented inside the Reports tab instead of the account-management area.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyListView(viewModel: SuperadminViewModel) {
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val adminAccounts by viewModel.adminAccounts.collectAsStateWithLifecycle()
    val contactsError by viewModel.emergencyContactsError.collectAsStateWithLifecycle()
    val adminAccountsError by viewModel.adminAccountsError.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedAdmin by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }
    var manualName by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)
    ) {
        Text(
            "Emergency List",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // Card 1: idagdag ang existing admin account sa emergency list.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Magdagdag ng Admin",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pumili ng existing admin account na isasama sa emergency list.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))

                if (adminAccountsError != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            adminAccountsError.orEmpty(),
                            fontSize = 12.sp,
                            color = ErrorRed
                        )
                        OutlinedButton(onClick = viewModel::retryListeners) {
                            Text("Subukang muli", maxLines = 1)
                        }
                    }
                } else if (adminAccounts.isEmpty()) {
                    Text(
                        "Walang admin account.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAdmin?.first ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pumili ng admin") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = expanded
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            adminAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${account.first} — ${account.second}")
                                    },
                                    onClick = {
                                        selectedAdmin = account
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            selectedAdmin?.let { (name, number) ->
                                viewModel.addEmergencyContact(name, number)
                                selectedAdmin = null
                            }
                        },
                        enabled = selectedAdmin != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnerjeepBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Idagdag ang Admin", maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Card 2: magdagdag ng emergency contact na hindi admin account.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Magdagdag ng Ibang Numero",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ilagay ang pangalan at numero ng emergency contact na hindi kabilang sa admin accounts.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("Pangalan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualNumber,
                    onValueChange = { manualNumber = it },
                    label = { Text("Numero (09XXXXXXXXX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.addEmergencyContact(
                            manualName, manualNumber
                        )
                        manualName = ""
                        manualNumber = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EnerjeepBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Idagdag ang Numero", maxLines = 1)
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = SuccessGreen)
        }

        Spacer(Modifier.height(16.dp))

        Text("Kasalukuyang Listahan", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (contactsError != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    contactsError.orEmpty(),
                    color = ErrorRed,
                    fontSize = 13.sp
                )
                Button(
                    onClick = viewModel::retryListeners,
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                ) {
                    Text("Subukang muli", maxLines = 1)
                }
            }
        } else if (contacts.isEmpty()) {
            Text("Walang laman ang listahan.", color = TextSecondary)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                contacts.forEach { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                contact.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                contact.number,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.removeEmergencyContact(contact)
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Alisin",
                                tint = ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogView(
    viewModel: SuperadminViewModel,
    embeddedInReport: Boolean = false
) {
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val logsError by viewModel.activityLogsError.collectAsStateWithLifecycle()

    /*
     * Mga filter ng talaan: uri ng aksyon at petsa (HistoryDateFilterBar -
     * "Ngayon" ang default, pareho ng ibang history list sa app).
     */
    var selectedAction by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember {
        mutableStateOf(HistoryFilterState())
    }
    var expandedAction by remember { mutableStateOf(false) }

    val actionOptions = remember(logs) {
        logs.map { it.action }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val visible = remember(
        logs, selectedAction, historyFilter, searchQuery
    ) {
        val filteredBySearch = logs.filter { log ->
            searchQuery.isBlank() ||
                log.plateNumber.contains(searchQuery, ignoreCase = true) ||
                log.driverName.contains(searchQuery, ignoreCase = true) ||
                log.detail.contains(searchQuery, ignoreCase = true)
        }
        val byAction = if (selectedAction.isBlank()) {
            filteredBySearch
        } else {
            filteredBySearch.filter { it.action == selectedAction }
        }
        filterHistoryRecords(
            byAction,
            { it.time },
            historyFilter
        ) { it.createdAtMillis }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (embeddedInReport) {
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                }
            )
            .imePadding()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 32.dp
            )
    ) {
        Text(
            "Talaan ng Aktibidad",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))
        SearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Hanapin ayon sa plaka o pangalan"
        )
        Spacer(Modifier.height(12.dp))

        /*
         * Filter ng uri ng aksyon (hal. "Settings", "Dispatch"). Mula sa
         * mga aktwal na talaan ang mga pagpipilian kaya kumpleto lagi.
         */
        ExposedDropdownMenuBox(
            expanded = expandedAction,
            onExpandedChange = { expandedAction = !expandedAction }
        ) {
            OutlinedTextField(
                value = if (selectedAction.isBlank()) {
                    "Lahat ng aksyon"
                } else {
                    selectedAction
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Aksyon") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expandedAction
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expandedAction,
                onDismissRequest = { expandedAction = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Lahat ng aksyon") },
                    onClick = {
                        selectedAction = ""
                        expandedAction = false
                    }
                )
                actionOptions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action) },
                        onClick = {
                            selectedAction = action
                            expandedAction = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        HistoryDateFilterBar(
            historyFilter,
            { historyFilter = it },
            Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (logsError != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    logsError.orEmpty(),
                    color = ErrorRed,
                    fontSize = 13.sp
                )
                Button(
                    onClick = viewModel::retryListeners,
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                ) {
                    Text("Subukang muli", maxLines = 1)
                }
            }
        } else if (logs.isEmpty()) {
            Text("Wala pang naitatalang aktibidad.", color = TextSecondary)
        } else if (visible.isEmpty()) {
            Text(
                "Walang tugmang talaan sa napiling filter.",
                color = TextSecondary
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visible.forEach { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                log.action,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                log.time,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        if (log.detail.isNotBlank()) {
                            Text(
                                log.detail,
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                        if (log.driverName.isNotBlank()) {
                            Text(
                                "Driver: ${log.driverName}",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                        Text(
                            "Ni: ${log.actorName}" +
                                    if (log.actorRole.isNotBlank()) {
                                        " (${log.actorRole})"
                                    } else {
                                        ""
                                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(viewModel: SuperadminViewModel) {
    val tiers by viewModel.tiers.collectAsStateWithLifecycle()
    val cutoffHour by viewModel.cutoffHour.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reset ng Pila", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                /*
                 * 12-hour format ang pagpili ng oras (hal. "9:00 PM")
                 * imbes na 0-23 na numero.
                 */
                var expandedCutoff by remember { mutableStateOf(false) }
                val cutoffValue = cutoffHour
                    .trim()
                    .toIntOrNull()
                    ?.coerceIn(0, 23)
                    ?: com.mobile.enerjeep.ui.queue.QueueViewModel.CUTOFF_HOUR
                ExposedDropdownMenuBox(
                    expanded = expandedCutoff,
                    onExpandedChange = { expandedCutoff = !expandedCutoff }
                ) {
                    OutlinedTextField(
                        value = com.mobile.enerjeep.ui.queue.QueueViewModel
                            .formatCutoffHour(cutoffValue),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Oras ng Reset") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expandedCutoff
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCutoff,
                        onDismissRequest = { expandedCutoff = false }
                    ) {
                        (0..23).forEach { hour ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        com.mobile.enerjeep.ui.queue.QueueViewModel
                                            .formatCutoffHour(hour)
                                    )
                                },
                                onClick = {
                                    viewModel.updateCutoffHour(hour.toString())
                                    expandedCutoff = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Bawas kada Upuan (Seater)",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                tiers.forEachIndexed { index, tier ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tier.seater,
                            onValueChange = {
                                viewModel.updateTier(index, it, tier.amount)
                            },
                            label = { Text("Upuan") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = tier.amount,
                            onValueChange = {
                                viewModel.updateTier(index, tier.seater, it)
                            },
                            label = { Text("Halaga (₱)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = { viewModel.removeTier(index) }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Alisin",
                                tint = ErrorRed
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.addTier() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Magdagdag", maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        message?.let {
            Text(it, fontSize = 12.sp, color = SuccessGreen)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.saveSettings() },
            colors = ButtonDefaults.buttonColors(
                containerColor = EnerjeepBlue
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("I-save", maxLines = 1) }
    }
}
