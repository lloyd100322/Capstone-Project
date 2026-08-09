package com.mobile.enerjeep.ui.login

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.data.UserProfile
import com.mobile.enerjeep.data.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 
 * Created by Bry1337 on 8/8/26.
 * Copyright (c) 2026. All rights reserved.
 */

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var verificationId: String? = null
    private var pendingProfile: UserProfile? = null

    fun sendVerificationCode(phoneNumber: String, password: String, activity: Activity) {
        if (phoneNumber.length < 10) {
            _uiState.value = LoginUiState.Error("Maling format ng numero")
            return
        }
        
        if (password.isBlank()) {
            _uiState.value = LoginUiState.Error("Pakilagay ang iyong password")
            return
        }

        val lowerPhoneNumber = phoneNumber.lowercase()

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                // Check if user exists in Firestore AND password matches
                val userQuery = firestore.collection("users")
                    .whereEqualTo("phoneNumber", lowerPhoneNumber)
                    .whereEqualTo("password", password)
                    .get()
                    .await()

                if (userQuery.isEmpty) {
                    _uiState.value = LoginUiState.Error("Maling numero o password")
                    return@launch
                }

                val doc = userQuery.documents.first()
                // Manual mapping to handle Enum conversion safely with lowercase roles
                val data = doc.data
                val roleString = data?.get("role") as? String ?: UserRole.Driver.name
                val matchedRole = UserRole.entries.find { 
                    it.name.lowercase() == roleString.lowercase() 
                } ?: UserRole.Driver

                pendingProfile = UserProfile(
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

                // If credentials are correct, proceed to SMS OTP
                triggerSmsVerification(lowerPhoneNumber, activity)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Hindi ma-verify ang account: ${e.message}")
            }
        }
    }

    private fun triggerSmsVerification(phoneNumber: String, activity: Activity) {
        // This disables reCAPTCHA/Play Integrity for testing with fictional phone numbers.
//        auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("PhoneAuth", "Phone verification failed", e)
                    _uiState.value = LoginUiState.Error(e.message ?: "Hindi maipadala ang code")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@LoginViewModel.verificationId = verificationId
                    _uiState.value = LoginUiState.CodeSent
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(otp: String) {
        if (otp.length != 6) {
            _uiState.value = LoginUiState.Error("Dapat ay 6 na digit ang code")
            return
        }

        val id = verificationId ?: run {
            _uiState.value = LoginUiState.Error("Nawala ang session, pakisubukan ulit")
            return
        }

        val credential = PhoneAuthProvider.getCredential(id, otp)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                auth.signInWithCredential(credential).await()
                pendingProfile?.let { userSession.setUser(it) }
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Maling code, pakisubukan ulit")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
        verificationId = null
        pendingProfile = null
    }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object CodeSent : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}
