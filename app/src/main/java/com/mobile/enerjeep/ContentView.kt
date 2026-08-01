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
import androidx.navigation.compose.rememberNavController
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.NavGraph
import com.mobile.enerjeep.ui.login.LoginScreen
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
    appNavigator: AppNavigator,
    finishActivity: () -> Unit
) {
    val navController = rememberNavController()
    EnerjeepTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightGrayBackground
        ) {
            NavGraph(
                navController = navController,
                appNavigator = appNavigator
            )
        }
    }
}
