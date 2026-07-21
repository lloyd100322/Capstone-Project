package com.mobile.enerjeep

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mobile.enerjeep.ui.theme.EnerjeepTheme

/** 
 * Created by Bry1337 on 7/21/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun ContentView(
    finishActivity: () -> Unit
) {
    EnerjeepTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello Capstone Project - $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EnerjeepTheme {
        Greeting("Android")
    }
}
