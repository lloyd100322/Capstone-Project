package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.ui.theme.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.Driver) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Driver Details
    var licenseNumber by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }
    val roles = UserRole.entries

    LaunchedEffect(uiState) {
        if (uiState is CreateAccountUiState.Success) {
            viewModel.goBack()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF0F2F5)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Name Fields
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pangalan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            CustomInputField(value = firstName, onValueChange = { firstName = it })
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Apelyido", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            CustomInputField(value = lastName, onValueChange = { lastName = it })
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Phone Field
                    Text("Telepono", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, FieldBorderColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+63", fontSize = 16.sp, color = Color.Gray)
                        Box(modifier = Modifier.padding(horizontal = 12.dp).height(24.dp).width(1.dp).background(FieldBorderColor))
                        TextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            placeholder = { Text("9XXXXXXXXX", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Role Dropdown
                    Text("Tungkulin", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = role.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = EnerjeepBlue,
                                focusedBorderColor = EnerjeepBlue
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roles.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption.name) },
                                    onClick = {
                                        role = selectionOption
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password Field
                    Text("Password", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = FieldBorderColor,
                            focusedBorderColor = EnerjeepBlue
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Driver Details Card (Conditional)
                    if (role == UserRole.Driver) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7F9)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Detalye ng Driver", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Driver License ID Number", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                CustomInputField(value = licenseNumber, onValueChange = { licenseNumber = it }, placeholder = "License Number")
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Plaka", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                CustomInputField(value = plateNumber, onValueChange = { plateNumber = it }, placeholder = "Plate Number")
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Emergency Contact", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                CustomInputField(value = emergencyContact, onValueChange = { emergencyContact = it }, placeholder = "Numero o pangalan")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (uiState is CreateAccountUiState.Error) {
                        Text(
                            text = (uiState as CreateAccountUiState.Error).message,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Buttons
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.goBack() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, FieldBorderColor)
                        ) {
                            Text("Kanselahin", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                viewModel.createAccount(
                                    firstName, lastName, "+63$phoneNumber", password, role,
                                    licenseNumber, plateNumber, emergencyContact
                                )
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                            enabled = uiState !is CreateAccountUiState.Loading
                        ) {
                            if (uiState is CreateAccountUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Text("Gumawa", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = ""
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.LightGray) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, FieldBorderColor, RoundedCornerShape(12.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        singleLine = true
    )
}
