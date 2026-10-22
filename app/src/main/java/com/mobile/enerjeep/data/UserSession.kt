package com.mobile.enerjeep.data

import javax.inject.Inject
import javax.inject.Singleton



@Singleton
class UserSession @Inject constructor() {
    var userProfile: UserProfile? = null
        private set

    fun setUser(profile: UserProfile) {
        userProfile = profile
    }

    fun clear() {
        userProfile = null
    }

    fun isAdmin(): Boolean = userProfile?.role == UserRole.Admin
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
    val emergencyContactNumber: String? = null
)
