package com.mobile.enerjeep.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.ui.components.PesoIcon
import java.text.NumberFormat
import java.util.Locale

val LightGrayBackground = Color(0xFFF8F9FA)
val EnerjeepBlue = Color(0xFF194287)
val SuccessGreenText = Color(0xFF16A34A)
val FieldBorderColor = Color(0xFFE5E7EB)

fun formatCurrency(amount: Double): String = "₱${NumberFormat.getNumberInstance(Locale.US).format(amount)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalFeeFormContent(
    drivers: List<LedgerDriverOption>,
    initialAmount: String = "50",
    initialPlate: String = "",
    onCancel: () -> Unit,
    onSubmit: (LedgerDriverOption, Double, String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var expandedDriver by remember { mutableStateOf(false) }
    var selectedDriver by remember(drivers, initialPlate) {
        mutableStateOf(
            drivers.firstOrNull { it.plateNumber == initialPlate }
                ?: drivers.firstOrNull()
        )
    }
    var expandedFeeType by remember { mutableStateOf(false) }
    /*
     * The terminal fee is gone: the dispatch deduction now comes from
     * the number of seats of the jeep. Only "Multa" is left to record.
     */
    val feeTypes = listOf("Multa")
    var selectedFeeType by remember { mutableStateOf(feeTypes.first()) }

    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("I-record ang Bayad ng Multa", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Plaka", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedDriver, onExpandedChange = { if (drivers.isNotEmpty()) expandedDriver = !expandedDriver }) {
            OutlinedTextField(
                value = selectedDriver?.let { "${it.plateNumber} — ${it.driverName}" } ?: "Walang rehistradong jeep",
                onValueChange = {}, readOnly = true, enabled = drivers.isNotEmpty(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDriver) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true
            )
            ExposedDropdownMenu(expanded = expandedDriver, onDismissRequest = { expandedDriver = false }) {
                drivers.forEach { driver -> DropdownMenuItem(text = { Text("${driver.plateNumber} — ${driver.driverName}") }, onClick = { selectedDriver = driver; expandedDriver = false }) }
            }
        }
        if (drivers.isEmpty()) Text("Walang driver account na may nakatalang plaka. Gumawa muna ng driver account.", color = Color.Red, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("Uri ng Bayad", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedFeeType, onExpandedChange = { expandedFeeType = !expandedFeeType }) {
            OutlinedTextField(value = selectedFeeType, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFeeType) }, modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = expandedFeeType, onDismissRequest = { expandedFeeType = false }) {
                feeTypes.forEach { type -> DropdownMenuItem(text = { Text(type) }, onClick = { selectedFeeType = type; expandedFeeType = false }) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Halaga", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) amount = it },
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp) },
            trailingIcon = {
                Column(Modifier.padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", modifier = Modifier.size(20.dp).clickable { amount = ((amount.toIntOrNull() ?: 0) + 10).toString() })
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", modifier = Modifier.size(20.dp).clickable { val current = amount.toIntOrNull() ?: 0; if (current >= 10) amount = (current - 10).toString() })
                }
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp)) { Text("Kanselahin", color = Color.Black) }
            Spacer(Modifier.width(16.dp))
            val finalAmount = amount.toDoubleOrNull() ?: 0.0
            Button(onClick = { selectedDriver?.let { if (finalAmount > 0.0) onSubmit(it, finalAmount, selectedFeeType) } }, enabled = selectedDriver != null && finalAmount > 0.0, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(28.dp)) { Text("Ipasa") }
        }
    }
}

