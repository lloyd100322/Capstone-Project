package com.mobile.enerjeep.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.TextSecondary

@Composable
fun ResetPasswordScreen(
    appNavigator: AppNavigator,
    viewModel: ResetPasswordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                LightGrayBackground
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Spacer(
            modifier = Modifier.height(48.dp)
        )

        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.app_icon
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Enerjeep",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        if (uiState.step == ResetStep.SUCCESS) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 16.dp
                    ),
                color = Color(0xFFDCFCE7),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text =
                        "Tagumpay! Na-reset ang password. Mag-sign In.",
                    color = Color(0xFF166534),
                    fontSize = 14.sp,
                    modifier =
                        Modifier.padding(16.dp)
                )
            }

            Button(
                onClick = {
                    appNavigator.navigateTo(
                        Screen.Login
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        EnerjeepBlue
                )
            ) {
                Text(
                    text = "Bumalik sa Login",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

        } else {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {

                    when (uiState.step) {

                        ResetStep.PHONE_INPUT ->
                            PhoneInputContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = {
                                    appNavigator.navigateBack()
                                }
                            )

                        ResetStep.OTP_INPUT ->
                            OtpInputContent(
                                uiState = uiState,
                                viewModel = viewModel
                            )

                        ResetStep.NEW_PASSWORD ->
                            NewPasswordContent(
                                uiState = uiState,
                                viewModel = viewModel
                            )

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneInputContent(
    uiState: ResetPasswordUiState,
    viewModel: ResetPasswordViewModel,
    onBack: () -> Unit
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        modifier =
            Modifier.clickable {
                onBack()
            }
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = "Nakalimutan ang Password",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(
        modifier = Modifier.height(16.dp)
    )

    Text(
        text =
            "Ilagay ang iyong nakarehistrong numero.",
        fontSize = 14.sp,
        color = TextSecondary
    )

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                1.dp,
                FieldBorderColor,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment =
            Alignment.CenterVertically
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
                .background(
                    FieldBorderColor
                )
        )

        TextField(
            value = uiState.phoneNumber,
            onValueChange = {
                viewModel.updatePhoneNumber(
                    it
                )
            },
            placeholder = {
                Text(
                    "9171234567",
                    color = TextSecondary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor =
                    Color.Transparent,
                unfocusedContainerColor =
                    Color.Transparent,
                disabledContainerColor =
                    Color.Transparent,
                focusedIndicatorColor =
                    Color.Transparent,
                unfocusedIndicatorColor =
                    Color.Transparent
            ),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Phone
                ),
            singleLine = true
        )
    }

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    val phoneContext = LocalContext.current

    uiState.errorMessage?.let { ResetErrorText(it) }

    Button(
        enabled = !uiState.isLoading,
        onClick = {
            phoneContext.findResetActivity()?.let { viewModel.sendOtp(it) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                EnerjeepBlue
        )
    ) {
        Text(
            text = "Magpatuloy",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OtpInputContent(
    uiState: ResetPasswordUiState,
    viewModel: ResetPasswordViewModel
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        modifier =
            Modifier.clickable {
                viewModel.navigateBack()
            }
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = "I-verify ang OTP",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(
        modifier = Modifier.height(16.dp)
    )

    Text(
        text =
            "Ilagay ang 6-digit na code na ipinadala sa +63${uiState.phoneNumber}",
        fontSize = 14.sp,
        color = TextSecondary
    )

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    BasicTextField(
        value = uiState.otp,
        onValueChange = {
            viewModel.updateOtp(it)
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.NumberPassword
            ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                repeat(6) { index ->

                    val char =
                        if (
                            index >=
                            uiState.otp.length
                        ) {
                            ""
                        } else {
                            uiState
                                .otp[index]
                                .toString()
                        }

                    val isFocused =
                        index ==
                                uiState.otp.length

                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .border(
                                1.dp,
                                if (isFocused)
                                    EnerjeepBlue
                                else
                                    FieldBorderColor,
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = char,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }
        }
    )

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    val otpContext = LocalContext.current

    uiState.errorMessage?.let { ResetErrorText(it) }

    TextButton(
        enabled = !uiState.isLoading,
        onClick = {
            otpContext.findResetActivity()?.let { viewModel.resendOtp(it) }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Ipadala", maxLines = 1)
    }

    Button(
        enabled = !uiState.isLoading,
        onClick = {
            viewModel.verifyOtp()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                EnerjeepBlue
        )
    ) {
        Text(
            text = "I-verify",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NewPasswordContent(
    uiState: ResetPasswordUiState,
    viewModel: ResetPasswordViewModel
) {
    var newPassVisible by remember {
        mutableStateOf(false)
    }

    var confirmPassVisible by remember {
        mutableStateOf(false)
    }

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        modifier =
            Modifier.clickable {
                viewModel.navigateBack()
            }
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = "I-reset ang Password",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    Text(
        text = "Bagong Password",
        fontSize = 12.sp,
        color = TextSecondary
    )

    Spacer(
        modifier = Modifier.height(4.dp)
    )

    OutlinedTextField(
        value = uiState.newPassword,
        onValueChange = {
            viewModel.updateNewPassword(
                it
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor =
                    FieldBorderColor,
                focusedBorderColor =
                    EnerjeepBlue
            ),
        visualTransformation =
            if (newPassVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(
                onClick = {
                    newPassVisible =
                        !newPassVisible
                }
            ) {
                Icon(
                    imageVector =
                        if (newPassVisible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    )

    Spacer(
        modifier = Modifier.height(16.dp)
    )

    Text(
        text = "Kumpirmahin ang Password",
        fontSize = 12.sp,
        color = TextSecondary
    )

    Spacer(
        modifier = Modifier.height(4.dp)
    )

    OutlinedTextField(
        value = uiState.confirmPassword,
        onValueChange = {
            viewModel.updateConfirmPassword(
                it
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor =
                    FieldBorderColor,
                focusedBorderColor =
                    EnerjeepBlue
            ),
        visualTransformation =
            if (confirmPassVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(
                onClick = {
                    confirmPassVisible =
                        !confirmPassVisible
                }
            ) {
                Icon(
                    imageVector =
                        if (confirmPassVisible)
                            Icons.Default.Visibility
                        else
                            Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    )

    Spacer(
        modifier = Modifier.height(24.dp)
    )

    uiState.errorMessage?.let { ResetErrorText(it) }

    Button(
        enabled = !uiState.isLoading,
        onClick = {
            viewModel.completeReset()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                EnerjeepBlue
        )
    ) {
        Text(
            text = "I-reset ang Password",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ResetErrorText(message: String) {
    Text(
        text = message,
        color = Color.Red,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

private fun Context.findResetActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
