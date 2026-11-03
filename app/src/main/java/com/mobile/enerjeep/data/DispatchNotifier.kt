package com.mobile.enerjeep.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * System notification para sa mga abiso ng dispatch.
 *
 * Ang in-app banner/bell ay para habang nakabukas ang app. Ang system
 * notification na ito ang nagpapakita sa notification tray ng device -
 * makikita kahit nasa background ang app o nakasara na ito (kapag
 * nakasara, ang FCM push ang nagpapadala - tingnan ang
 * EnerJeepMessagingService).
 *
 * Tahimik (silent) ang notification na ina-post dito mula sa app: ang
 * tunog ay galing na sa ringtone ng banner. Ang FCM push naman (kapag
 * nakasara ang app) ay may tunog sa pamamagitan ng channel.
 */
object DispatchNotifier {

    const val CHANNEL_ID = "enerjeep_dispatch"
    const val INCIDENT_CHANNEL_ID = "enerjeep_incident"

    private const val NOTIFICATION_ID = 1001
    private const val INCIDENT_NOTIFICATION_ID = 1002

    /** Tawagin sa pagbukas ng app - laging ligtas ulitin. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val dispatchChannel = NotificationChannel(
                CHANNEL_ID,
                "Mga Abiso ng Dispatch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Abiso kapag na-dispatch ang sasakyan ng driver"
            }
            manager.createNotificationChannel(dispatchChannel)
        }

        if (manager.getNotificationChannel(INCIDENT_CHANNEL_ID) == null) {
            val incidentChannel = NotificationChannel(
                INCIDENT_CHANNEL_ID,
                "Mga Abiso ng Insidente",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Abiso kapag may bagong insidenteng ipinadala sa mga driver"
            }
            manager.createNotificationChannel(incidentChannel)
        }
    }

    /**
     * Mag-post sa notification tray. Kapag walang pahintulot sa Android
     * 13+, tahimik lang na hindi nagpo-post (hindi nag-crash) - ang
     * in-app banner pa rin ang gagana.
     */
    fun post(
        context: Context,
        title: String,
        body: String
    ) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // Tahimik ito: ang tunog ay galing sa ringtone ng banner
            // (kapag buhay ang app) o sa FCM notification (kapag
            // nakasara).
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Hindi dapat masira ang aktwal na aksyon dahil sa abiso.
        }

    /**
     * Foreground display for incident pushes. When the app is in the
     * background, Firebase/Android displays the notification payload
     * itself; this method is used only when onMessageReceived is called.
     */
    fun postIncident(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = INCIDENT_NOTIFICATION_ID
    ) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, INCIDENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        } catch (_: Exception) {
            // Hindi dapat masira ang app kapag nabigo ang notification.
        }
    }
}
