package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp



@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    iconBackground: Color
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBackground, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(24.dp)) {
                    icon()
                }
            }
            
            Column {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: @Composable () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp)) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun BusIconCustom(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Bus body
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.15f, height * 0.25f),
            size = Size(width * 0.7f, height * 0.5f),
            cornerRadius = CornerRadius(width * 0.05f),
            style = Stroke(width = width * 0.05f)
        )

        // Windshield
        drawRect(
            color = color,
            topLeft = Offset(width * 0.25f, height * 0.35f),
            size = Size(width * 0.5f, height * 0.15f),
            style = Stroke(width = width * 0.05f)
        )

        // Headlights
        drawCircle(
            color = color,
            radius = width * 0.05f,
            center = Offset(width * 0.3f, height * 0.65f)
        )
        drawCircle(
            color = color,
            radius = width * 0.05f,
            center = Offset(width * 0.7f, height * 0.65f)
        )
    }
}

@Composable
fun SalesIcon(modifier: Modifier = Modifier, color: Color = Color.Blue) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        /*
         * The peso sign of the Philippines: a vertical stem with a bowl on
         * the right at the top, and two horizontal bars crossing the stem.
         * The old drawing was a dollar sign, which is not the money of this
         * app.
         */
        val stemWidth = width * 0.12f
        val stemX = width * 0.38f

        // Vertical stem.
        drawLine(
            color = color,
            start = Offset(stemX, height * 0.08f),
            end = Offset(stemX, height * 0.88f),
            strokeWidth = stemWidth
        )

        // The bowl of the "P", on the right of the stem.
        drawPath(
            path = Path().apply {
                moveTo(stemX, height * 0.16f)
                cubicTo(
                    width * 0.82f, height * 0.16f,
                    width * 0.82f, height * 0.44f,
                    stemX, height * 0.44f
                )
            },
            color = color,
            style = Stroke(width = stemWidth)
        )

        // The two bars that cross the stem.
        drawLine(
            color = color,
            start = Offset(width * 0.18f, height * 0.44f),
            end = Offset(width * 0.62f, height * 0.44f),
            strokeWidth = stemWidth
        )
        drawLine(
            color = color,
            start = Offset(width * 0.18f, height * 0.58f),
            end = Offset(width * 0.62f, height * 0.58f),
            strokeWidth = stemWidth
        )
    }
}

@Composable
fun WarningIcon(modifier: Modifier = Modifier, color: Color = Color.Red) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        drawPath(
            path = Path().apply {
                moveTo(width * 0.5f, height * 0.1f)
                lineTo(width * 0.1f, height * 0.85f)
                lineTo(width * 0.9f, height * 0.85f)
                close()
            },
            color = color,
            style = Stroke(width = width * 0.08f)
        )
        
        drawRect(
            color = color,
            topLeft = Offset(width * 0.46f, height * 0.35f),
            size = Size(width * 0.08f, height * 0.25f)
        )
        
        drawCircle(
            color = color,
            radius = width * 0.04f,
            center = Offset(width * 0.5f, height * 0.72f)
        )
    }
}

@Composable
fun TruckIcon(modifier: Modifier = Modifier, color: Color = Color.Green) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Truck body
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.1f, height * 0.3f),
            size = Size(width * 0.5f, height * 0.4f),
            cornerRadius = CornerRadius(width * 0.05f),
            style = Stroke(width = width * 0.05f)
        )
        
        // Cabin
        drawPath(
            path = Path().apply {
                moveTo(width * 0.6f, height * 0.4f)
                lineTo(width * 0.85f, height * 0.4f)
                lineTo(width * 0.85f, height * 0.7f)
                lineTo(width * 0.6f, height * 0.7f)
                close()
            },
            color = color,
            style = Stroke(width = width * 0.05f)
        )
        
        // Wheels
        drawCircle(color = color, radius = width * 0.08f, center = Offset(width * 0.25f, height * 0.75f))
        drawCircle(color = color, radius = width * 0.08f, center = Offset(width * 0.72f, height * 0.75f))
    }
}

@Composable
fun DocumentIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.2f, height * 0.1f),
            size = Size(width * 0.6f, height * 0.8f),
            cornerRadius = CornerRadius(width * 0.05f),
            style = Stroke(width = width * 0.05f)
        )
        
        // Lines
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.3f), end = Offset(width * 0.65f, height * 0.3f), strokeWidth = width * 0.05f)
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.5f), end = Offset(width * 0.65f, height * 0.5f), strokeWidth = width * 0.05f)
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.7f), end = Offset(width * 0.65f, height * 0.7f), strokeWidth = width * 0.05f)
    }
}

@Composable
fun ListIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Dots
        drawCircle(color = color, radius = width * 0.05f, center = Offset(width * 0.2f, height * 0.3f))
        drawCircle(color = color, radius = width * 0.05f, center = Offset(width * 0.2f, height * 0.5f))
        drawCircle(color = color, radius = width * 0.05f, center = Offset(width * 0.2f, height * 0.7f))
        
        // Lines
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.3f), end = Offset(width * 0.8f, height * 0.3f), strokeWidth = width * 0.05f)
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.5f), end = Offset(width * 0.8f, height * 0.5f), strokeWidth = width * 0.05f)
        drawLine(color = color, start = Offset(width * 0.35f, height * 0.7f), end = Offset(width * 0.8f, height * 0.7f), strokeWidth = width * 0.05f)
    }
}

@Composable
fun SirenIcon(modifier: Modifier = Modifier, color: Color = Color.Red) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(width * 0.2f, height * 0.3f),
            size = Size(width * 0.6f, height * 0.4f)
        )
        
        drawRoundRect(
            color = color,
            topLeft = Offset(width * 0.15f, height * 0.65f),
            size = Size(width * 0.7f, height * 0.15f),
            cornerRadius = CornerRadius(width * 0.05f)
        )
    }
}
