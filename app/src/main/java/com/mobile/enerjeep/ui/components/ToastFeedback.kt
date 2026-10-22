package com.mobile.enerjeep.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ToastFeedback {

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 32
    )

    val messages = _messages.asSharedFlow()

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

@Composable
fun ToastFeedbackHost() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ToastFeedback.messages.collect { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}