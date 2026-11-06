package com.mobile.enerjeep.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intuit.sdp.R as SdpR
import com.intuit.ssp.R as SspR

/**
 * Compose adapters for Intuit SDP/SSP resources.
 *
 * SDP/SSP resolve through the current smallest-width resource bucket.
 * Clamps keep spacing and headings controlled on very narrow windows and
 * prevent them from growing excessively on large screens.
 */
@Composable
fun responsivePageHorizontalPadding(): Dp =
    dimensionResource(SdpR.dimen._20sdp).coerceIn(16.dp, 24.dp)

/**
 * SSP dimensions are returned by dimensionResource as Dp. Convert back to
 * TextUnit using the active density so Android's accessibility font scale is
 * applied by Text exactly once.
 */
@Composable
fun responsivePageTitleSize(): TextUnit {
    val titleDimension = dimensionResource(SspR.dimen._26ssp)
    val titleSp = with(LocalDensity.current) { titleDimension.toSp() }
    return titleSp.value.coerceIn(26f, 28f).sp
}
