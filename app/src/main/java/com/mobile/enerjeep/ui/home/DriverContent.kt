package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.ui.components.*
import com.mobile.enerjeep.ui.theme.*

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun DriverContent(
    firstName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EnerjeepBlue, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                BusIconCustom(modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Enerjeep",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Greeting
        Text(
            text = "Musta, $firstName!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "Narito ang iyong biyahe ngayong araw.",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Driver Info Card
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
                    text = "Status ng Biyahe",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Naka-pila", color = Color.Gray)
                    Text("Pang-apat (4)", fontWeight = FontWeight.Bold, color = EnerjeepBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Actions for Driver
        Text(
            text = "Mabilis na Aksyon",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(16.dp))

        QuickActionButton(
            text = "I-report ang Insidente",
            icon = { WarningIcon(modifier = Modifier.size(20.dp), color = Color.Black) },
            backgroundColor = Color.White,
            contentColor = Color.Black,
            onClick = { /* TODO */ }
        )
        Spacer(modifier = Modifier.height(12.dp))
        QuickActionButton(
            text = "Emergency",
            icon = { SirenIcon(modifier = Modifier.size(20.dp), color = ErrorRed) },
            backgroundColor = ErrorRedLight,
            contentColor = ErrorRed,
            onClick = { /* TODO */ }
        )
    }
}
