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
import com.mobile.enerjeep.ui.theme.*

/*
 * The SUPERADMIN toolbox screens. Only the superadmin can open these
 * (the caller checks the user level): the emergency list of "available
 * admin" contacts, the accountability log and the app settings.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyListView(viewModel: SuperadminViewModel) {
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val adminAccounts by viewModel.adminAccounts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedAdmin by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }
    var manualName by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Emergency List",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Ang mga admin dito ang makikita ng mga driver sa Emergency " +
                    "button. Idagdag lang ang mga \"available\" na tatawagan.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(16.dp))

        // Idagdag mula sa mga admin account
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Magdagdag ng Admin",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))

                if (adminAccounts.isEmpty()) {
                    Text(
                        "Walang admin account.",
                        fontSize = 12.sp,
                        color = Color.Gray
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

                    Spacer(Modifier.height(8.dp))

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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Idagdag", maxLines = 1)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "O maglagay ng ibang numero:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("Pangalan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.addEmergencyContact(
                            manualName, manualNumber
                        )
                        manualName = ""
                        manualNumber = ""
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Idagdag") }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = SuccessGreen)
        }

        Spacer(Modifier.height(16.dp))

        Text("Kasalukuyang Listahan", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (contacts.isEmpty()) {
            Text("Walang laman ang listahan.", color = Color.Gray)
        } else {
            contacts.forEach { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
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
                                color = Color.Gray
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

@Composable
fun ActivityLogView(viewModel: SuperadminViewModel) {
    val logs by viewModel.activityLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Talaan ng Aktibidad",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Sino ang gumawa ng bawat aksyon at kailan - para sa " +
                    "accountability. Hindi ito pwedeng baguhin o burahin.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Text("Wala pang naitatalang aktibidad.", color = Color.Gray)
        } else {
            logs.forEach { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
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
                                color = Color.Gray
                            )
                        }
                        if (log.detail.isNotBlank()) {
                            Text(
                                log.detail,
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
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(viewModel: SuperadminViewModel) {
    val cutoff by viewModel.cutoffHour.collectAsStateWithLifecycle()
    val tiers by viewModel.tiers.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Cut-off ng reset ng pila at ang bawas kada upuan ng jeep.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cut-off ng Pila", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Oras na nagre-reset ang pila (24-hour). Hal: 23 = 11 PM.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cutoff,
                    onValueChange = { viewModel.updateCutoffHour(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Bawas kada Upuan (Seater)",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kapag nag-dispatch, ang bawas ay awtomatiko mula sa " +
                            "bilang ng upuan ng jeep.",
                    fontSize = 11.sp,
                    color = Color.Gray
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
                    shape = RoundedCornerShape(12.dp)
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
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("I-save", maxLines = 1) }
    }
}
