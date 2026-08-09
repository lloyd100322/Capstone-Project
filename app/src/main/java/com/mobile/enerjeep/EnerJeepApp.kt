package com.mobile.enerjeep

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

/** 
 * Created by Bry1337 on 7/21/26.
 * Copyright (c) 2026. All rights reserved.
 */

@HiltAndroidApp
class EnerJeepApp: Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = Firebase.appCheck
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
