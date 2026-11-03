/**
 * EnerJeep Firestore security-rules tests.
 *
 * Run (needs the Firebase CLI + a local Java runtime for the emulator):
 *
 *     cd firestore-tests && npm install
 *     npx firebase emulators:exec --only firestore \
 *         --project enerjeep-rules-test "npm test" \
 *         -c ../firebase.json
 *
 * Each test locks one rule from ../firestore.rules: the driver
 * "Tapos Na Bumyahe" contract, the queue-jump block, the superadmin-only
 * cut-off hour, immutable audit logs bound to the writer's own phone,
 * the owner-only profile allowlist, and owner-only password secrets.
 */

import { test, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import {
  deleteField,
  doc,
  collection,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";

let testEnv;

const DRIVER_PHONE = "+639171000001";
const ADMIN_PHONE = "+639171000002";
const SUPER_PHONE = "+639171000003";

const driverId = "user-driver-1";

function dbAs(phone, uid) {
  return testEnv.authenticatedContext(uid, { phone_number: phone }).firestore();
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "enerjeep-rules-test",
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `users/${DRIVER_PHONE}`), {
      role: "driver", userId: driverId, plateNumber: "TXA 448",
    });
    await setDoc(doc(db, `users/${ADMIN_PHONE}`), {
      role: "admin", userId: "user-admin-1",
    });
    await setDoc(doc(db, `users/${SUPER_PHONE}`), {
      role: "superadmin", userId: "user-super-1",
    });
    await setDoc(doc(db, "queue/q-drv"), {
      driverId, status: "Nakapila", position: 3, route: "marilao",
    });
    await setDoc(doc(db, "queue/q-oth"), {
      driverId: "user-other-1", status: "Nakapila", position: 4, route: "marilao",
    });
    await setDoc(doc(db, "settings/queueRotation"), {
      cutoffHour: 21, lastQueueDay: "20260101",
    });
    await setDoc(doc(db, "kita/k-drv"), {
      driverId, amount: 100, type: "Kita",
    });
    await setDoc(doc(db, `userSecrets/${DRIVER_PHONE}`), {
      passwordHash: "aa", passwordSalt: "bb", iterations: 210000,
    });
    await setDoc(doc(db, `userSecrets/${ADMIN_PHONE}`), {
      passwordHash: "cc", passwordSalt: "dd", iterations: 210000,
    });
    await setDoc(doc(db, "activityLogs/log-1"), {
      actorName: "Admin", action: "Dispatch", detail: "x",
    });
  });
});

// ---------------------------------------------------------- queue / end-travel

test("driver CAN finish his own travel (status completed + position 0)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-drv"), {
      status: "completed",
      position: 0,
      endTravelAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("driver CANNOT directly rewrite queue display identity fields", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, "queue/q-drv"), {
      driverName: "Juan Dela Cruz",
      plateNumber: "HIJACKED 123",
      updatedAt: new Date(),
    })
  );
});

test("driver CANNOT move his own entry up the queue (queue-jump)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, "queue/q-drv"), {
      position: 1,
      updatedAt: new Date(),
    })
  );
});

test("driver CANNOT renumber another driver's queue entry", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, "queue/q-oth"), {
      position: 2,
      updatedAt: new Date(),
    })
  );
});

test("driver CANNOT change another entry's status or identity fields", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, "queue/q-oth"), {
      status: "completed",
      position: 2,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    updateDoc(doc(db, "queue/q-oth"), {
      driverId: "hijacked",
    })
  );
});

test("admin CAN dispatch-style update any queue entry", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-oth"), {
      status: "dispatched",
      position: 0,
      income: 500,
    })
  );
});

// --------------------------------------------------------------- settings

test("admin CANNOT change the cut-off hour", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(
    updateDoc(doc(db, "settings/queueRotation"), {
      cutoffHour: 22,
      updatedAt: new Date(),
    })
  );
});

test("admin CAN maintain the rotation meta fields (auto-reset engine)", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(
    updateDoc(doc(db, "settings/queueRotation"), {
      lastQueueDay: "20260102",
      updatedAt: new Date(),
    })
  );
});

test("only the superadmin CAN change the cut-off hour", async () => {
  const db = dbAs(SUPER_PHONE, "u-super");
  await assertSucceeds(
    updateDoc(doc(db, "settings/queueRotation"), {
      cutoffHour: 22,
      updatedAt: new Date(),
    })
  );
});

// --------------------------------------------------------------- kita

test("driver CANNOT edit or create his own earnings records", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(updateDoc(doc(db, "kita/k-drv"), { amount: 999999 }));
  await assertFails(
    setDoc(doc(db, "kita/k-new"), { driverId, amount: 999999, type: "Kita" })
  );
});

test("admin CAN manage earnings records", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(updateDoc(doc(db, "kita/k-drv"), { amount: 250 }));
});

// --------------------------------------------------------------- activityLogs

test("driver CAN write his own audit entry (Tapos Na Bumyahe)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(
    setDoc(doc(db, "activityLogs/log-2"), {
      actorName: "Juan", actorPhone: DRIVER_PHONE, actorRole: "Driver",
      action: "Tapos Na Bumyahe", detail: "TXA 448",
    })
  );
});

test("an audit entry must carry the WRITER'S OWN phone (no spoofing)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    setDoc(doc(db, "activityLogs/log-spoof"), {
      actorName: "Maria", actorPhone: ADMIN_PHONE, actorRole: "Admin",
      action: "Nag-dispatch", detail: "pinanggap si admin",
    })
  );
  await assertFails(
    setDoc(doc(db, "activityLogs/log-orph"), {
      actorName: "Juan", actorRole: "Driver",
      action: "Tapos Na Bumyahe", detail: "walang actorPhone",
    })
  );
});

test("audit entries can never be edited or deleted", async () => {
  await assertFails(
    updateDoc(doc(dbAs(ADMIN_PHONE, "u-admin"), "activityLogs/log-1"), {
      detail: "tampered",
    })
  );
});

// --------------------------------------------------------------- secrets & roles

test("a user can read ONLY his own password secret", async () => {
  await assertSucceeds(
    getDoc(doc(dbAs(DRIVER_PHONE, "u-driver"), `userSecrets/${DRIVER_PHONE}`))
  );
  await assertFails(
    getDoc(doc(dbAs(DRIVER_PHONE, "u-driver"), `userSecrets/${ADMIN_PHONE}`))
  );
});

test("unauthenticated clients CANNOT read password secrets before OTP", async () => {
  const anon = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anon, `userSecrets/${DRIVER_PHONE}`)));
  await assertFails(getDocs(collection(anon, "userSecrets")));
  await assertFails(
    setDoc(doc(anon, `userSecrets/${DRIVER_PHONE}`), {
      passwordHash: "x",
      passwordSalt: "y",
      iterations: 210000,
    })
  );
});

test("an admin cannot change roles; the superadmin can", async () => {
  await assertFails(
    updateDoc(doc(dbAs(ADMIN_PHONE, "u-admin"), `users/${DRIVER_PHONE}`), {
      role: "superadmin",
    })
  );
  await assertSucceeds(
    updateDoc(doc(dbAs(SUPER_PHONE, "u-super"), `users/${DRIVER_PHONE}`), {
      role: "admin",
    })
  );
});

test("a user may edit only the profile fields allowed by the current rules", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      firstName: "Renamed",
      lastName: "Driver",
      emergencyContactName: "Inay",
      emergencyContactNumber: "+639171234502",
      emergencyContact: "Inay - +639171234502",
      fcmToken: "device-push-token",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      plateNumber: "TXB 449",
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      licenseNumber: "N01-123456",
    })
  );
});

test("a user can NEVER rewrite his own identity or account-state fields", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      userId: "hijacked",
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      role: "superadmin",
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      status: "Deactivated",
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      isVehicleActive: false,
    })
  );
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      seater: 99,
    })
  );
});

test("a legacy plaintext password field can be REMOVED but never SET", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      password: "Enerjeep@2026",
    })
  );
  await assertSucceeds(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      password: deleteField(),
    })
  );
});

test("only admins can read and manage driver plate claims", async () => {
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(
    setDoc(doc(adminDb, "driverPlateClaims/TXA448"), {
      phoneNumber: DRIVER_PHONE,
      driverId,
    })
  );
  await assertFails(
    getDoc(doc(dbAs(DRIVER_PHONE, "u-driver"), "driverPlateClaims/TXA448"))
  );
  await assertFails(
    setDoc(doc(dbAs(DRIVER_PHONE, "u-driver"), "driverPlateClaims/ABC1234"), {
      phoneNumber: DRIVER_PHONE,
      driverId,
    })
  );
});

test("a member cannot self-create his own users doc (role escape closed)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    setDoc(doc(db, `users/${DRIVER_PHONE}`), {
      role: "superadmin", userId: driverId, plateNumber: "TXA 448",
    })
  );
});
