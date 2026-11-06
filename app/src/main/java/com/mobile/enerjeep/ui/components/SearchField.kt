package com.mobile.enerjeep.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.TextSecondary

/**
 * Shared compact, single-line search input used across the app.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(48.dp),
        textStyle = TextStyle(fontSize = 14.sp),
        placeholder = {
            Text(placeholder, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Hanapin",
                tint = TextSecondary,
                modifier = Modifier.height(18.dp)
            )
        },
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = FieldBorderColor,
            focusedBorderColor = EnerjeepBlue,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
}
