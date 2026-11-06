package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.TextSecondary

/*
 * Labeled view-only bar chart.
 *
 * Month/period labels and the plotted bars are kept in separate fixed-height
 * containers. A long month name can wrap to two lines inside its own label
 * area without changing the width or vertical alignment of the chart.
 */
@Composable
fun LabeledBarChart(
    title: String,
    bars: List<Pair<String, Double>>,
    barColor: Color = EnerjeepBlue,
    modifier: Modifier = Modifier,
    isMoney: Boolean = true
) {
    val max = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(12.dp))

        if (bars.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Text(
                    "Walang datos.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Fixed label container: full month/year names remain readable
                // and never steal width from the plotted bars.
                Card(
                    modifier = Modifier.width(108.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bars.forEach { (label, _) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(
                                        Color(0xFFF5F7FA),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Separate chart container with a fixed row height and spacing.
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bars.forEach { (_, amount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .background(
                                            Color(0xFFECEFF3),
                                            RoundedCornerShape(7.dp)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(
                                                fraction = (amount / max)
                                                    .toFloat()
                                                    .coerceIn(0.02f, 1f)
                                            )
                                            .fillMaxHeight()
                                            .background(
                                                barColor,
                                                RoundedCornerShape(7.dp)
                                            )
                                    )
                                }

                                Spacer(Modifier.width(6.dp))

                                Text(
                                    text = if (isMoney) {
                                        "₱" + NumberFormat
                                            .getNumberInstance(Locale.US)
                                            .format(amount)
                                            .replace(".00", "")
                                    } else {
                                        NumberFormat
                                            .getNumberInstance(Locale.US)
                                            .format(amount)
                                            .replace(".00", "")
                                    },
                                    modifier = Modifier.width(66.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
