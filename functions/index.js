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
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

exports.deleteAuthUserOnAccountDelete = onDocumentDeleted(
  { document: "users/{phone}", retry: true },
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

      console.error(`Failed to delete Auth user for ${phone}; retrying:`, error);
      throw error;
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
    const queueControlRef = db.collection("settings").doc("queueControl");
    const observedRevision = (await queueControlRef.get()).get("revision") || 0;
    const queueIds = (await db.collection("queue").get()).docs.map((d) => d.id);

    const rotated = await db.runTransaction(async (tx) => {
      const queueControl = await tx.get(queueControlRef);
      const currentRevision = queueControl.get("revision") || 0;
      if (currentRevision !== observedRevision) {
        throw new Error("Queue changed during scheduled reset; retry on next schedule.");
      }
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
      tx.set(
        queueControlRef,
        { revision: currentRevision + 1, updatedAt: FieldValue.serverTimestamp() },
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


/**
 * Completes the authenticated driver's own queue entry. The server verifies
 * the Phone Auth identity and changes the entry and route positions in one
 * Admin SDK transaction; clients cannot reorder another driver's queue row.
 */
exports.finishDriverTravel = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kailangan mag-login muna.");
    }

    const phone = request.auth.token.phone_number;
    if (typeof phone !== "string" || !phone) {
      throw new HttpsError("permission-denied", "Walang verified phone number.");
    }

    const userSnap = await db.collection("users").doc(phone).get();
    if (!userSnap.exists || userSnap.get("role") !== "driver") {
      throw new HttpsError("permission-denied", "Driver account lamang ang puwedeng magtapos ng biyahe.");
    }

    const driverId = userSnap.get("userId");
    const entryId = request.data && request.data.queueEntryId;
    if (typeof driverId !== "string" || !driverId ||
        typeof entryId !== "string" || !entryId) {
      throw new HttpsError("invalid-argument", "Invalid queue entry.");
    }

    const entryRef = db.collection("queue").doc(entryId);
    const queueControlRef = db.collection("settings").doc("queueControl");

    try {
      await db.runTransaction(async (tx) => {
        const entry = await tx.get(entryRef);
        if (!entry.exists || entry.get("driverId") !== driverId ||
            !isQueueActive(entry.data()) || entry.get("isCancelled") === true) {
          // An already-completed request is safe to repeat after a network timeout.
          if (entry.exists &&
              entry.get("driverId") === driverId &&
              entry.get("status") === "completed") return;
          throw new HttpsError("failed-precondition", "Wala na sa aktibong pila ang sasakyang ito.");
        }

        const route = entry.get("route");
        if (typeof route !== "string" || !route) {
          throw new HttpsError("failed-precondition", "Walang valid na ruta ang queue entry.");
        }

        const routeSnapshot = await tx.get(
          db.collection("queue").where("route", "==", route)
        );
        const remaining = routeSnapshot.docs
          .filter((doc) => doc.id !== entryId &&
            !QUEUE_STATUSES_INACTIVE.includes(String(doc.get("status") || "").toLowerCase()))
          .sort((a, b) => {
            const cancelledA = a.get("isCancelled") === true ? 1 : 0;
            const cancelledB = b.get("isCancelled") === true ? 1 : 0;
            return cancelledA - cancelledB ||
              (a.get("position") ?? Number.MAX_SAFE_INTEGER) -
              (b.get("position") ?? Number.MAX_SAFE_INTEGER);
          });

        const control = await tx.get(queueControlRef);
        const revision = control.get("revision") || 0;

        tx.update(entryRef, {
          status: "completed",
          endTravelAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
          position: 0,
        });
        remaining.forEach((doc, index) => {
          tx.update(doc.ref, {
            position: index + 1,
            updatedAt: FieldValue.serverTimestamp(),
          });
        });
        tx.set(queueControlRef, {
          revision: revision + 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      console.error("finishDriverTravel failed:", error);
      throw new HttpsError("internal", "Hindi matapos ang biyahe. Subukan muli.");
    }

    return { success: true };
  }
);


/**
 * Updates the authenticated driver's editable profile and synchronizes only
 * that driver's active queue display fields in the same server transaction.
 * Plate uniqueness is enforced against current user records, not by a
 * best-effort client-side list query.
 */
exports.updateDriverProfile = onCall(
  { enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kailangan mag-login muna.");
    }

    const phone = request.auth.token.phone_number;
    if (typeof phone !== "string" || !phone) {
      throw new HttpsError("permission-denied", "Walang verified phone number.");
    }

    const data = request.data || {};
    const firstName = typeof data.firstName === "string" ? data.firstName.trim() : null;
    const lastName = typeof data.lastName === "string" ? data.lastName.trim() : null;
    const licenseNumber = typeof data.licenseNumber === "string" ? data.licenseNumber.trim() : null;
    const plateNumber = typeof data.plateNumber === "string" ? data.plateNumber.trim().toUpperCase() : null;
    const emergencyContactName = typeof data.emergencyContactName === "string"
      ? data.emergencyContactName.trim() : null;
    const emergencyContactNumber = typeof data.emergencyContactNumber === "string"
      ? data.emergencyContactNumber.trim() : null;
    const emergencyContact = typeof data.emergencyContact === "string"
      ? data.emergencyContact.trim() : null;

    if ([firstName, lastName, licenseNumber, plateNumber,
         emergencyContactName, emergencyContactNumber, emergencyContact]
        .some((value) => value === null)) {
      throw new HttpsError("invalid-argument", "Kulang o invalid ang profile fields.");
    }

    const normalizedPlate = plateNumber.replace(/[^A-Z0-9]/g, "");
    if (!/^[A-Z]{2,3}[0-9]{1,4}$/.test(normalizedPlate)) {
      throw new HttpsError("invalid-argument", "Invalid ang plate number.");
    }

    if (licenseNumber &&
        (licenseNumber.length > 20 ||
         !/^[A-Za-z0-9 -]+$/.test(licenseNumber) ||
         !/[0-9]/.test(licenseNumber))) {
      throw new HttpsError("invalid-argument", "Invalid ang driver license number.");
    }

    const nameDigits = (emergencyContactName.match(/[0-9]/g) || []).length;
    if (!emergencyContactName ||
        emergencyContactName.length > 50 ||
        nameDigits > 11 ||
        !/[A-Za-zÀ-ÖØ-öø-ÿ]/.test(emergencyContactName) ||
        !/^[A-Za-zÀ-ÖØ-öø-ÿ0-9 .'-]+$/.test(emergencyContactName)) {
      throw new HttpsError("invalid-argument", "Invalid ang pangalan ng emergency contact.");
    }

    if (!/^(09[0-9]{9}|\+639[0-9]{9})$/.test(emergencyContactNumber)) {
      throw new HttpsError("invalid-argument", "Invalid ang emergency contact number.");
    }

    const userRef = db.collection("users").doc(phone);
    const queueControlRef = db.collection("settings").doc("queueControl");

    try {
      await db.runTransaction(async (tx) => {
        const userSnap = await tx.get(userRef);
        if (!userSnap.exists || userSnap.get("role") !== "driver") {
          throw new HttpsError("permission-denied", "Driver account lamang ang maaaring mag-edit ng profile.");
        }

        const driverId = userSnap.get("userId");
        if (typeof driverId !== "string" || !driverId) {
          throw new HttpsError("failed-precondition", "Walang valid na User ID ang account.");
        }

        const drivers = await tx.get(
          db.collection("users").where("role", "==", "driver")
        );
        const duplicatePlate = drivers.docs.some((doc) =>
          doc.id !== phone &&
          String(doc.get("plateNumber") || "")
            .toUpperCase()
            .replace(/[^A-Z0-9]/g, "") === normalizedPlate
        );
        if (duplicatePlate) {
          throw new HttpsError("already-exists", "Ginagamit na ng ibang driver ang plate number na ito.");
        }

        const queueSnapshot = await tx.get(
          db.collection("queue").where("driverId", "==", driverId)
        );
        const control = await tx.get(queueControlRef);
        const revision = control.get("revision") || 0;

        tx.update(userRef, {
          firstName,
          lastName,
          licenseNumber,
          plateNumber,
          emergencyContact,
          emergencyContactName,
          emergencyContactNumber,
          updatedAt: FieldValue.serverTimestamp(),
        });

        const driverName = [firstName, lastName]
          .filter((value) => value)
          .join(" ") || "Driver";

        queueSnapshot.docs.forEach((document) => {
          const status = String(document.get("status") || "").toLowerCase();
          if (QUEUE_STATUSES_INACTIVE.includes(status)) return;
          tx.update(document.ref, {
            driverName,
            plateNumber,
            updatedAt: FieldValue.serverTimestamp(),
          });
        });

        tx.set(queueControlRef, {
          revision: revision + 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      console.error("updateDriverProfile failed:", error);
      throw new HttpsError("internal", "Hindi ma-update ang profile. Subukan muli.");
    }

    return { success: true };
  }
);


/**
 * PRE-OTP LOGIN CREDENTIAL CHECK.
 *
 * This callable intentionally allows unauthenticated requests because it
 * runs BEFORE Firebase Phone Auth. App Check is mandatory, credentials are
 * checked using the Admin SDK (the Android client never receives a hash),
 * and attempts are rate-limited per phone number.
 *
 * The client must request the SMS OTP only when { valid: true } is returned.
 */
const crypto = require("node:crypto");

const LOGIN_CHECK_WINDOW_MS = 15 * 60 * 1000;
const LOGIN_CHECK_MAX_ATTEMPTS = 8;
const LOGIN_HASH_BITS = 256;
const LOGIN_DUMMY_SALT = Buffer.from("enerjeep-login-dummy-salt", "utf8");

function safeEqualBuffers(left, right) {
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function safeEqualText(left, right) {
  const a = Buffer.from(String(left), "utf8");
  const b = Buffer.from(String(right), "utf8");
  return safeEqualBuffers(a, b);
}

function verifyStoredPassword(password, secretData) {
  const saltHex = secretData.passwordSalt;
  const hashHex = secretData.passwordHash;

  if (
    typeof saltHex !== "string" ||
    !/^(?:[0-9a-f]{2}){16,64}$/i.test(saltHex) ||
    typeof hashHex !== "string" ||
    !/^[0-9a-f]{64}$/i.test(hashHex)
  ) {
    // Keep missing/legacy/malformed hash handling reasonably expensive too.
    crypto.pbkdf2Sync(password, LOGIN_DUMMY_SALT, 12000, LOGIN_HASH_BITS / 8, "sha256");
    return false;
  }

  const rawIterations = Number(secretData.iterations);
  const iterations =
    Number.isSafeInteger(rawIterations) &&
    rawIterations >= 1000 &&
    rawIterations <= 1000000
      ? rawIterations
      : 12000;

  const candidate = crypto.pbkdf2Sync(
    password,
    Buffer.from(saltHex, "hex"),
    iterations,
    LOGIN_HASH_BITS / 8,
    "sha256"
  );
  const stored = Buffer.from(hashHex, "hex");
  return safeEqualBuffers(candidate, stored);
}

exports.verifyLoginCredentials = onCall(
  { enforceAppCheck: true, maxInstances: 20 },
  async (request) => {
    const data = request.data || {};
    const phone = typeof data.phoneNumber === "string"
      ? data.phoneNumber.trim()
      : "";
    const password = typeof data.password === "string"
      ? data.password
      : "";

    // Generic invalid-credential result avoids revealing whether an account exists.
    if (
      !/^\\+639[0-9]{9}$/.test(phone) ||
      !password ||
      password.length > 128
    ) {
      return { valid: false };
    }

    // Use a one-way phone digest for the rate-limit document id.
    // No phone number or password is stored in the limiter document.
    const phoneKey = crypto
      .createHash("sha256")
      .update(phone, "utf8")
      .digest("hex");
    const attemptRef = db.collection("_loginPrecheckAttempts").doc(phoneKey);
    const now = Date.now();
    let rateLimited = false;

    await db.runTransaction(async (tx) => {
      const snapshot = await tx.get(attemptRef);
      const storedStart = Number(snapshot.get("windowStartedAt") || 0);
      const storedAttempts = Number(snapshot.get("attempts") || 0);
      const windowExpired =
        !Number.isFinite(storedStart) ||
        now - storedStart >= LOGIN_CHECK_WINDOW_MS ||
        now < storedStart;

      if (windowExpired) {
        tx.set(attemptRef, {
          attempts: 1,
          windowStartedAt: now,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return;
      }

      if (storedAttempts >= LOGIN_CHECK_MAX_ATTEMPTS) {
        rateLimited = true;
        return;
      }

      tx.set(attemptRef, {
        attempts: storedAttempts + 1,
        windowStartedAt: storedStart,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    if (rateLimited) {
      throw new HttpsError(
        "resource-exhausted",
        "Masyadong maraming pagsubok. Maghintay ng 15 minuto bago subukan muli."
      );
    }

    const [userSnapshot, secretSnapshot] = await Promise.all([
      db.collection("users").doc(phone).get(),
      db.collection("userSecrets").doc(phone).get(),
    ]);

    const secretData = secretSnapshot.exists ? secretSnapshot.data() || {} : {};
    let valid = false;

    if (userSnapshot.exists) {
      valid = verifyStoredPassword(password, secretData);

      // Older accounts may still have a legacy plaintext password field.
      // It is migrated and removed only after the OTP succeeds in LoginViewModel.
      if (!valid) {
        const legacyPassword = userSnapshot.get("password");
        if (
          typeof legacyPassword === "string" &&
          legacyPassword.length > 0
        ) {
          // Do a small dummy derivation before the legacy comparison to reduce
          // obvious timing differences between current and legacy accounts.
          crypto.pbkdf2Sync(
            password,
            LOGIN_DUMMY_SALT,
            12000,
            LOGIN_HASH_BITS / 8,
            "sha256"
          );
          valid = safeEqualText(password, legacyPassword);
        }
      }
    } else {
      // Do not make missing accounts substantially cheaper to probe.
      crypto.pbkdf2Sync(
        password,
        LOGIN_DUMMY_SALT,
        12000,
        LOGIN_HASH_BITS / 8,
        "sha256"
      );
    }

    if (valid) {
      // Successful credential checks reset the temporary lockout counter.
      await attemptRef.delete();
    }

    return { valid };
  }
);
