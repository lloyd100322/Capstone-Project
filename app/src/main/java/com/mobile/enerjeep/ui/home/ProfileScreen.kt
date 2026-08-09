package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobile.enerjeep.ui.theme.EnerjeepBlue

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Logout Button
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)
        ) {
            Text(
                text = "Mag-logout",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
