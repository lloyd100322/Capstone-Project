/**
 * EnerJeep Cloud Functions.
 *
 * The Android app can delete the Firestore documents of an account
 * (/users/{phone} and /userSecrets/{phone}), but the Firebase
 * Authentication record CANNOT be deleted from the client (Firebase
 * does not allow one user to delete another Auth user). Without this
 * function the Auth record would survive the deletion and the phone
 * number could never be used again for a new account.
 *
 * This function listens for the account document deletion and removes
 * the matching Firebase Auth user (found by its phone number, which is
 * also the document id).
 *
 * Deploy (requires the Blaze/pay-as-you-go plan):
 *
 *   cd functions && npm install && cd ..
 *   firebase deploy --only functions
 */

const { onDocumentDeleted, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

exports.deleteAuthUserOnAccountDelete = onDocumentDeleted(
  "users/{phone}",
  async (event) => {
    // The document id of /users is the phone number, e.g. "+639171234567".
    const phone = event.params.phone;

    try {
      const authUser = await admin.auth().getUserByPhoneNumber(phone);
      await admin.auth().deleteUser(authUser.uid);
      console.log(`Deleted Firebase Auth user for ${phone}.`);
    } catch (error) {
      if (error && error.code === "auth/user-not-found") {
        // Already gone (or the account never verified by phone): nothing
        // to do, and this is NOT an error.
        console.log(`No Firebase Auth user for ${phone}; nothing to delete.`);
        return;
      }

      // Never throw: the Firestore deletion already happened and failing
      // here would only retry forever. Log for the operator instead.
      console.error(`Failed to delete Auth user for ${phone}:`, error);
    }
  }
);

/*
 * ============================================================================
 * AWTOMATIKONG RESET NG PILA (background) - tumatakbo kahit SARADO ang app.
 *
 * Isang Cloud Scheduler job ang tumatawag nito bawat oras (Asia/Manila).
 * Kapag lagpas na sa cut-off hour (settings/queueRotation.cutoffHour,
 * default 21 = 9 PM) at hindi pa na-ikot ang araw na iyon, ina-ikot nito
 * ang pila: ang #1 ng bawat ruta ay napupunta sa huli, ang kasunod ang
 * mauuna. Idempotent ito - ang unang makakatransaksyon ang nagre-reset
 * (lastQueueDay marker), ang iba ay umuurong na.
 *
 * Ang Android app ay may parehong engine (QueueRotation) bilang PANTULONG:
 * kung hindi tumakbo ang scheduler (hal. hindi pa naka-on ang Blaze plan),
 * nagre-reset pa rin ang pila sa unang pagbukas ng app.
 *
 * Deploy (requires the Blaze/pay-as-you-go plan):
 *   cd functions && npm install && cd ..
 *   firebase deploy --only functions
 * ============================================================================
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { FieldValue } = require("firebase-admin/firestore");

const QUEUE_STATUSES_INACTIVE = ["dispatched", "cancelled", "completed", "resolved"];

function isQueueActive(data) {
  const status = (data.status || "").toLowerCase();
  return !QUEUE_STATUSES_INACTIVE.includes(status) && data.isCancelled !== true;
}

/** Pareho ng QueueViewModel.routeKey: "Marilao..." ang entry route. */
function routeKey(route) {
  return (route || "").trim().toLowerCase().startsWith("marilao") ? 1 : 0;
}

/** Pareho ng QueueRotation.queueDayId (Asia/Manila ang araw ng operasyon). */
function queueDayIdManila(now, cutoffHour) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Manila",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      hour12: false,
    })
      .formatToParts(now)
      .map((p) => [p.type, p.value])
  );
  const hour = parseInt(parts.hour, 10) % 24;

  // Ang cut-off ay bahagi ng PAREHONG araw ng operasyon: pagkalipas ng
  // cut-off, ang pila ay pagmamay-ari na ng susunod na araw.
  const day = new Date(Date.UTC(+parts.year, +parts.month - 1, +parts.day));
  if (hour >= cutoffHour) {
    day.setUTCDate(day.getUTCDate() + 1);
  }
  const y = day.getUTCFullYear();
  const m = String(day.getUTCMonth() + 1).padStart(2, "0");
  const d = String(day.getUTCDate()).padStart(2, "0");
  return `${y}${m}${d}`;
}

exports.dailyQueueReset = onSchedule(
  { schedule: "0 * * * *", timeZone: "Asia/Manila" },
  async () => {
    const metaRef = db.collection("settings").doc("queueRotation");

    // Simple read muna para makuha ang mga id (walang query sa loob ng
    // transaction sa mobile/admin SDK - pareho ng QueueRotation).
    const queueIds = (await db.collection("queue").get()).docs.map((d) => d.id);

    const rotated = await db.runTransaction(async (tx) => {
      const meta = await tx.get(metaRef);
      const rawHour = meta.get("cutoffHour");
      const cutoffHour =
        typeof rawHour === "number" && rawHour >= 0 && rawHour <= 23
          ? rawHour
          : 21;

      const today = queueDayIdManila(new Date(), cutoffHour);
      if (meta.get("lastQueueDay") === today) {
        return false; // na-ikot na ang araw na ito
      }

      // Basahin ang bawat entry SA LOOB ng transaction (laging sariwa).
      const docs = [];
      for (const id of queueIds) {
        const snap = await tx.get(db.collection("queue").doc(id));
        if (snap.exists) docs.push(snap);
      }

      [1, 0].forEach((key) => {
        const active = docs
          .filter((d) => isQueueActive(d.data()) && routeKey(d.data().route) === key)
          .sort(
            (a, b) =>
              (a.get("position") ?? Number.MAX_SAFE_INTEGER) -
              (b.get("position") ?? Number.MAX_SAFE_INTEGER)
          );

        if (active.length > 1) {
          // Rotation: ang #1 ngayon ay napupunta sa huli.
          const rotated = [...active.slice(1), active[0]];
          rotated.forEach((doc, index) => {
            tx.update(doc.ref, {
              position: index + 1,
              updatedAt: FieldValue.serverTimestamp(),
            });
          });
        }
      });

      tx.set(
        metaRef,
        { lastQueueDay: today, updatedAt: FieldValue.serverTimestamp() },
        { merge: true }
      );
      return true;
    });

    if (rotated) {
      console.log("dailyQueueReset: na-ikot ang pila para sa bagong araw");
    }
  }
);

/**
 * PUSH NOTIFICATION sa dispatch.
 *
 * Kapag ang isang queue entry ay naging "dispatched", nagpapadala ng
 * Firebase Cloud Messaging (FCM) push sa device ng driver - KAHIT
 * NAKASARA ang app. Ang notification payload ay awtomatikong
 * ipinapakita ng system ng Android (may tunog sa channel
 * "enerjeep_dispatch"), kaya hindi na kailangan ng buhay na proseso ng
 * app. Ang in-app banner/bell naman ang gumagana kapag bukas ang app
 * (tahimik ang push doon para hindi mag-doble).
 */
exports.onQueueDispatched = onDocumentUpdated(
  "queue/{entryId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();

    // Tanging ang PAGPASOK sa "dispatched" lang - hindi bawat update.
    if ((after.status || "") !== "dispatched") return null;
    if ((before.status || "") === "dispatched") return null;

    const driverId = after.driverId;
    if (!driverId) return null;

    // Hanapin ang user doc ng driver (driverId -> users.userId) para
    // makuha ang fcmToken ng device.
    const users = await db
      .collection("users")
      .where("userId", "==", driverId)
      .limit(1)
      .get();
    if (users.empty) return null;

    const userRef = users.docs[0].ref;
    const token = users.docs[0].get("fcmToken");
    if (!token || typeof token !== "string") return null;

    const plate = after.plateNumber || "";
    const route = after.route || "";
    const body = [plate, route].filter((x) => x && String(x).trim() !== "").join(" • ");

    try {
      await admin.messaging().send({
        token,
        // Notification payload: ipinapakita ito ng system kahit
        // nakasara ang app.
        notification: {
          title: "Na-dispatch ka!",
          body: body || "Tingnan ang Pila para sa detalye.",
        },
        data: {
          type: "dispatch",
          entryId: event.params.entryId,
          plateNumber: plate,
          route,
        },
        android: {
          notification: {
            channel_id: "enerjeep_dispatch",
            sound: "default",
          },
        },
      });
      console.log(`onQueueDispatched: naipadala ang push sa ${users.docs[0].id}`);
    } catch (error) {
      const code = error && error.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token" ||
        code === "messaging/invalid-argument"
      ) {
        // Lipas na ang token ng device - alisin para hindi na subukan.
        await userRef.update({ fcmToken: FieldValue.delete() });
        console.log(`onQueueDispatched: inalis ang lipas na token (${code})`);
      } else {
        console.error("onQueueDispatched: hindi naipadala:", error);
      }
    }
    return null;
  }
);
