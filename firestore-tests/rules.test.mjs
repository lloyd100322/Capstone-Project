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
  writeBatch,
  doc,
  collection,
  getDoc,
  getDocs,
  deleteDoc,
  query,
  where,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";

let testEnv;

const DRIVER_PHONE = "+639171000001";
const ADMIN_PHONE = "+639171000002";
const SUPER_PHONE = "+639171000003";
const MONUMENTO_ADMIN_PHONE = "+639171000004";

const driverId = "user-driver-1";

function dbAs(phone, uid) {
  return testEnv.authenticatedContext(uid, {
    phone_number: phone,
    auth_time: 1700000000,
  }).firestore();
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
      role: "driver", status: "active", userId: driverId, plateNumber: "TXA 448",
    });
    await setDoc(doc(db, `users/${ADMIN_PHONE}`), {
      role: "admin", status: "active", adminStation: "marilao", userId: "user-admin-1",
    });
    await setDoc(doc(db, `users/${MONUMENTO_ADMIN_PHONE}`), {
      role: "admin", status: "active", adminStation: "monumento", userId: "user-admin-2",
    });
    await setDoc(doc(db, `users/${SUPER_PHONE}`), {
      role: "superadmin", status: "active", userId: "user-super-1",
    });
    for (const [phone, uid] of [
      [DRIVER_PHONE, "u-driver"],
      [ADMIN_PHONE, "u-admin"],
      [MONUMENTO_ADMIN_PHONE, "u-monumento-admin"],
      [SUPER_PHONE, "u-super"],
    ]) {
      await setDoc(doc(db, `secureSessions/${uid}`), {
        phoneNumber: phone,
        authTime: 1700000000,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
      });
    }
    await setDoc(doc(db, "queue/q-drv"), {
      driverId, status: "Nakapila", position: 3, route: "marilao",
    });
    await setDoc(doc(db, "queue/q-oth"), {
      driverId: "user-other-1", status: "Nakapila", position: 4, route: "marilao",
    });
    await setDoc(doc(db, "queue/q-monument"), {
      driverId: "user-monument-1", status: "Nakapila", position: 1,
      route: "Monumento – Marilao",
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
    await setDoc(doc(db, "incidents/shared-1"), { title: "Shared incident", sentToDriver: true, isResolved: false });
    await setDoc(doc(db, `users/${DRIVER_PHONE}/notifications/dispatch_sample`), {
      type: "dispatch", title: "Na-dispatch ka!", body: "TXA 448", read: false,
    });
    await setDoc(doc(db, `users/${ADMIN_PHONE}/notifications/incident_sample`), {
      type: "incident", title: "Bagong Insidente", body: "TXA 448", read: false,
    });
    await setDoc(doc(db, "incidents/draft-1"), { title: "Admin draft", sentToDriver: false, isResolved: false });
    await setDoc(doc(db, "ledger/dispatch-driver"), { driverId, amount: 450, grossAmount: 500, otherFees: 50, netAmount: 450 });
    await setDoc(doc(db, "ledger/dispatch-other"), { driverId: "user-other-1", amount: 600, grossAmount: 650, otherFees: 50, netAmount: 600 });
  });
});

test("OTP-only Firebase sessions cannot access protected application data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await deleteDoc(doc(ctx.firestore(), "secureSessions/u-driver"));
  });
  const db = dbAs(DRIVER_PHONE, "u-driver");

  // The phone owner may read only their own basic profile during recovery.
  await assertSucceeds(getDoc(doc(db, `users/${DRIVER_PHONE}`)));
  await assertFails(getDocs(collection(db, "queue")));
  await assertFails(getDoc(doc(db, `userSecrets/${DRIVER_PHONE}`)));
  await assertFails(getDoc(doc(db, "ledger/dispatch-driver")));
  await assertFails(getDoc(doc(db, "incidents/shared-1")));
  await assertFails(getDocs(collection(db, `users/${DRIVER_PHONE}/notifications`)));
});

test("clients cannot directly delete account documents", async () => {
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  const superDb = dbAs(SUPER_PHONE, "u-super");
  await assertFails(deleteDoc(doc(adminDb, `users/${DRIVER_PHONE}`)));
  await assertFails(deleteDoc(doc(superDb, `users/${DRIVER_PHONE}`)));
});

test("OTP-only admin sessions cannot manage station queues", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await deleteDoc(doc(ctx.firestore(), "secureSessions/u-admin"));
  });
  const db = dbAs(ADMIN_PHONE, "u-admin");

  await assertFails(getDocs(collection(db, "queue")));
  await assertFails(
    setDoc(doc(db, "queue/q-otp-admin"), {
      id: "q-otp-admin",
      driverId: "user-otp-admin",
      plateNumber: "TXB 449",
      driverName: "OTP Admin Test",
      route: "Marilao – Monumento",
      position: 5,
      status: "Nakapila",
      time: "10:00 AM",
      createdAt: new Date(),
    })
  );
  await assertFails(updateDoc(doc(db, "queue/q-drv"), {
    position: 4,
  }));
});

test("admins cannot directly dispatch or rewrite protected queue fields", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(updateDoc(doc(db, "queue/q-drv"), {
    status: "dispatched",
    position: 0,
    income: 500,
  }));
  await assertFails(updateDoc(doc(db, "queue/q-drv"), {
    driverId: "spoofed-driver",
  }));
});

test("admins cannot directly create, update, or delete ledger records", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(setDoc(doc(db, "ledger/manual-fake"), {
    driverId,
    amount: 50000,
    source: "ledger",
  }));
  await assertFails(updateDoc(doc(db, "ledger/dispatch-driver"), {
    amount: 999999,
  }));
  await assertFails(deleteDoc(doc(db, "ledger/dispatch-driver")));
});

// ---------------------------------------------------------- queue / end-travel

test("driver CANNOT directly finish queue travel; the trusted callable owns completion", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, "queue/q-drv"), {
      status: "completed", position: 0,
      endTravelAt: serverTimestamp(), updatedAt: serverTimestamp(),
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

test("Marilao admin can manage its own queue but can only read Monumento", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");

  await assertFails(
    updateDoc(doc(db, "queue/q-oth"), {
      status: "dispatched",
      position: 0,
      income: 500,
    })
  );
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-oth"), {
      position: 5,
      updatedAt: new Date(),
    })
  );

  // The whole queue remains readable, including the other terminal.
  await assertSucceeds(getDocs(collection(db, "queue")));
  await assertSucceeds(getDoc(doc(db, "queue/q-monument")));

  // New entries may be added only to the administrator's own terminal.
  await assertSucceeds(
    setDoc(doc(db, "queue/q-created-own-terminal"), {
      driverId: "user-new-own",
      status: "Nakapila",
      position: 5,
      route: "Marilao – Monumento",
    })
  );
  await assertFails(
    updateDoc(doc(db, "queue/q-monument"), {
      status: "dispatched",
      position: 0,
    })
  );
  await assertFails(
    updateDoc(doc(db, "queue/q-monument"), { isCancelled: true })
  );
  await assertFails(
    updateDoc(doc(db, "queue/q-drv"), { route: "Monumento – Marilao" })
  );
  await assertFails(deleteDoc(doc(db, "queue/q-monument")));
  await assertFails(
    setDoc(doc(db, "queue/q-created-on-other-terminal"), {
      driverId: "user-new",
      status: "Nakapila",
      position: 2,
      route: "Monumento – Marilao",
    })
  );
});

test("Monumento admin can manage Monumento but cannot modify Marilao", async () => {
  const db = dbAs(MONUMENTO_ADMIN_PHONE, "u-monumento-admin");
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-monument"), {
      position: 2,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    updateDoc(doc(db, "queue/q-drv"), {
      status: "dispatched",
      position: 0,
    })
  );
});

test("superadmin can manage both terminal queues", async () => {
  const db = dbAs(SUPER_PHONE, "u-super");
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-monument"), { position: 3 })
  );
  await assertSucceeds(
    updateDoc(doc(db, "queue/q-drv"), { position: 2 })
  );
});

test("only superadmin can normalize a legacy queue entry with no route", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "queue/q-legacy"), {
      driverId: "legacy-driver",
      status: "Nakapila",
      position: 99,
    });
  });

  const superDb = dbAs(SUPER_PHONE, "u-super");
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");

  await assertFails(
    updateDoc(doc(adminDb, "queue/q-legacy"), {
      route: "Marilao – Monumento",
      position: 6,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    updateDoc(doc(superDb, "queue/q-legacy"), {
      route: "Marilao – Monumento",
      position: 6,
      updatedAt: new Date(),
    })
  );

  await assertFails(
    updateDoc(doc(superDb, "queue/q-legacy"), {
      status: "dispatched",
      position: 0,
    })
  );
});

test("deactivated station admin cannot modify a queue with an old session", async () => {
  const superDb = dbAs(SUPER_PHONE, "u-super");
  await assertSucceeds(
    updateDoc(doc(superDb, `users/${ADMIN_PHONE}`), {
      status: "inactive",
    })
  );

  const oldAdminSession = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(
    updateDoc(doc(oldAdminSession, "queue/q-drv"), {
      position: 6,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    setDoc(doc(oldAdminSession, "settings/queueControl"), {
      revision: 1,
    })
  );
});

// ---------------------------------------------------------- incident sharing

test("drivers can read only incidents explicitly shared with drivers", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(getDoc(doc(db, "incidents/shared-1")));
  await assertFails(getDoc(doc(db, "incidents/draft-1")));
  await assertSucceeds(
    getDocs(query(collection(db, "incidents"), where("sentToDriver", "==", true)))
  );
  await assertFails(getDocs(collection(db, "incidents")));

  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(getDoc(doc(adminDb, "incidents/draft-1")));
});

// ------------------------------------------------------- notification privacy

test("each active role can read only its own notification center", async () => {
  const driverDb = dbAs(DRIVER_PHONE, "u-driver");
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  const superDb = dbAs(SUPER_PHONE, "u-super");

  await assertSucceeds(getDocs(collection(driverDb, `users/${DRIVER_PHONE}/notifications`)));
  await assertFails(getDocs(collection(driverDb, `users/${ADMIN_PHONE}/notifications`)));
  await assertSucceeds(getDocs(collection(adminDb, `users/${ADMIN_PHONE}/notifications`)));
  await assertSucceeds(getDocs(collection(superDb, `users/${SUPER_PHONE}/notifications`)));
});

test("members can mark their own notifications read but cannot create or rewrite them", async () => {
  const driverDb = dbAs(DRIVER_PHONE, "u-driver");
  const ownNotification = doc(driverDb, `users/${DRIVER_PHONE}/notifications/dispatch_sample`);

  await assertSucceeds(updateDoc(ownNotification, {
    read: true,
    readAt: new Date(),
  }));
  await assertFails(updateDoc(ownNotification, {
    title: "Tampered notification",
  }));
  await assertFails(setDoc(doc(driverDb, `users/${DRIVER_PHONE}/notifications/fake`), {
    type: "incident", title: "Fake", read: false,
  }));
  await assertFails(deleteDoc(ownNotification));
  await assertFails(updateDoc(
    doc(driverDb, `users/${ADMIN_PHONE}/notifications/incident_sample`),
    { read: true }
  ));
});

// --------------------------------------------------------------- ledger privacy

test("drivers can read only ledger entries matching their stable userId", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(getDoc(doc(db, "ledger/dispatch-driver")));
  await assertFails(getDoc(doc(db, "ledger/dispatch-other")));
  await assertSucceeds(
    getDocs(query(collection(db, "ledger"), where("driverId", "==", driverId)))
  );
  await assertFails(getDocs(collection(db, "ledger")));
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

test("station admin CANNOT change global queue rotation metadata", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(
    updateDoc(doc(db, "settings/queueRotation"), {
      lastQueueDay: "20260102",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    updateDoc(doc(db, "settings/queueRotation"), {
      routesNormalized: true,
    })
  );
});

test("station admin can create and increment only the shared queue revision", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertSucceeds(
    setDoc(doc(db, "settings/queueControl"), {
      revision: 1,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    updateDoc(doc(db, "settings/queueControl"), {
      revision: 2,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    updateDoc(doc(db, "settings/queueControl"), {
      revision: 3,
      routesNormalized: true,
    })
  );
  await assertFails(
    updateDoc(doc(db, "settings/queueControl"), {
      revision: 10,
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

test("admin CANNOT write to the retired kita collection", async () => {
  const db = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(updateDoc(doc(db, "kita/k-drv"), { amount: 250 }));
  await assertFails(
    setDoc(doc(db, "kita/k-new"), { driverId, amount: 250, type: "Kita" })
  );
});

// --------------------------------------------------------------- activityLogs

test("driver CAN write his own audit entry (Tapos Na Bumyahe)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertSucceeds(
    setDoc(doc(db, "activityLogs/log-2"), {
      actorName: "Hindi kilala", actorPhone: DRIVER_PHONE, actorRole: "Driver",
      action: "Tapos Na Bumyahe", detail: "TXA 448",
      createdAt: serverTimestamp(),
    })
  );
});

test("an audit entry must carry the WRITER'S OWN phone (no spoofing)", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    setDoc(doc(db, "activityLogs/log-spoof"), {
      actorName: "Maria", actorPhone: ADMIN_PHONE, actorRole: "Admin",
      action: "Nag-dispatch", detail: "pinanggap si admin",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    setDoc(doc(db, "activityLogs/log-orph"), {
      actorName: "Juan", actorRole: "Driver",
      action: "Tapos Na Bumyahe", detail: "walang actorPhone",
      createdAt: serverTimestamp(),
    })
  );
});

test("an audit entry cannot spoof actorName or actorRole with the correct phone", async () => {
  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    setDoc(doc(db, "activityLogs/log-name-spoof"), {
      actorName: "Maria", actorPhone: DRIVER_PHONE, actorRole: "Driver",
      action: "Tapos Na Bumyahe", detail: "pekeng pangalan",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    setDoc(doc(db, "activityLogs/log-role-spoof"), {
      actorName: "Hindi kilala", actorPhone: DRIVER_PHONE, actorRole: "Superadmin",
      action: "Settings", detail: "pekeng role",
      createdAt: serverTimestamp(),
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

test("clients cannot read any password secret", async () => {
  const driverDb = dbAs(DRIVER_PHONE, "u-driver");
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  await assertFails(getDoc(doc(driverDb, `userSecrets/${DRIVER_PHONE}`)));
  await assertFails(getDoc(doc(driverDb, `userSecrets/${ADMIN_PHONE}`)));
  await assertFails(getDoc(doc(adminDb, `userSecrets/${DRIVER_PHONE}`)));
  await assertFails(getDocs(collection(driverDb, "userSecrets")));
});

test("clients cannot create or overwrite an existing password secret", async () => {
  const driverDb = dbAs(DRIVER_PHONE, "u-driver");
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  const driverSecret = {
    passwordHash: "a".repeat(64),
    passwordSalt: "b".repeat(32),
    iterations: 210000,
    updatedAt: new Date(),
  };

  await assertFails(setDoc(doc(driverDb, `userSecrets/${DRIVER_PHONE}`), driverSecret));
  await assertFails(updateDoc(doc(driverDb, `userSecrets/${DRIVER_PHONE}`), {
    passwordHash: "c".repeat(64),
  }));
  await assertFails(updateDoc(doc(adminDb, `userSecrets/${DRIVER_PHONE}`), {
    passwordHash: "d".repeat(64),
  }));
  await assertFails(setDoc(doc(adminDb, "userSecrets/+639171000005"), driverSecret));
});

test("an admin can create a password secret only with a brand-new account in one atomic batch", async () => {
  const adminDb = dbAs(ADMIN_PHONE, "u-admin");
  const phone = "+639171000005";
  const batch = writeBatch(adminDb);

  batch.set(doc(adminDb, `users/${phone}`), {
    userId: "user-new-password-test",
    firstName: "New",
    lastName: "Driver",
    phoneNumber: phone,
    role: "driver",
    status: "active",
    createdAt: new Date(),
  });
  batch.set(doc(adminDb, `userSecrets/${phone}`), {
    passwordHash: "a".repeat(64),
    passwordSalt: "b".repeat(32),
    iterations: 210000,
    createdAt: new Date(),
  });

  await assertSucceeds(batch.commit());

  // A later, separate secret create must fail because the profile already exists.
  await assertFails(setDoc(doc(adminDb, `userSecrets/${phone}`), {
    passwordHash: "c".repeat(64),
    passwordSalt: "d".repeat(32),
    iterations: 210000,
  }));
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

test("clients cannot set or remove the legacy plaintext password field", async () => {
  // Simulate a legacy account that still has a plaintext password. Without
  // this seed, deleteField() is a no-op and Firestore correctly permits it.
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), `users/${DRIVER_PHONE}`), {
      password: "legacy-test-only",
    });
  });

  const db = dbAs(DRIVER_PHONE, "u-driver");
  await assertFails(
    updateDoc(doc(db, `users/${DRIVER_PHONE}`), {
      password: "Enerjeep@2026",
    })
  );
  await assertFails(
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
