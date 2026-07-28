package com.mobile.enerjeep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.EnerjeepTheme
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.LightGrayBackground

/** 
 * Created by Bry1337 on 7/21/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun ContentView(
    finishActivity: () -> Unit
) {
    EnerjeepTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightGrayBackground
        ) {
            LoginScreen()
        }
    }
}

@Composable
fun BusIconCustom(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Bus body
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(width * 0.15f, height * 0.25f),
            size = Size(width * 0.7f, height * 0.5f),
            cornerRadius = CornerRadius(width * 0.05f),
            style = Stroke(width = width * 0.05f)
        )
        
        // Windshield
        drawRect(
            color = Color.White,
            topLeft = Offset(width * 0.25f, height * 0.35f),
            size = Size(width * 0.5f, height * 0.15f),
            style = Stroke(width = width * 0.05f)
        )
        
        // Headlights
        drawCircle(
            color = Color.White,
            radius = width * 0.05f,
            center = Offset(width * 0.3f, height * 0.65f)
        )
        drawCircle(
            color = Color.White,
            radius = width * 0.05f,
            center = Offset(width * 0.7f, height * 0.65f)
        )
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
                cubicTo(width * 0.3f, height * 0.1f, width * 0.7f, height * 0.1f, width * 0.7f, height * 0.25f)
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

@Composable
fun LoginScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "Mag-sign In",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Phone Number Field
                Text(
                    text = "Numero ng Telepono",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                var phoneNumber by remember { mutableStateOf("") }
                
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

                var password by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Ilagay ang password", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        LockIconCustom(modifier = Modifier.size(24.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            EyeIconCustom(modifier = Modifier.size(24.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

                // Forgot Password
                TextButton(
                    onClick = { /* TODO */ },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Nakalimutan ang Password?",
                        color = EnerjeepBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Login Button
                Button(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
                ) {
                    Text(
                        text = "Mag-login",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    EnerjeepTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightGrayBackground
        ) {
            LoginScreen()
        }
    }
}
