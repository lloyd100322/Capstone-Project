package com.mobile.enerjeep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.UserProfile
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _authState = MutableStateFlow<AppAuthState>(AppAuthState.Loading)
    val authState = _authState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _authState.value = AppAuthState.Unauthenticated
            return
        }

        viewModelScope.launch {
            try {
                // Phone number in Firebase Auth is usually +63...
                val phoneNumber = currentUser.phoneNumber ?: run {
                    auth.signOut()
                    _authState.value = AppAuthState.Unauthenticated
                    return@launch
                }

                val doc = firestore.collection("users")
                    .document(phoneNumber.lowercase())
                    .get()
                    .await()

                if (doc.exists()) {
                    val data = doc.data
                    val roleString = data?.get("role") as? String ?: UserRole.Driver.name
                    val matchedRole = UserRole.entries.find { 
                        it.name.lowercase() == roleString.lowercase() 
                    } ?: UserRole.Driver

                    val profile = UserProfile(
                        userId = data?.get("userId") as? String ?: "",
                        firstName = data?.get("firstName") as? String ?: "",
                        lastName = data?.get("lastName") as? String ?: "",
                        phoneNumber = data?.get("phoneNumber") as? String ?: "",
                        password = data?.get("password") as? String ?: "",
                        role = matchedRole,
                        licenseNumber = data?.get("licenseNumber") as? String,
                        plateNumber = data?.get("plateNumber") as? String,
                        emergencyContact = data?.get("emergencyContact") as? String
                    )
                    
                    userSession.setUser(profile)
                    _authState.value = AppAuthState.Authenticated(Screen.Home)
                } else {
                    // Profile missing in Firestore, force re-login
                    auth.signOut()
                    _authState.value = AppAuthState.Unauthenticated
                }
            } catch (e: Exception) {
                // Network error or other issues, default to login for safety
                _authState.value = AppAuthState.Unauthenticated
            }
        }
    }
}

sealed interface AppAuthState {
    data object Loading : AppAuthState
    data object Unauthenticated : AppAuthState
    data class Authenticated(val startDestination: Screen) : AppAuthState
}
