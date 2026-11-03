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

const { onDocumentDeleted, onDocumentUpdated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

exports.deleteAuthUserOnAccountDelete = onDocumentDeleted(
  { document: "users/{phone}", region: "asia-east2", retry: true },
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

/** Resolve the signed-in administrator and enforce active account status. */
async function getAdminContext(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Kailangan mag-login muna.");
  }
  const phone = request.auth.token.phone_number;
  if (typeof phone !== "string" || !phone) {
    throw new HttpsError("permission-denied", "Walang verified phone number.");
  }
  const user = await db.collection("users").doc(phone).get();
  const role = user.get("role");
  if (!user.exists ||
      !["admin", "superadmin"].includes(role) ||
      String(user.get("status") || "active").toLowerCase() !== "active") {
    throw new HttpsError("permission-denied", "Aktibong administrator account lamang ang maaaring gumawa nito.");
  }
  return {
    phone,
    role,
    station: String(user.get("adminStation") || "").trim().toLowerCase(),
  };
}

function queueRouteStation(route) {
  const value = typeof route === "string" ? route.trim().toLowerCase() : "";
  if (value.startsWith("marilao")) return "marilao";
  if (value.startsWith("monumento")) return "monumento";
  return null;
}

function canManageQueueRoute(adminContext, route) {
  if (adminContext.role === "superadmin") return true;
  const station = queueRouteStation(route);
  return adminContext.role === "admin" &&
    station !== null &&
    adminContext.station === station;
}

function queueStatusIsActive(data) {
  return !QUEUE_STATUSES_INACTIVE.includes(
    String(data.status || "").toLowerCase()
  );
}

function queueTimestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return Number.MAX_SAFE_INTEGER;
}

function compareQueueEntries(left, right) {
  const cancelledLeft = left.get("isCancelled") === true ? 1 : 0;
  const cancelledRight = right.get("isCancelled") === true ? 1 : 0;
  return cancelledLeft - cancelledRight ||
    (left.get("position") ?? Number.MAX_SAFE_INTEGER) -
      (right.get("position") ?? Number.MAX_SAFE_INTEGER) ||
    queueTimestampMillis(left.get("createdAt")) -
      queueTimestampMillis(right.get("createdAt"));
}

function manilaDateKey(value) {
  if (!value) return "";
  const date = typeof value.toDate === "function" ? value.toDate() : new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Manila",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const byType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return byType.year + byType.month + byType.day;
}


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
  { schedule: "0 * * * *", timeZone: "Asia/Manila", region: "us-central1" },
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
/**
 * Every notification is stored under the recipient's own user document.
 * The deterministic document ID makes Firestore trigger retries idempotent.
 */
async function createUserNotification(userRef, notificationId, payload) {
  const notificationRef = userRef.collection("notifications").doc(notificationId);
  try {
    await notificationRef.create({
      ...payload,
      read: false,
      createdAt: FieldValue.serverTimestamp(),
    });
    return true;
  } catch (error) {
    if (error && (error.code === 6 || error.code === "already-exists")) {
      return false;
    }
    throw error;
  }
}

function isActiveEnerJeepMember(userDocument) {
  const role = String(userDocument.get("role") || "").toLowerCase();
  return ["admin", "superadmin", "driver"].includes(role) &&
    String(userDocument.get("status") || "active").toLowerCase() === "active";
}

async function sendUserNotificationPush(userDocument, title, body, data, channelId) {
  const token = userDocument.get("fcmToken");
  if (typeof token !== "string" || token.length === 0) return;

  try {
    await admin.messaging().send({
      token,
      notification: { title, body },
      data,
      android: {
        priority: "high",
        notification: {
          channel_id: channelId,
          sound: "default",
        },
      },
    });
  } catch (error) {
    const code = error && error.code;
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token" ||
      code === "messaging/invalid-argument"
    ) {
      await userDocument.ref.update({ fcmToken: FieldValue.delete() });
      console.log("Inalis ang expired FCM token:", userDocument.id);
    } else {
      console.error("Hindi naipadala ang notification push sa", userDocument.id, error);
    }
  }
}

/**
 * Kapag may bagong dispatch, nagse-save ng notification para sa lahat ng
 * active account roles. Ang mismong driver ay makakakita ng "Na-dispatch ka!";
 * makikita naman ng admin at superadmin kung sinong driver ang na-dispatch.
 */
exports.onQueueDispatched = onDocumentUpdated(
  { document: "queue/{entryId}", region: "asia-east2" },
  async (event) => {
    const before = event.data.before.data() || {};
    const after = event.data.after.data() || {};

    if (String(after.status || "").toLowerCase() !== "dispatched") return null;
    if (String(before.status || "").toLowerCase() === "dispatched") return null;

    const driverId = String(after.driverId || "");
    if (!driverId) return null;

    const memberSnapshot = await db.collection("users").get();
    const activeMembers = memberSnapshot.docs.filter(isActiveEnerJeepMember);
    if (activeMembers.length === 0) return null;

    const entryId = String(event.params.entryId);
    const driverName = String(after.driverName || "Driver");
    const plate = String(after.plateNumber || "");
    const route = String(after.route || "");
    const details = [driverName, plate, route]
      .filter((part) => part.trim().length > 0)
      .join(" • ")
      .slice(0, 180);

    const createdRecipients = [];
    for (const userDocument of activeMembers) {
      const isDispatchedDriver =
        String(userDocument.get("userId") || "") === driverId &&
        String(userDocument.get("role") || "").toLowerCase() === "driver";
      const title = isDispatchedDriver
        ? "Na-dispatch ka!"
        : "May Na-dispatch na Driver";
      const body = details || "May bagong driver na na-dispatch sa pila.";
      const notificationId = "dispatch_" + entryId;
      const created = await createUserNotification(userDocument.ref, notificationId, {
        type: "dispatch",
        title,
        body,
        entityId: entryId,
        driverId,
        driverName,
        plateNumber: plate,
        route,
      });
      if (created) {
        createdRecipients.push({
          userDocument,
          title,
          body,
          data: {
            type: "dispatch",
            entryId,
            driverId,
            driverName,
            plateNumber: plate,
            route,
          },
        });
      }
    }

    await Promise.all(createdRecipients.map((recipient) =>
      sendUserNotificationPush(
        recipient.userDocument,
        recipient.title,
        recipient.body,
        recipient.data,
        "enerjeep_dispatch"
      )
    ));

    console.log(
      "onQueueDispatched: na-save ang abiso sa " +
      createdRecipients.length + " active account(s)."
    );
    return null;
  }
);

/**
 * Bawat bagong incident report ay ipinapaalam sa lahat ng active roles,
 * kahit sentToDriver=false at kahit resolved na ang initial status.
 * Kapag nag-transition mula unresolved tungo sa resolved, nagpapadala rin
 * ng isang hiwalay na status update; ordinaryong edits ay hindi duplicate.
 */
exports.onIncidentShared = onDocumentWritten(
  { document: "incidents/{incidentId}", region: "asia-east2" },
  async (event) => {
    const beforeSnapshot = event.data.before;
    const afterSnapshot = event.data.after;
    if (!afterSnapshot.exists) return null;

    const before = beforeSnapshot.exists ? (beforeSnapshot.data() || {}) : {};
    const incident = afterSnapshot.data() || {};

    // Remove the legacy phone field when an incident is touched.
    if (Object.prototype.hasOwnProperty.call(incident, "driverPhone")) {
      await afterSnapshot.ref.update({
        driverPhone: FieldValue.delete(),
      });
    }

    const isResolved = incident.isResolved === true ||
      String(incident.status || "").toLowerCase() === "resolved";
    const wasResolved = before.isResolved === true ||
      String(before.status || "").toLowerCase() === "resolved";
    const isNewIncident = !beforeSnapshot.exists;
    const becameResolved = beforeSnapshot.exists && !wasResolved && isResolved;

    if (!isNewIncident && !becameResolved) return null;

    const incidentId = String(event.params.incidentId);
    const notificationId = isNewIncident
      ? "incident_" + incidentId
      : "incident_" + incidentId + "_resolved";
    const statusLabel = isResolved ? "Naayos" : "Hindi pa naayos";
    const title = isNewIncident
      ? (isResolved ? "Bagong Insidente • Naayos" : "Bagong Insidente sa Kooperatiba")
      : "Naayos na ang Insidente";
    const plate = String(incident.plateNumber || "");
    const incidentTitle = String(incident.title || "Insidente");
    const location = String(incident.location || "");
    const body = [plate, incidentTitle, location, statusLabel]
      .filter((part) => part.trim().length > 0)
      .join(" • ")
      .slice(0, 180);

    const memberSnapshot = await db.collection("users").get();
    const activeMembers = memberSnapshot.docs.filter(isActiveEnerJeepMember);
    const createdRecipients = [];

    for (const userDocument of activeMembers) {
      const created = await createUserNotification(userDocument.ref, notificationId, {
        type: "incident",
        title,
        body,
        entityId: incidentId,
        incidentId,
        driverId: String(incident.driverId || ""),
        plateNumber: plate,
        incidentTitle,
        location,
        status: isResolved ? "resolved" : "open",
        statusLabel,
      });
      if (created) {
        createdRecipients.push(userDocument);
      }
    }

    const data = {
      type: "incident",
      incidentId,
      status: isResolved ? "resolved" : "open",
      statusLabel,
      plateNumber: plate,
      incidentTitle,
      location,
    };

    await Promise.all(createdRecipients.map((userDocument) =>
      sendUserNotificationPush(
        userDocument,
        title,
        body || "May bagong ulat ng insidente sa EnerJeep.",
        data,
        "enerjeep_incident"
      )
    ));

    console.log(
      "onIncidentShared: na-save ang abiso sa " +
      createdRecipients.length + " active account(s)."
    );
    return null;
  }
);

/**
 * Deletes an account and its active queue entries through the trusted Admin SDK.
 * The server can clean up both routes while client queue writes stay station-restricted.
 */
exports.deleteAccount = onCall(
  { enforceAppCheck: true, region: "us-central1" },
  async (request) => {
    const caller = await getAdminContext(request);
    const targetPhone = request.data && request.data.phoneNumber;
    if (typeof targetPhone !== "string" ||
        !/^\+639[0-9]{9}$/.test(targetPhone) ||
        targetPhone === caller.phone) {
      throw new HttpsError("invalid-argument", "Invalid na account na buburahin.");
    }

    const callerRef = db.collection("users").doc(caller.phone);
    const userRef = db.collection("users").doc(targetPhone);
    const secretRef = db.collection("userSecrets").doc(targetPhone);
    const queueControlRef = db.collection("settings").doc("queueControl");
    const queueCollection = db.collection("queue");

    try {
      await db.runTransaction(async (tx) => {
        const [callerSnapshot, target, control, queueSnapshot] = await Promise.all([
          tx.get(callerRef),
          tx.get(userRef),
          tx.get(queueControlRef),
          tx.get(queueCollection),
        ]);

        const currentCallerRole = String(callerSnapshot.get("role") || "").toLowerCase();
        if (!callerSnapshot.exists ||
            !["admin", "superadmin"].includes(currentCallerRole) ||
            String(callerSnapshot.get("status") || "active").toLowerCase() !== "active") {
          throw new HttpsError("permission-denied", "Wala nang aktibong pahintulot ang account na ito.");
        }

        if (!target.exists) {
          throw new HttpsError("not-found", "Hindi na makita ang account.");
        }

        const targetRole = String(target.get("role") || "").toLowerCase();
        if (targetRole === "superadmin" ||
            (targetRole === "admin" && currentCallerRole !== "superadmin") ||
            !["driver", "admin"].includes(targetRole)) {
          throw new HttpsError("permission-denied", "Walang pahintulot na burahin ang account na ito.");
        }

        // A station admin must not remove an active driver from the other
        // terminal as a side effect of account deletion.
        if (targetRole === "driver" && currentCallerRole === "admin") {
          const targetDriverId = String(target.get("userId") || targetPhone);
          const hasForeignActiveQueueEntry = queueSnapshot.docs.some((document) =>
            document.get("driverId") === targetDriverId &&
            queueStatusIsActive(document.data()) &&
            queueRouteStation(document.get("route")) !== caller.station
          );
          if (hasForeignActiveQueueEntry) {
            throw new HttpsError(
              "permission-denied",
              "Ang driver na ito ay kasalukuyang nasa pila ng kabilang terminal."
            );
          }
        }

        let plateClaimRef = null;
        let plateClaim = null;
        if (targetRole === "driver") {
          const normalizedPlate = String(target.get("plateNumber") || "")
            .toUpperCase()
            .replace(/[^A-Z0-9]/g, "");
          if (normalizedPlate) {
            plateClaimRef = db.collection("driverPlateClaims").doc(normalizedPlate);
            plateClaim = await tx.get(plateClaimRef);
          }
        }

        const queueDocs = queueSnapshot.docs;
        if (targetRole === "driver") {
          const driverId = String(target.get("userId") || targetPhone);
          const entriesToDelete = queueDocs.filter((document) =>
            document.get("driverId") === driverId &&
            queueStatusIsActive(document.data())
          );
          const deletedIds = new Set(entriesToDelete.map((document) => document.id));
          entriesToDelete.forEach((document) => tx.delete(document.ref));

          const remainingByRoute = queueDocs
            .filter((document) =>
              !deletedIds.has(document.id) &&
              queueStatusIsActive(document.data())
            )
            .reduce((groups, document) => {
              const route = String(document.get("route") || "");
              if (!groups.has(route)) groups.set(route, []);
              groups.get(route).push(document);
              return groups;
            }, new Map());

          remainingByRoute.forEach((routeEntries) => {
            routeEntries.sort(compareQueueEntries).forEach((document, index) => {
              tx.update(document.ref, {
                position: index + 1,
                updatedAt: FieldValue.serverTimestamp(),
              });
            });
          });
        }

        tx.delete(userRef);
        tx.delete(secretRef);
        if (plateClaimRef && plateClaim &&
            plateClaim.get("phoneNumber") === targetPhone) {
          tx.delete(plateClaimRef);
        }
        tx.set(queueControlRef, {
          revision: (control.get("revision") || 0) + 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      console.error("deleteAccount failed:", error);
      throw new HttpsError("internal", "Hindi mabura ang account. Subukan muli.");
    }

    return { success: true };
  }
);

/**
 * Dispatches from the administrator's assigned terminal. The trusted
 * transaction updates both route positions and the dispatch ledger together.
 */
exports.dispatchQueueVehicle = onCall(
  { enforceAppCheck: true, region: "us-central1" },
  async (request) => {
    const caller = await getAdminContext(request);
    const data = request.data || {};
    const entryId = data.queueEntryId;
    const income = data.income;
    const otherFees = data.otherFees;

    if (typeof entryId !== "string" || !entryId || entryId.length > 180 ||
        typeof income !== "number" || !Number.isFinite(income) ||
        typeof otherFees !== "number" || !Number.isFinite(otherFees) ||
        income < 0 || otherFees < 0 || otherFees > income) {
      throw new HttpsError("invalid-argument", "Invalid ang detalye ng dispatch.");
    }

    const queueCollection = db.collection("queue");
    const queueControlRef = db.collection("settings").doc("queueControl");
    const tiersRef = db.collection("settings").doc("deductionTiers");

    try {
      await db.runTransaction(async (tx) => {
        const callerRef = db.collection("users").doc(caller.phone);
        const [callerSnapshot, control, queueSnapshot] = await Promise.all([
          tx.get(callerRef),
          tx.get(queueControlRef),
          tx.get(queueCollection),
        ]);
        const currentCallerRole = String(callerSnapshot.get("role") || "").toLowerCase();
        const transactionCaller = {
          role: currentCallerRole,
          station: String(callerSnapshot.get("adminStation") || "").trim().toLowerCase(),
        };
        if (!callerSnapshot.exists ||
            !["admin", "superadmin"].includes(currentCallerRole) ||
            String(callerSnapshot.get("status") || "active").toLowerCase() !== "active") {
          throw new HttpsError("permission-denied", "Wala nang aktibong pahintulot ang account na ito.");
        }
        const target = queueSnapshot.docs.find((document) => document.id === entryId);

        if (!target || !queueStatusIsActive(target.data()) ||
            target.get("isCancelled") === true) {
          throw new HttpsError("failed-precondition", "Wala na sa aktibong pila ang sasakyang ito.");
        }

        const currentRoute = String(target.get("route") || "");
        const currentStation = queueRouteStation(currentRoute);
        if (!currentStation || !canManageQueueRoute(transactionCaller, currentRoute)) {
          throw new HttpsError("permission-denied", "Sa terminal na nakatalaga lamang maaaring mag-dispatch.");
        }

        const stationQueue = queueSnapshot.docs
          .filter((document) =>
            queueStatusIsActive(document.data()) &&
            (queueRouteStation(document.get("route")) || "monumento") === currentStation
          )
          .sort(compareQueueEntries);
        const frontOfLine = stationQueue.find((document) =>
          document.get("isCancelled") !== true
        );
        if (frontOfLine && frontOfLine.id !== entryId) {
          throw new HttpsError("failed-precondition", "Ang nasa unahan lamang ng pila ang maaaring i-dispatch.");
        }

        const driverId = String(target.get("driverId") || "").trim();
        if (!driverId) {
          throw new HttpsError("failed-precondition", "Walang User ID ang driver na ito.");
        }

        const driverQuery = db.collection("users")
          .where("userId", "==", driverId)
          .where("role", "==", "driver")
          .limit(1);
        const [driverSnapshot, tiersSnapshot] = await Promise.all([
          tx.get(driverQuery),
          tx.get(tiersRef),
        ]);
        if (driverSnapshot.empty) {
          throw new HttpsError("not-found", "Hindi makita ang driver account.");
        }

        const driver = driverSnapshot.docs[0];
        if (driver.get("isVehicleActive") === false ||
            String(driver.get("status") || "active").toLowerCase() === "inactive") {
          throw new HttpsError("failed-precondition", "Hindi aktibo ang driver o jeepney na ito.");
        }

        const todayKey = manilaDateKey(new Date());
        const doneForToday = queueSnapshot.docs.some((document) =>
          document.get("driverId") === driverId &&
          String(document.get("status") || "").toLowerCase() === "completed" &&
          manilaDateKey(document.get("endTravelAt")) === todayKey
        );
        if (doneForToday) {
          throw new HttpsError(
            "failed-precondition",
            "Tapos na bumyahe ang driver na ito ngayong araw. Hindi na siya pwedeng i-dispatch."
          );
        }

        const driverName = String(target.get("driverName") || "");
        const plateNumber = String(target.get("plateNumber") || "").trim().toUpperCase();
        const netAmount = income - otherFees;
        const seater = Math.max(0, Math.trunc(Number(driver.get("seater") || 0)));
        const tiers = tiersSnapshot.data() || {};
        const configuredTier = seater > 0 ? Number(tiers[String(seater)]) : Number.NaN;
        const tierDeduction = seater <= 0
          ? 0
          : Number.isFinite(configuredTier) && configuredTier >= 0
            ? configuredTier
            : seater * 2;

        // The cancelled entries remain visible at the end of their terminal's list.
        const remainingActive = queueSnapshot.docs.filter((document) =>
          document.id !== entryId && queueStatusIsActive(document.data())
        );
        const sameRouteRemaining = remainingActive
          .filter((document) =>
            (queueRouteStation(document.get("route")) || "monumento") === currentStation
          )
          .sort(compareQueueEntries);
        const oppositeRouteRemaining = remainingActive
          .filter((document) =>
            (queueRouteStation(document.get("route")) || "monumento") !== currentStation
          )
          .sort(compareQueueEntries);
        const alreadyQueuedAfterDispatch = remainingActive.some((document) =>
          document.get("driverId") === driverId
        );
        const shouldRequeue = driver.get("isVehicleActive") !== false &&
          !alreadyQueuedAfterDispatch;

        tx.update(target.ref, {
          status: "dispatched",
          dispatchedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
          position: 0,
        });
        sameRouteRemaining.forEach((document, index) => {
          tx.update(document.ref, {
            position: index + 1,
            updatedAt: FieldValue.serverTimestamp(),
          });
        });
        oppositeRouteRemaining.forEach((document, index) => {
          tx.update(document.ref, {
            position: index + 2,
            updatedAt: FieldValue.serverTimestamp(),
          });
        });

        if (shouldRequeue) {
          const oppositeRoute = currentStation === "marilao"
            ? "Monumento – Marilao"
            : "Marilao – Monumento";
          const requeueId = "queue_" + driverId + "_after_" + entryId;
          tx.set(queueCollection.doc(requeueId), {
            id: requeueId,
            driverId,
            driverName,
            plateNumber,
            route: oppositeRoute,
            position: 1,
            status: "Nakapila",
            time: new Intl.DateTimeFormat("en-US", {
              timeZone: "Asia/Manila",
              hour: "2-digit",
              minute: "2-digit",
            }).format(new Date()),
            createdAt: FieldValue.serverTimestamp(),
          });
        }

        const ledgerId = entryId + "_dispatch";
        tx.set(db.collection("ledger").doc(ledgerId), {
          id: ledgerId,
          driverId,
          driverName,
          plateNumber,
          queueId: entryId,
          date: new Intl.DateTimeFormat("en-US", {
            timeZone: "Asia/Manila",
            month: "short",
            day: "2-digit",
            year: "numeric",
          }).format(new Date()),
          amount: netAmount,
          grossAmount: income,
          otherFees,
          netAmount,
          seater,
          seaterDeduction: tierDeduction,
          deductionCustomized: otherFees !== tierDeduction,
          feeType: "Dispatch",
          status: "Bayad",
          source: "queue_dispatch",
          createdAt: FieldValue.serverTimestamp(),
        });

        tx.set(queueControlRef, {
          revision: (control.get("revision") || 0) + 1,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      console.error("dispatchQueueVehicle failed:", error);
      throw new HttpsError("internal", "Hindi ma-dispatch ang sasakyan. Subukan muli.");
    }

    return { success: true };
  }
);

/**
 * Returns a driver's phone number only when an authenticated driver requests
 * a shared incident. Phone numbers are not stored in member-readable incident
 * documents and the callable is protected by App Check.
 */
exports.getIncidentDriverPhone = onCall(
  { enforceAppCheck: true, region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kailangan mag-login muna.");
    }

    const callerPhone = request.auth.token.phone_number;
    if (typeof callerPhone !== "string" || !callerPhone) {
      throw new HttpsError("permission-denied", "Walang verified phone number.");
    }

    const caller = await db.collection("users").doc(callerPhone).get();
    if (!caller.exists || caller.get("role") !== "driver") {
      throw new HttpsError("permission-denied", "Driver account lamang ang maaaring gumamit ng contact lookup.");
    }

    const incidentId = request.data && request.data.incidentId;
    if (typeof incidentId !== "string" || !incidentId || incidentId.length > 180) {
      throw new HttpsError("invalid-argument", "Invalid incident ID.");
    }

    const incident = await db.collection("incidents").doc(incidentId).get();
    if (!incident.exists || incident.get("sentToDriver") !== true) {
      throw new HttpsError("not-found", "Hindi makita ang ibinahaging insidente.");
    }

    const driverId = incident.get("driverId");
    if (typeof driverId !== "string" || !driverId) {
      throw new HttpsError("failed-precondition", "Walang naka-link na driver ang insidente.");
    }

    const driver = await db.collection("users")
      .where("userId", "==", driverId)
      .where("role", "==", "driver")
      .limit(1)
      .get();

    if (driver.empty) {
      throw new HttpsError("not-found", "Hindi makita ang driver account.");
    }

    const driverDoc = driver.docs[0];
    const phoneNumber = driverDoc.get("phoneNumber") || driverDoc.id;
    if (typeof phoneNumber !== "string" || !/^\+639[0-9]{9}$/.test(phoneNumber)) {
      throw new HttpsError("failed-precondition", "Walang valid na numero ang driver.");
    }

    return { phoneNumber };
  }
);


/**
 * Completes the authenticated driver's own queue entry. The server verifies
 * the Phone Auth identity and changes the entry and route positions in one
 * Admin SDK transaction; clients cannot reorder another driver's queue row.
 */
exports.finishDriverTravel = onCall(
  { enforceAppCheck: true, region: "us-central1" },
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
  { enforceAppCheck: true, region: "us-central1" },
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

        const oldNormalizedPlate = String(userSnap.get("plateNumber") || "")
          .toUpperCase()
          .replace(/[^A-Z0-9]/g, "");
        const claimCollection = db.collection("driverPlateClaims");
        const newClaimRef = claimCollection.doc(normalizedPlate);
        const oldClaimRef = oldNormalizedPlate
          ? claimCollection.doc(oldNormalizedPlate)
          : null;
        const newClaim = await tx.get(newClaimRef);
        const oldClaim = oldClaimRef && oldNormalizedPlate !== normalizedPlate
          ? await tx.get(oldClaimRef)
          : newClaim;

        if (newClaim.exists && newClaim.get("phoneNumber") !== phone) {
          throw new HttpsError("already-exists", "May nakareserba nang ibang account sa plate number na ito.");
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

        if (
          oldNormalizedPlate &&
          oldNormalizedPlate !== normalizedPlate &&
          oldClaim.exists &&
          oldClaim.get("phoneNumber") === phone
        ) {
          tx.delete(oldClaimRef);
        }
        tx.set(newClaimRef, {
          phoneNumber: phone,
          driverId,
          createdAt: newClaim.exists
            ? (newClaim.get("createdAt") || FieldValue.serverTimestamp())
            : FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });

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
  { enforceAppCheck: true, maxInstances: 20, region: "us-central1" },
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
      !/^\+639[0-9]{9}$/.test(phone) ||
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
