package com.mobile.enerjeep.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R

// --- LOCAL THEME COLORS ---
val LightGrayBackground = Color(0xFFF8F9FA)
val EnerjeepBlue = Color(0xFF194287)
val FieldBorderColor = Color(0xFFE5E7EB)
val ErrorRed = Color(0xFFB91C1C)

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentView by remember { mutableStateOf("Main") } // "Main", "Edit", "Security"

    Scaffold(
        containerColor = LightGrayBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EnerjeepBlue)
                    }
                }
                is ProfileUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = ErrorRed)
                    }
                }
                is ProfileUiState.Success -> {
                    when (currentView) {
                        "Main" -> ProfileMainContent(
                            profile = state.profile,
                            onEditClick = { currentView = "Edit" },
                            onSecurityClick = { currentView = "Security" },
                            onSignOutClick = onSignOut
                        )
                        "Edit" -> EditProfileContent(
                            profile = state.profile,
                            onBack = { currentView = "Main" },
                            onSave = { fName, lName, phone ->
                                viewModel.updateProfile(fName, lName, phone)
                                currentView = "Main"
                            }
                        )
                        "Security" -> SecurityContent(
                            onBack = { currentView = "Main" },
                            onSave = { old, new, confirm ->
                                val success = viewModel.updatePassword(old, new, confirm)
                                if (success) currentView = "Main"
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- 1. MAIN PROFILE VIEW ---
@Composable
fun ProfileMainContent(
    profile: ProfileData,
    onEditClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        // App Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Enerjeep Logo",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        // User Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(modifier = Modifier.size(60.dp).background(EnerjeepBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = profile.initials, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = profile.fullName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "+63 ${profile.phoneNumber}", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(color = EnerjeepBlue, shape = RoundedCornerShape(12.dp)) {
                        Text(text = profile.role, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu Items
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                // FIXED: Changed to Icons.Default.Person
                MenuRow(icon = Icons.Default.Person, title = "I-edit ang Profile", subtitle = "I-update ang iyong impormasyon", onClick = onEditClick)
                HorizontalDivider(color = FieldBorderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                // FIXED: Changed to Icons.Default.Security
                MenuRow(icon = Icons.Default.Security, title = "Seguridad", subtitle = "Password at privacy", onClick = onSecurityClick)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = onSignOutClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mag-sign Out", color = ErrorRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Enerjeep v1.0.0", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// --- 2. EDIT PROFILE VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileContent(
    profile: ProfileData,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var firstName by remember { mutableStateOf(profile.firstName) }
    var lastName by remember { mutableStateOf(profile.lastName) }
    var phone by remember { mutableStateOf(profile.phoneNumber) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // App Bar
        TopAppBar(
            title = { Text("I-edit ang Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightGrayBackground)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Editable Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(modifier = Modifier.size(100.dp).background(EnerjeepBlue, CircleShape), contentAlignment = Alignment.Center) {
                        Text(text = profile.initials, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(modifier = Modifier.size(32.dp).offset(x = 8.dp, y = 8.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
                        Box(modifier = Modifier.fillMaxSize().padding(2.dp).background(EnerjeepBlue, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Name Fields
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Pangalan", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Apelyido", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Phone Field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Telepono", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phone = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("+63", color = Color.Gray, modifier = Modifier.padding(start = 12.dp, end = 8.dp))
                                Box(modifier = Modifier.height(24.dp).width(1.dp).background(FieldBorderColor))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { onSave(firstName, lastName, phone) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(28.dp)) { Text("I-save") }
                }
            }
        }
    }
}

// --- 3. SECURITY VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityContent(
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("Seguridad", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LightGrayBackground)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Palitan ang iyong password para sa account security.", fontSize = 14.sp, color = Color.Gray, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(24.dp))

                PasswordTextField(label = "Lumang Password", value = oldPass, onValueChange = { oldPass = it })
                Spacer(modifier = Modifier.height(16.dp))
                PasswordTextField(label = "Bagong Password", value = newPass, onValueChange = { newPass = it })
                Spacer(modifier = Modifier.height(16.dp))
                PasswordTextField(label = "Kumpirmahin", value = confirmPass, onValueChange = { confirmPass = it })

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { onSave(oldPass, newPass, confirmPass) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(28.dp)) { Text("I-save") }
                }
            }
        }
    }
}

// --- REUSABLE COMPONENTS ---
@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = EnerjeepBlue, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun PasswordTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = image, contentDescription = null, tint = Color.Gray) }
            },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue)
        )
    }
}