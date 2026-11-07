package com.mobile.enerjeep.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.TextSecondary

/**
 * Shared edit-profile form used by every account role.
 * The driver layout is the source of truth so Admin and Superadmin forms
 * keep the same spacing, field labels, card, avatar, and action buttons.
 */
@Composable
fun SharedEditProfileForm(
    initials: String,
    emergencyContactName: String,
    emergencyContactNumber: String,
    isSubmitting: Boolean,
    onEmergencyContactNameChange: (String) -> Unit,
    onEmergencyContactNumberChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightGrayBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = !isSubmitting,
                    role = Role.Button,
                    onClick = onCancel
                )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Bumalik sa profile",
                tint = Color.Black
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "I-edit ang Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = EnerjeepBlue
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Emergency Contact Name",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = emergencyContactName,
                    onValueChange = { onEmergencyContactNameChange(EmergencyContact.sanitizeName(it)) },
                    placeholder = { Text("Pangalan ng contact") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Emergency Contact Number",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = emergencyContactNumber,
                    onValueChange = { onEmergencyContactNumberChange(EmergencyContact.sanitizeNumber(it)) },
                    placeholder = { Text("09XXXXXXXXX") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text("Kanselahin", color = Color.Black)
                    }

                    Button(
                        onClick = onSave,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("I-save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared password/security form used by every account role.
 */
@Composable
fun SharedSecurityForm(
    oldPassword: String,
    newPassword: String,
    confirmPassword: String,
    isSubmitting: Boolean,
    onOldPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightGrayBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = !isSubmitting,
                    role = Role.Button,
                    onClick = onCancel
                )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Bumalik sa profile",
                tint = Color.Black
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Seguridad",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Spacer(Modifier.height(20.dp))

                SharedPasswordField(
                    label = "Lumang Password",
                    value = oldPassword,
                    enabled = !isSubmitting,
                    onValueChange = onOldPasswordChange
                )

                Spacer(Modifier.height(16.dp))

                SharedPasswordField(
                    label = "Bagong Password",
                    value = newPassword,
                    enabled = !isSubmitting,
                    onValueChange = onNewPasswordChange
                )

                Spacer(Modifier.height(16.dp))

                SharedPasswordField(
                    label = "Kumpirmahin",
                    value = confirmPassword,
                    enabled = !isSubmitting,
                    onValueChange = onConfirmPasswordChange
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, EnerjeepBlue.copy(alpha = 0.35f))
                    ) {
                        Text("Kanselahin", color = Color.Black)
                    }

                    Button(
                        onClick = onSave,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("I-save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedPasswordField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Text(
        text = label,
        fontSize = 12.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(4.dp))

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled
            ) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = if (passwordVisible) {
                        "Itago ang password"
                    } else {
                        "Ipakita ang password"
                    },
                    tint = TextSecondary
                )
            }
        }
    )
}
