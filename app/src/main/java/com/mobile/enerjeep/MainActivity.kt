package com.mobile.enerjeep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mobile.enerjeep.data.DispatchNotifier
import com.mobile.enerjeep.navigation.AppNavigator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appNavigator: AppNavigator

    /*
     * Pahintulot sa notification (Android 13+): hinihingi ito ng app
     * kapag unang binuksan - "mag ask ng permission na lang sa device".
     * Kapag tinanggihan, tuloy pa rin ang app at ang in-app banner ang
     * mananatiling alerto.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* Kahit tanggihan, tuloy ang app. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Laging ligtas ulitin - idempotent ang channel creation.
        DispatchNotifier.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        enableEdgeToEdge()
        setContent {
            ContentView(
                appNavigator = appNavigator,
                activity = this,
                finishActivity = {
                    finish()
                }
            )
        }
    }
}
