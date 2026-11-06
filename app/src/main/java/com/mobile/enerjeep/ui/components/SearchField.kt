package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.TextSecondary

/**
 * Shared compact, single-line search input used across the app.
 *
 * BasicTextField is used instead of forcing a Material OutlinedTextField
 * into a 48dp height (below its default minimum), which clipped the
 * placeholder and input vertically on some devices.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Color.White)
            .border(
                width = 1.dp,
                color = if (isFocused) EnerjeepBlue else FieldBorderColor,
                shape = shape
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Hanapin",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = placeholder },
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF172033),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(EnerjeepBlue),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
