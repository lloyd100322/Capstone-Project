package com.mobile.enerjeep.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSession @Inject constructor() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)

    /**
     * Reactive view of the signed-in profile. The UI collects this so
     * screens update immediately when the profile changes (for example
     * when the emergency contact is edited on the Profile screen).
     */
    val userProfileFlow: StateFlow<UserProfile?> =
        _userProfile.asStateFlow()

    val userProfile: UserProfile?
        get() = _userProfile.value

    fun setUser(profile: UserProfile) {
        _userProfile.value = profile
    }

    fun clear() {
        _userProfile.value = null
    }

    /*
     * Superadmin and Admin share the admin dashboard. Only the
     * superadmin may create admin accounts and manage the emergency
     * contact list (see CreateAccountScreen).
     */
    fun isAdmin(): Boolean =
        userProfile?.role == UserRole.Admin ||
                userProfile?.role == UserRole.Superadmin

    fun isSuperadmin(): Boolean =
        userProfile?.role == UserRole.Superadmin

    fun isDriver(): Boolean = userProfile?.role == UserRole.Driver
}

/**
 * The signed-in user's profile.
 *
 * NOTE: this class must NEVER carry the password (or its hash). The
 * password is verified against /userSecrets and forgotten immediately.
 */
data class UserProfile(
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val role: UserRole = UserRole.Driver,
    val licenseNumber: String? = null,
    val plateNumber: String? = null,
    val emergencyContact: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactNumber: String? = null,
    // Istasyon ng admin (Marilao/Monumento) - display lang, walang
    // epekto sa role/permiso. Wala nito ang driver at superadmin.
    val adminStation: String? = null
)
