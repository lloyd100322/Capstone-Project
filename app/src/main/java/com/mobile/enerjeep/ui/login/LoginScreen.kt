package com.mobile.enerjeep.ui.login

import android.app.Activity
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.MainActivity
import com.mobile.enerjeep.ui.components.BusIconCustom
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.EnerjeepTheme
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.LightGrayBackground

/** 
 * Created by Bry1337 on 8/1/26.
 * Copyright (c) 2026. All rights reserved.
 */


@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
    activity: MainActivity
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

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
            Spacer(modifier = Modifier.height(40.dp))

            // App Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(EnerjeepBlue, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                BusIconCustom(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Title
            Text(
                text = "Enerjeep",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Sign In Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (uiState is LoginUiState.CodeSent) "I-verify ang Numero" else "Mag-sign In",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState !is LoginUiState.CodeSent) {
                        // Phone Number Field
                        Text(
                            text = "Numero ng Telepono",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(1.dp, FieldBorderColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+63",
                                fontSize = 16.sp,
                                color = Color.Gray
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
                                onValueChange = { phoneNumber = it },
                                placeholder = { Text("9XXXXXXXXX", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // Password Field
                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
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
                    } else {
                        // OTP Field
                        Text(
                            text = "Ilagay ang SMS Code",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            placeholder = { Text("XXXXXX", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = FieldBorderColor,
                                focusedBorderColor = EnerjeepBlue
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { viewModel.resetState() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Palitan ang numero",
                                color = EnerjeepBlue,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState is LoginUiState.Error) {
                        Text(
                            text = (uiState as LoginUiState.Error).message,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Login Button
                    Button(
                        onClick = {
                            if (uiState is LoginUiState.CodeSent) {
                                viewModel.verifyOtp(otpCode)
                            } else {
                                viewModel.sendVerificationCode("+63$phoneNumber", password, activity)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = uiState !is LoginUiState.Loading,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                    ) {
                        if (uiState is LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (uiState is LoginUiState.CodeSent) "I-verify ang Code" else "Mag-login",
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



@Composable
fun LockIconCustom(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Base
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(width * 0.2f, height * 0.45f),
            size = Size(width * 0.6f, height * 0.45f),
            cornerRadius = CornerRadius(width * 0.05f)
        )

        // Shackle
        drawPath(
            path = Path().apply {
                moveTo(width * 0.3f, height * 0.45f)
                lineTo(width * 0.3f, height * 0.25f)
                cubicTo(
                    width * 0.3f,
                    height * 0.1f,
                    width * 0.7f,
                    height * 0.1f,
                    width * 0.7f,
                    height * 0.25f
                )
                lineTo(width * 0.7f, height * 0.45f)
            },
            color = Color.Gray,
            style = Stroke(width = width * 0.1f)
        )
    }
}

@Composable
fun EyeIconCustom(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Eye shape
        drawPath(
            path = Path().apply {
                moveTo(width * 0.1f, height * 0.5f)
                quadraticTo(width * 0.5f, height * 0.1f, width * 0.9f, height * 0.5f)
                quadraticTo(width * 0.5f, height * 0.9f, width * 0.1f, height * 0.5f)
            },
            color = Color.Gray,
            style = Stroke(width = width * 0.05f)
        )

        // Pupil
        drawCircle(
            color = Color.Gray,
            radius = width * 0.15f,
            center = Offset(width * 0.5f, height * 0.5f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    val context = LocalContext.current
    EnerjeepTheme {
        LoginScreen(
            onLoginSuccess = {},
            activity = context as MainActivity
        )
    }
}
