package com.mobile.enerjeep

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.NavGraph
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.theme.EnerjeepTheme
import com.mobile.enerjeep.ui.theme.LightGrayBackground

/** 
 * Created by Bry1337 on 7/21/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun ContentView(
    appNavigator: AppNavigator,
    finishActivity: () -> Unit,
    activity: MainActivity,
    viewModel: ContentViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    EnerjeepTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LightGrayBackground
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = authState) {
                    is AppAuthState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AppAuthState.Authenticated -> {
                        NavGraph(
                            navController = navController,
                            appNavigator = appNavigator,
                            activity = activity,
                            startDestination = state.startDestination
                        )
                    }
                    is AppAuthState.Unauthenticated -> {
                        NavGraph(
                            navController = navController,
                            appNavigator = appNavigator,
                            activity = activity,
                            startDestination = Screen.Login
                        )
                    }
                }
            }
        }
    }
}
