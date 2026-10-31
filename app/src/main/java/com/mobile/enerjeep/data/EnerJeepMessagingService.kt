package com.mobile.enerjeep.data

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
         * Ang mensahe ay may notification payload: kapag naka-background
         * o nakasara ang app, ang SYSTEM na ang nagpakita nito (hindi
         * na tumatawag ng method na ito). Kapag foreground naman, ang
         * in-app banner + tunog + bell na ang gumagana (may Firestore
         * listener din) - kaya tahimik lang dito para hindi mag-doble.
         */
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
        } catch (_: Exception) {
            // Hindi dapat masira ang token refresh.
        }
    }
}
