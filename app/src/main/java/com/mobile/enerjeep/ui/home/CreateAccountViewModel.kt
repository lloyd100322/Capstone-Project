package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val appNavigator: AppNavigator
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateAccountUiState>(CreateAccountUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun createAccount(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        password: String,
        role: UserRole,
        licenseNumber: String = "",
        plateNumber: String = "",
        emergencyContact: String = ""
    ) {
        if (firstName.isBlank() || lastName.isBlank() || phoneNumber.isBlank() || password.isBlank()) {
            _uiState.value = CreateAccountUiState.Error("Pakilagay ang lahat ng kailangang impormasyon")
            return
        }

        val cleanPhoneNumber = phoneNumber.lowercase()
        val generatedUserId = UUID.randomUUID().toString()

        val userMap = mutableMapOf(
            "userId" to generatedUserId,
            "firstName" to firstName,
            "lastName" to lastName,
            "phoneNumber" to cleanPhoneNumber,
            "password" to password,
            "role" to role.name.lowercase(),
            "createdAt" to System.currentTimeMillis()
        )

        if (role == UserRole.Driver) {
            userMap["licenseNumber"] = licenseNumber
            userMap["plateNumber"] = plateNumber
            userMap["emergencyContact"] = emergencyContact
        }

        viewModelScope.launch {
            _uiState.value = CreateAccountUiState.Loading
            try {
                firestore.collection("users")
                    .document(cleanPhoneNumber)
                    .set(userMap)
                    .await()
                _uiState.value = CreateAccountUiState.Success
            } catch (e: Exception) {
                _uiState.value = CreateAccountUiState.Error(e.message ?: "Hindi nagawa ang account")
            }
        }
    }

    fun goBack() {
        appNavigator.navigateBack()
    }

    fun resetState() {
        _uiState.value = CreateAccountUiState.Idle
    }
}

sealed interface CreateAccountUiState {
    data object Idle : CreateAccountUiState
    data object Loading : CreateAccountUiState
    data object Success : CreateAccountUiState
    data class Error(val message: String) : CreateAccountUiState
}
