package com.mobile.enerjeep.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mobile.enerjeep.MainActivity
import com.mobile.enerjeep.ui.home.AccountManagementScreen
import com.mobile.enerjeep.ui.home.CreateAccountScreen
import com.mobile.enerjeep.ui.home.HomeScreen
import com.mobile.enerjeep.ui.login.LoginScreen
import com.mobile.enerjeep.ui.login.ResetPasswordScreen

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
                    /*
                     * Never pop the LAST entry of the back stack. When the
                     * stack becomes empty (double tap on the back arrow, or
                     * back right after a slow write), the NavHost renders
                     * nothing and the whole app turns into a blank screen.
                     * Home is the root: back there must stay a no-op.
                     */
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
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
                appNavigator = appNavigator,
                activity = activity,
                onLoginSuccess = {
                    appNavigator.navigateTo(Screen.Home) {
                        popUpTo(Screen.Login) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable<Screen.Home> {
            HomeScreen(appNavigator = appNavigator)
        }

        composable<Screen.AccountManagement> {
            AccountManagementScreen(
                appNavigator = appNavigator
            )
        }

        composable<Screen.CreateAccount> {
            CreateAccountScreen()
        }

        composable<Screen.ResetPassword> {
            ResetPasswordScreen(
                appNavigator = appNavigator
            )
        }
    }
}