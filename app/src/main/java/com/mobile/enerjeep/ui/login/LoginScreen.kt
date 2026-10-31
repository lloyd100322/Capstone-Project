
package com.mobile.enerjeep.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.MainActivity
import com.mobile.enerjeep.R
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    appNavigator: AppNavigator,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
    activity: MainActivity
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var phoneNumber by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    var otpCode by remember {
        mutableStateOf("")
    }

    var resendCooldownSeconds by remember {
        mutableIntStateOf(0)
    }

    /*
     * Keep the OTP screen visible during verification,
     * resend requests, and OTP-related errors.
     */
    val isOtpStage = when (uiState) {
        LoginUiState.CodeSent,
        LoginUiState.ResendingOtp,
        LoginUiState.VerifyingOtp -> true

        is LoginUiState.OtpError -> true

        else -> false
    }

    /*
     * Handle successful login and start the resend countdown
     * after Firebase confirms that the OTP was sent.
     */
    LaunchedEffect(uiState) {
        when (uiState) {
            LoginUiState.CodeSent -> {
                resendCooldownSeconds = 30
            }

            LoginUiState.Success -> {
                onLoginSuccess()
                viewModel.resetState()
            }

            else -> Unit
        }
    }

    /*
     * Update the resend countdown without blocking the UI.
     */
    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1000L)
            resendCooldownSeconds -= 1
        }
    }

    val errorMessage = when (val state = uiState) {
        is LoginUiState.Error -> state.message
        is LoginUiState.OtpError -> state.message
        is LoginUiState.CachedPasswordMismatch -> state.message
        else -> null
    }

    val isBusy =
        uiState is LoginUiState.Loading ||
                uiState is LoginUiState.ResendingOtp ||
                uiState is LoginUiState.VerifyingOtp

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightGrayBackground
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

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            Image(
                painter = painterResource(
                    id = R.drawable.app_icon
                ),
                contentDescription = "Enerjeep App Logo",
                modifier = Modifier.size(60.dp)
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Enerjeep",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {

                    Text(
                        text = if (isOtpStage) {
                            "I-verify ang Numero"
                        } else {
                            "Mag-sign In"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    if (!isOtpStage) {

                        // PHONE NUMBER

                        Text(
                            text = "Numero ng Telepono",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        val phoneInteraction = remember {
                            MutableInteractionSource()
                        }
                        val phoneFocused by phoneInteraction
                            .collectIsFocusedAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(
                                    1.dp,
                                    if (phoneFocused) {
                                        EnerjeepBlue
                                    } else {
                                        FieldBorderColor
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "+63",
                                fontSize = 16.sp,
                                color = TextSecondary
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(FieldBorderColor)
                            )

                            TextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    phoneNumber = input
                                        .filter { it.isDigit() }
                                        .take(10)
                                },
                                placeholder = {
                                    Text(
                                        "9XXXXXXXXX",
                                        color = TextSecondary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                interactionSource = phoneInteraction,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone
                                ),
                                singleLine = true,
                                enabled = !isBusy
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(20.dp)
                        )

                        // PASSWORD

                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        passwordVisible = !passwordVisible
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Default.Visibility
                                        } else {
                                            Icons.Default.VisibilityOff
                                        },
                                        contentDescription = if (passwordVisible) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        }
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            singleLine = true,
                            enabled = !isBusy,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = FieldBorderColor,
                                focusedBorderColor = EnerjeepBlue
                            )
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "Nakalimutan ang Password?",
                            color = EnerjeepBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(
                                enabled = !isBusy
                            ) {
                                appNavigator.navigateTo(
                                    Screen.ResetPassword
                                )
                            }
                        )

                    } else {

                        // OTP VERIFICATION

                        Text(
                            text = "Ilagay ang SMS Code",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { input ->
                                otpCode = input
                                    .filter { it.isDigit() }
                                    .take(6)
                            },
                            placeholder = {
                                Text(
                                    "XXXXXX",
                                    color = TextSecondary
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = !isBusy,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = FieldBorderColor,
                                focusedBorderColor = EnerjeepBlue
                            )
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        TextButton(
                            onClick = {
                                viewModel.resendVerificationCode(activity)
                            },
                            enabled =
                                resendCooldownSeconds == 0 &&
                                        !isBusy,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (
                                    uiState is LoginUiState.ResendingOtp
                                ) {
                                    "Nagpapadala..."
                                } else {
                                    "Ipadala"
                                },
                                maxLines = 1,
                                color = EnerjeepBlue,
                                fontSize = 14.sp
                            )
                        }

                        if (resendCooldownSeconds > 0) {
                            Text(
                                text = "Maaaring magpadala muli pagkalipas ng " +
                                        "${resendCooldownSeconds}s",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    // ERROR MESSAGE

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(
                                bottom = 16.dp
                            )
                        )
                    }

                    if (uiState is LoginUiState.CachedPasswordMismatch) {
                        TextButton(
                            onClick = {
                                viewModel.continueWithOtpAfterCacheMismatch(activity)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Pinalitan ba ang password sa ibang device? Ituloy sa OTP",
                                color = EnerjeepBlue,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // LOGIN / VERIFY BUTTON

                    Button(
                        onClick = {
                            if (isOtpStage) {
                                viewModel.verifyOtp(otpCode)
                            } else {
                                viewModel.sendVerificationCode(
                                    "+63$phoneNumber",
                                    password,
                                    activity
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnerjeepBlue
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isOtpStage) {
                                    "I-verify"
                                } else {
                                    "Mag-login"
                                },
                                maxLines = 1,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
