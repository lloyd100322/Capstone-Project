package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject



@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val appNavigator: AppNavigator,
    private val userSession: UserSession
) : ViewModel() {

    fun logout() {
        auth.signOut()
        userSession.clear()
        appNavigator.navigateTo(Screen.Login) {
            popUpTo(Screen.Home) { inclusive = true }
        }
    }
}
