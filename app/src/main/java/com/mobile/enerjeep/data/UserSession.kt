package com.mobile.enerjeep.data

import javax.inject.Inject
import javax.inject.Singleton

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

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

data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val role: UserRole = UserRole.Driver,
    val licenseNumber: String? = null,
    val plateNumber: String? = null,
    val emergencyContact: String? = null
)
