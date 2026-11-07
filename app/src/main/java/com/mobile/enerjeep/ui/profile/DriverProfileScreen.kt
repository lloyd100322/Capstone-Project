package com.mobile.enerjeep.ui.profile

import com.mobile.enerjeep.ui.theme.responsivePageHorizontalPadding
import com.mobile.enerjeep.ui.theme.responsivePageTitleSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.ErrorRed
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.TextSecondary
import com.mobile.enerjeep.ui.theme.WarningOrangeLight


/** Initials ng driver para sa avatar (hal. "JS" para Juan Santos). */
private fun profileInitials(first: String, last: String): String =
    (first.take(1) + last.take(1)).uppercase().ifBlank { "?" }

@Composable
fun DriverProfileScreen(
    viewModel: DriverProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSavingProfile by viewModel.isSavingProfile.collectAsStateWithLifecycle()
    val isSavingPassword by viewModel.isSavingPassword.collectAsStateWithLifecycle()
    val isSigningOut by viewModel.isSigningOut.collectAsStateWithLifecycle()

    when (uiState.currentView) {

        ProfileSubView.MAIN ->
            DriverProfileMainContent(
                uiState = uiState,
                onNavigateEdit = {
                    viewModel.setView(
                        ProfileSubView.EDIT_PROFILE
                    )
                },
                onNavigateSecurity = {
                    viewModel.setView(
                        ProfileSubView.SECURITY
                    )
                },
                onSignOut = {
                    viewModel.signOut()
                },
                isSigningOut = isSigningOut
            )

        ProfileSubView.EDIT_PROFILE ->
            DriverEditProfileContent(
                uiState = uiState,
                viewModel = viewModel,
                isSubmitting = isSavingProfile,
                onCancel = {
                    viewModel.setView(
                        ProfileSubView.MAIN
                    )
                }
            )

        ProfileSubView.SECURITY ->
            DriverSecurityContent(
                uiState = uiState,
                viewModel = viewModel,
                isSubmitting = isSavingPassword,
                onCancel = {
                    viewModel.setView(
                        ProfileSubView.MAIN
                    )
                }
            )
    }
}

@Composable
fun DriverProfileMainContent(
    uiState: DriverProfileUiState,
    onNavigateEdit: () -> Unit,
    onNavigateSecurity: () -> Unit,
    onSignOut: () -> Unit,
    isSigningOut: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                LightGrayBackground
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(responsivePageHorizontalPadding())
    ) {
        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.app_icon
                ),
                contentDescription =
                    "Enerjeep App Logo",
                modifier = Modifier.size(60.dp)
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Text(
                text = "Enerjeep",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        Text(
            text = "Profile",
            fontSize = responsivePageTitleSize(),
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = EnerjeepBlue
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = profileInitials(uiState.firstName, uiState.lastName),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.width(16.dp)
                )

                Column {
                    Text(
                        text =
                            "${uiState.firstName} ${uiState.lastName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "+63 ${uiState.phone}",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Surface(
                        color = WarningOrangeLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Driver",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            ),
                            color = Color(0xFFD97706),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onNavigateEdit()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Person,
                        contentDescription = null,
                        tint = EnerjeepBlue,
                        modifier =
                            Modifier.size(24.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(16.dp)
                    )

                    Column {
                        Text(
                            text = "I-edit ang Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "I-update ang iyong impormasyon",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                    }
                }

                Icon(
                    imageVector =
                        Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onNavigateSecurity()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Lock,
                        contentDescription = null,
                        tint = EnerjeepBlue,
                        modifier =
                            Modifier.size(24.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(16.dp)
                    )

                    Column {
                        Text(
                            text = "Seguridad",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Black
                        )

                        Text(
                            text =
                                "Password at privacy",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector =
                        Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedButton(
            onClick = onSignOut,
            enabled = !isSigningOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                ErrorRed.copy(alpha = 0.5f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ErrorRed
            )
        ) {
            if (isSigningOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ErrorRed
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Nagsa-sign out...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else {
                Text(
                    text = "Mag-sign Out",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                text = "Enerjeep v1.0.0",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun DriverEditProfileContent(
    uiState: DriverProfileUiState,
    viewModel: DriverProfileViewModel,
    isSubmitting: Boolean = false,
    onCancel: () -> Unit
) {
    SharedEditProfileForm(
        initials = profileInitials(uiState.firstName, uiState.lastName),
        emergencyContactName = uiState.emergencyContactName,
        emergencyContactNumber = uiState.emergencyContactNumber,
        isSubmitting = isSubmitting,
        onEmergencyContactNameChange = viewModel::updateEmergencyContactName,
        onEmergencyContactNumberChange = viewModel::updateEmergencyContactNumber,
        onCancel = onCancel,
        onSave = viewModel::saveProfile
    )
}

@Composable
fun DriverSecurityContent(
    uiState: DriverProfileUiState,
    viewModel: DriverProfileViewModel,
    isSubmitting: Boolean = false,
    onCancel: () -> Unit
) {
    SharedSecurityForm(
        oldPassword = uiState.oldPassword,
        newPassword = uiState.newPassword,
        confirmPassword = uiState.confirmPassword,
        isSubmitting = isSubmitting,
        onOldPasswordChange = viewModel::updateOldPassword,
        onNewPasswordChange = viewModel::updateNewPassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onCancel = onCancel,
        onSave = viewModel::savePassword
    )
}
