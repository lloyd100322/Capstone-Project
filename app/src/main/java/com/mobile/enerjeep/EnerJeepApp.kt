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
             * -> Manage debug tokens). Older SDK builds only logged it
             * once, which made the token hard to find.
             */
            debugFactory
                .create(FirebaseApp.getInstance())
                .appCheckToken
                .addOnSuccessListener { token ->
                    Log.d(
                        "AppCheckDebug",
                        "Enter this debug token into the list of " +
                            "valid debug tokens in the Firebase " +
                            "console for your project: ${token.token}"
                    )
                }
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
