package com.mobile.enerjeep.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mobile.enerjeep.MainActivity
import com.mobile.enerjeep.ui.home.CreateAccountScreen
import com.mobile.enerjeep.ui.home.HomeScreen
import com.mobile.enerjeep.ui.login.LoginScreen

/** 
 * Created by Bry1337 on 8/1/26.
 * Copyright (c) 2026. All rights reserved.
 */

@Composable
fun NavGraph(
    navController: NavHostController,
    appNavigator: AppNavigator,
    activity: MainActivity,
    startDestination: Screen
) {
    LaunchedEffect(Unit) {
        appNavigator.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateTo -> {
                    navController.navigate(event.route, event.builder)
                }
                NavigationEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<Screen.Login> {
            LoginScreen(
                activity = activity,
                onLoginSuccess = {
                    appNavigator.navigateTo(Screen.Home) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.Home> {
            HomeScreen(appNavigator = appNavigator)
        }
        composable<Screen.CreateAccount> {
            CreateAccountScreen()
        }
    }
}
