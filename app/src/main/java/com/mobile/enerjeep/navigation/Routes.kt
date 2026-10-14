package com.mobile.enerjeep.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {

    @Serializable
    data object Login : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object AccountManagement : Screen

    @Serializable
    data object CreateAccount : Screen

    @Serializable
    data object ResetPassword : Screen
}