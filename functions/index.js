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

const { onDocumentDeleted } = require("firebase-functions/v2/firestore");
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
