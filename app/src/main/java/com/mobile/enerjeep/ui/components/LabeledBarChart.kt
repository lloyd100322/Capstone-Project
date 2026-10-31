package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.TextSecondary

/*
 * Fase 5: "labeled view-only charts" - mga simpleng bar chart na may
 * label (pangalan + halaga). Puro basa lang; walang editing.
 */
@Composable
fun LabeledBarChart(
    title: String,
    bars: List<Pair<String, Double>>,
    barColor: Color = EnerjeepBlue,
    modifier: Modifier = Modifier,
    /*
     * isMoney=false para sa mga BILANG (hal: bilang ng insidente) -
     * walang peso sign, numero lang. Default true (pera) ang Kita/Multa/
     * Donasyon charts.
     */
    isMoney: Boolean = true
) {
    val max = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (bars.isEmpty()) {
                    Text("Walang datos.", color = TextSecondary, fontSize = 13.sp)
                } else {
                    bars.forEach { (label, amount) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(88.dp),
                                maxLines = 1
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .background(
                                            Color(0xFFECEFF3),
                                            RoundedCornerShape(7.dp)
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            fraction = (amount / max)
                                                .toFloat()
                                                .coerceIn(0.02f, 1f)
                                        )
                                        .height(14.dp)
                                        .background(
                                            barColor,
                                            RoundedCornerShape(7.dp)
                                        )
                                )
                            }
                            Spacer(Modifier.width(8.dp))
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(72.dp),
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
