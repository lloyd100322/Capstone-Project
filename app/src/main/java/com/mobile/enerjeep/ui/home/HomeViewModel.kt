package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.data.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject



@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userSession: UserSession,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    val userProfile: UserProfile?
        get() = userSession.userProfile

    private val _activeJeepsCount = MutableStateFlow(0)
    val activeJeepsCount = _activeJeepsCount.asStateFlow()

    private val _totalJeepsCount = MutableStateFlow(0)
    val totalJeepsCount = _totalJeepsCount.asStateFlow()

    init {
        startListeners()
    }

    private fun startListeners() {
        // Listen to all drivers to get active/total counts
        firestore.collection("users")
            .whereEqualTo("role", "driver")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _totalJeepsCount.value = snapshot.size()
                    // For now, assuming all registered drivers are "active" for demo
                    // In a real app, you'd check a 'status' field
                    _activeJeepsCount.value = snapshot.size()
                }
            }
    }

    fun isAdmin(): Boolean = userSession.isAdmin()
    fun isDriver(): Boolean = userSession.isDriver()
}
