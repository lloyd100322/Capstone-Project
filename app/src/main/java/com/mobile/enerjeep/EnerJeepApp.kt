package com.mobile.enerjeep

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EnerJeepApp : Application() {
    override fun onCreate() {
        super.onCreate()

        /*
         * App Check proves that requests come from the real app, so a
         * script or a modified client cannot read the database even with
         * the google-services.json values. Debug builds use the debug
         * provider; release builds use Play Integrity.
         */
        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
