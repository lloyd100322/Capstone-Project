package com.mobile.enerjeep.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging - ito ang nagpapagana ng abiso KAHIT
 * NAKASARA ang app.
 *
 * Paano gumagana:
 *  - Ang Cloud Function (`onQueueDispatched` sa functions/index.js) ang
 *    nagpapadala ng push kapag may na-dispatch.
 *  - Kapag NAKASARA o naka-background ang app, ang system ng FCM ang
 *    nagpapakita ng notification (notification payload) - kaya kahit
 *    patay ang proseso, lumalabas ang abiso.
 *  - Kapag BUKAS ang app, ang in-app banner/bell ang nag-a-alert (may
 *    tunog) kaya hindi nagpo-post dito para hindi mag-doble.
 *  - `onNewToken` ang nag-i-save ng device token sa /users/{phone}
 *    (`fcmToken`) - dito nagpapadala ang function.
 */
class EnerJeepMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        /*
         * Kapag nasa background/nakasara ang app, Android mismo ang
         * nagpapakita ng FCM notification payload. Kapag foreground,
         * tawagin ang local notifier para makita rin ang incident push.
         *
         * Hindi ito nagpo-post ng dispatch push sa foreground dahil may
         * sarili nang in-app dispatch alert ang Home screen.
         */
        if (message.data["type"] != "incident") return

        val title = message.notification?.title
            ?: "Bagong Insidente sa Kooperatiba"
        val body = message.notification?.body
            ?: "May bagong insidenteng ipinadala sa mga driver."
        val notificationId =
            message.data["incidentId"]?.hashCode()
                ?: 1002

        DispatchNotifier.postIncident(
            context = this,
            title = title,
            body = body,
            notificationId = notificationId
        )
    }

    private fun saveToken(token: String) {
        val phone =
            FirebaseAuth.getInstance().currentUser?.phoneNumber
                ?: return

        try {
            FirebaseFirestore.getInstance()
                .collection(FirestoreCollections.USERS)
                .document(phone.lowercase())
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener { error ->
                    Log.w("FCMToken", "Failed to save refreshed FCM token", error)
                }
        } catch (_: Exception) {
            // Hindi dapat masira ang token refresh.
        }
    }
}
