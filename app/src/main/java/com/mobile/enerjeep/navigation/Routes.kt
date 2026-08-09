package com.mobile.enerjeep.navigation

import kotlinx.serialization.Serializable

/** 
 * Created by Bry1337 on 8/1/26.
 * Copyright (c) 2026. All rights reserved.
 */

sealed interface Screen {
    @Serializable
    data object Login : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object CreateAccount : Screen
}
