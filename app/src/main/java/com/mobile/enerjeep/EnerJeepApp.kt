package com.mobile.enerjeep

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
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
            val debugFactory = DebugAppCheckProviderFactory.getInstance()

            appCheck.installAppCheckProviderFactory(debugFactory)

            /*
             * Always print the App Check debug token at startup so it can
             * be registered in the Firebase console (App Check -> Settings
             * -> Manage debug tokens). The message itself repeats the
             * AppCheckDebug words so ANY Logcat search (tag or full text)
             * will find it.
             */
            Log.d(
                "AppCheckDebug",
                "AppCheckDebug: EnerJeepApp started (debug build) - " +
                    "loading AppCheckDebug token..."
            )

            try {
                debugFactory
                    .create(FirebaseApp.getInstance())
                    .getToken()
                    .addOnSuccessListener { token ->
                        Log.d(
                            "AppCheckDebug",
                            "AppCheckDebug TOKEN: ${token.getToken()} - " +
                                "i-paste ito sa Firebase console > " +
                                "App Check > Settings > Manage debug tokens"
                        )
                    }
                    .addOnFailureListener { error ->
                        Log.e(
                            "AppCheckDebug",
                            "AppCheckDebug: hindi nakuha ang token: " +
                                error.message
                        )
                    }
            } catch (error: Exception) {
                Log.e(
                    "AppCheckDebug",
                    "AppCheckDebug: error sa token request: " +
                        error.message
                )
            }
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
