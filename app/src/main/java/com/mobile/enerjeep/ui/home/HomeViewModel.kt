package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.data.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userSession: UserSession
) : ViewModel() {

    val userProfile: UserProfile?
        get() = userSession.userProfile

    fun isAdmin(): Boolean = userSession.isAdmin()
    fun isDriver(): Boolean = userSession.isDriver()
}
