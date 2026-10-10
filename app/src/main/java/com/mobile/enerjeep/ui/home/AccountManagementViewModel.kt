package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// 1. Data Model
data class AccountItem(
    val id: String,
    val name: String,
    val role: String,
    val phone: String,
    val dateCreated: String,
    val isActive: Boolean
)

// 2. UI State
data class AccountManagementUiState(
    val searchQuery: String = "",
    val allAccounts: List<AccountItem> = emptyList(),
    val filteredAccounts: List<AccountItem> = emptyList()
)

// 3. ViewModel
@HiltViewModel
class AccountManagementViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManagementUiState())
    val uiState: StateFlow<AccountManagementUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        // Mock data. This will be replaced with Firestore fetching later.
        val mockAccounts = listOf(
            AccountItem("1", "Pedro Santos", "Admin", "+63 9179876543", "Jul 16, 2026", false),
            AccountItem("2", "Maria Garcia", "Driver", "+63 9175551234", "Jul 16, 2026", true),
            AccountItem("3", "Jose Reyes", "Driver", "+63 9171112222", "Jul 16, 2026", false)
        )

        _uiState.value = _uiState.value.copy(allAccounts = mockAccounts)
        applyFilters()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun toggleAccountStatus(accountId: String) {
        val updatedAccounts = _uiState.value.allAccounts.map { account ->
            if (account.id == accountId) {
                account.copy(isActive = !account.isActive)
            } else {
                account
            }
        }
        _uiState.value = _uiState.value.copy(allAccounts = updatedAccounts)
        applyFilters()
    }

    fun deleteAccount(accountId: String) {
        val updatedAccounts = _uiState.value.allAccounts.filter { it.id != accountId }
        _uiState.value = _uiState.value.copy(allAccounts = updatedAccounts)
        applyFilters()
    }

    private fun applyFilters() {
        val currentState = _uiState.value
        val filtered = if (currentState.searchQuery.isBlank()) {
            currentState.allAccounts
        } else {
            currentState.allAccounts.filter {
                it.name.contains(currentState.searchQuery, ignoreCase = true) ||
                        it.phone.contains(currentState.searchQuery, ignoreCase = true)
            }
        }
        _uiState.value = currentState.copy(filteredAccounts = filtered)
    }
}