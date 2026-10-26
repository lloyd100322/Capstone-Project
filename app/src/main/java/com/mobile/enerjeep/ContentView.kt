package com.mobile.enerjeep

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.NavGraph
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.theme.EnerjeepTheme
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.components.ToastFeedbackHost


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
                ToastFeedbackHost()
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
                    is AppAuthState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp)
                            )
                            Button(onClick = { viewModel.retry() }) {
                                Text("Subukan", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
