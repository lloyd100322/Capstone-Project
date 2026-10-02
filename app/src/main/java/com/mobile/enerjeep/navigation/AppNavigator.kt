package com.mobile.enerjeep.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton



@Singleton
class AppNavigator @Inject constructor() {
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(
        route: Any,
        builder: NavOptionsBuilder.() -> Unit = {}
    ) {
        _navigationEvents.tryEmit(NavigationEvent.NavigateTo(route, builder))
    }

    fun navigateBack() {
        _navigationEvents.tryEmit(NavigationEvent.NavigateBack)
    }
}

sealed interface NavigationEvent {
    data class NavigateTo(
        val route: Any,
        val builder: NavOptionsBuilder.() -> Unit
    ) : NavigationEvent

    data object NavigateBack : NavigationEvent
}
