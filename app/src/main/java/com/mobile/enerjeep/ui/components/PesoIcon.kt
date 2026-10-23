package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================================================================
 *  PESO SIGN — DITO KAYO MAG-EDIT
 * ============================================================================
 *  String na po ang peso sign ngayon (karaniwang text na "₱"), hindi na
 *  guhit/canvas. Baguhin lang ang mga halaga sa ibaba at AWTOMATIKONG
 *  mag-iiba ang peso sign SA LAHAT NG LUGAR:
 *
 *    - Admin dashboard  ("Kabuuang Benta" card)
 *    - Kita tab ng driver (bottom navigation)
 *    - Dispatch collection form  (Kita / Other Fees)
 *    - Ledger "+ Idagdag"  (Halaga)
 *    - Donasyon "+ Idagdag"  (Halaga)
 *    - Insidente report form  (Multa)
 *
 *  Pagkatapos baguhin, i-build at i-run lang ulit ang app.
 * ============================================================================
 */

/** 1. ANG KARAKTER. Halimbawa: "₱", "P", "PHP", o "₱" (default). */
private const val PESO_CHARACTER = "₱"

/** 2. KAPAL NG LETRA: Normal, Medium, SemiBold, Bold, Black. */
private val PESO_FONT_WEIGHT = FontWeight.Bold

/**
 * 3. LAKI NG LETRA kumpara sa laki ng icon (0.5 hanggang 1.2).
 *    Halimbawa: 0.85 = medyo maliit kaysa sa box, 1.0 = saktong puno.
 */
private const val PESO_SIZE_RATIO = 0.85f

/** 4. IUSOG PATAAS/BABA kung hindi ninyo gusto ang direksyon (sp). */
private const val PESO_VERTICAL_OFFSET_SP = 0f

/**
 * Ang peso sign bilang text. Lahat ng screen ay dumadaan dito kaya isang
 * edit lang ang kailangan para mabago ang buong app.
 */
@Composable
fun PesoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.Black
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = PESO_CHARACTER,
            color = color,
            fontWeight = PESO_FONT_WEIGHT,
            fontSize = (size.value * PESO_SIZE_RATIO).sp,
            modifier = if (PESO_VERTICAL_OFFSET_SP == 0f) {
                Modifier
            } else {
                Modifier.offset(y = PESO_VERTICAL_OFFSET_SP.sp)
            }
        )
    }
}

/** Pareho lang ng PesoIcon — ginagamit sa "Kita" tab at dashboard. */
@Composable
fun SalesIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color.Blue
) {
    PesoIcon(modifier = modifier, size = size, color = color)
}
