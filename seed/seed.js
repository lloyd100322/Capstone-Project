#!/usr/bin/env node
/**
 * EnerJeep — Firestore seed data (realistic demo dataset).
 *
 * Creates login-ready demo accounts, queue entries across both routes,
 * incidents, earnings (kita), dispatch ledger records, donations,
 * settings, emergency contacts and audit-log samples — enough to
 * demonstrate every feature without manual data entry.
 *
 * Usage:
 *   node seed.js --serviceAccount /path/to/serviceAccount.json
 *   node seed.js                      (uses GOOGLE_APPLICATION_CREDENTIALS)
 *
 * Options:
 *   --wipe     delete existing docs in the seeded collections first
 *              (DANGEROUS - never on a project with real data)
 *
 * The password hash matches the app's PasswordHasher exactly:
 * PBKDF2-HMAC-SHA256, 12,000 iterations, 16-byte salt, 256-bit output,
 * hex-encoded. The demo password is plain ASCII so the Java and Node
 * encodings are byte-identical.
 *
 * NEVER commit the service-account key. Keep it outside the repo.
 */

const admin = require("firebase-admin");
const crypto = require("crypto");

// ---------------------------------------------------------------- args
const args = process.argv.slice(2);
const wipe = args.includes("--wipe");
const serviceAccountArgIdx = args.indexOf("--serviceAccount");
const serviceAccountPath =
  serviceAccountArgIdx >= 0 ? args[serviceAccountArgIdx + 1] : null;

if (serviceAccountPath) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccountPath),
  });
} else {
  // Uses GOOGLE_APPLICATION_CREDENTIALS or the default service account.
  admin.initializeApp();
}

const db = admin.firestore();

// ------------------------------------------------------------- helpers
function pbkdf2(password, saltHex) {
  return crypto
    .pbkdf2Sync(password, Buffer.from(saltHex, "hex"), 12_000, 32, "sha256")
    .toString("hex");
}

function makeSalt() {
  return crypto.randomBytes(16).toString("hex");
}

function makeSecrets(password) {
  const salt = makeSalt();
  return {
    passwordHash: pbkdf2(password, salt),
    passwordSalt: salt,
  };
}

/** Firestore Timestamp from a JS Date. */
function ts(date) {
  return admin.firestore.Timestamp.fromDate(date);
}

function daysAgo(n, hour = 8, minute = 0) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  d.setHours(hour, minute, 0, 0);
  return d;
}

/** "MMM dd, yyyy" - the same display format the app stores. */
function dateLabel(d) {
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric",
  });
}

/** "yyyyMMdd" queue-day id (cut-off aware, same as the app). */
function queueDayId(d, cutoffHour) {
  const c = new Date(d);
  if (c.getHours() >= cutoffHour) c.setDate(c.getDate() + 1);
  const y = c.getFullYear();
  const m = String(c.getMonth() + 1).padStart(2, "0");
  const day = String(c.getDate()).padStart(2, "0");
  return `${y}${m}${day}`;
}

function timeLabel(d) {
  return d
    .toLocaleTimeString("en-US", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
    })
    .replace(/^0/, "");
}

const DEMO_PASSWORD = "Demo123!";

// ---------------------------------------------------------------- data
const ROUTE_AM = "Marilao – Monumento"; // entry route
const ROUTE_MA = "Monumento – Marilao"; // fills up after dispatches

const users = [
  {
    phone: "+639171000001",
    userId: "seed-uid-superadmin",
    firstName: "Ramon",
    lastName: "Villanueva",
    role: "superadmin",
  },
  {
    phone: "+639171000002",
    userId: "seed-uid-admin",
    firstName: "Maria",
    lastName: "Santos",
    role: "admin",
  },
  // Drivers - Filipino names, PH-style plates (3 letters + 3 digits).
  {
    phone: "+639171000003",
    userId: "seed-uid-juan",
    firstName: "Juan",
    lastName: "dela Cruz",
    role: "driver",
    plate: "TUV 345",
    seater: 16,
    license: "N01-12-345678",
  },
  {
    phone: "+639171000004",
    userId: "seed-uid-pedro",
    firstName: "Pedro",
    lastName: "Reyes",
    role: "driver",
    plate: "WXY 678",
    seater: 22,
    license: "N02-23-456789",
  },
  {
    phone: "+639171000005",
    userId: "seed-uid-jose",
    firstName: "Jose",
    lastName: "Ramos",
    role: "driver",
    plate: "ABC 901",
    seater: 16,
    license: "N03-34-567890",
  },
  {
    phone: "+639171000006",
    userId: "seed-uid-ana",
    firstName: "Ana",
    lastName: "Lim",
    role: "driver",
    plate: "DEF 234",
    seater: 22,
    license: "N04-45-678901",
  },
  {
    phone: "+639171000007",
    userId: "seed-uid-rafael",
    firstName: "Rafael",
    lastName: "Garcia",
    role: "driver",
    plate: "GHI 567",
    seater: 16,
    license: "N05-56-789012",
  },
  {
    phone: "+639171000008",
    userId: "seed-uid-miguel",
    firstName: "Miguel",
    lastName: "Torres",
    role: "driver",
    plate: "JKL 890",
    seater: 22,
    license: "N06-67-890123",
  },
];

const drivers = users.filter((u) => u.role === "driver");

// ------------------------------------------------------------ writing
async function wipeSeeded() {
  const collections = [
    "users",
    "userSecrets",
    "queue",
    "incidents",
    "ledger",
    "kita",
    "donations",
    "settings",
    "activityLogs",
    "emergencyContacts",
    "dispatches",
  ];
  for (const name of collections) {
    const snap = await db.collection(name).limit(500).get();
    if (snap.empty) continue;
    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    console.log(`  wiped ${snap.size} docs from ${name}/`);
  }
}

async function seedUsers() {
  for (const u of users) {
    const isDriver = u.role === "driver";
    const doc = {
      userId: u.userId,
      firstName: u.firstName,
      lastName: u.lastName,
      phoneNumber: u.phone,
      role: u.role,
      status: "active",
      createdAt: ts(daysAgo(30)),
    };
    if (isDriver) {
      doc.licenseNumber = u.license;
      doc.plateNumber = u.plate;
      doc.seater = u.seater;
      doc.emergencyContactName = "Bahay ni " + u.firstName;
      doc.emergencyContactNumber = "+6391755500" + u.phone.slice(-2);
      doc.emergencyContact =
        "Bahay ni " + u.firstName + " - +6391755500" + u.phone.slice(-2);
      doc.isVehicleActive = true;
    }
    await db.collection("users").doc(u.phone).set(doc);

    await db
      .collection("userSecrets")
      .doc(u.phone)
      .set({
        ...makeSecrets(DEMO_PASSWORD),
        createdAt: ts(daysAgo(30)),
      });
  }
  console.log(`  users + userSecrets: ${users.length} accounts`);
}

async function seedQueue() {
  // Marilao - Monumento: the pila for today (5 waiting).
  const order = [drivers[0], drivers[1], drivers[2], drivers[3], drivers[4]];
  let batch = db.batch();
  order.forEach((d, i) => {
    const at = daysAgo(0, 5 + i, 15 + i * 7);
    batch.set(db.collection("queue").doc(`queue_seed_am_${i + 1}`), {
      id: `queue_seed_am_${i + 1}`,
      driverId: d.userId,
      plateNumber: d.plate,
      driverName: `${d.firstName} ${d.lastName}`,
      route: ROUTE_AM,
      position: i + 1,
      status: "Nakapila",
      isCancelled: false,
      time: timeLabel(at),
      createdAt: ts(at),
    });
  });

  // Monumento - Marilao: one driver already dispatched (#1 of the other
  // route) - this is what the tab looks like after the first dispatch.
  batch.set(db.collection("queue").doc("queue_seed_ma_1"), {
    id: "queue_seed_ma_1",
    driverId: drivers[5].userId,
    plateNumber: drivers[5].plate,
    driverName: `${drivers[5].firstName} ${drivers[5].lastName}`,
    route: ROUTE_MA,
    position: 1,
    status: "Nakapila",
    isCancelled: false,
    time: timeLabel(daysAgo(0, 6, 5)),
    createdAt: ts(daysAgo(0, 6, 5)),
    dispatchedAt: ts(daysAgo(0, 7, 40)),
  });

  // One completed trip today (Kasaysayan + red "Tapos Na Bumyahe").
  batch.set(db.collection("queue").doc("queue_seed_hist_1"), {
    id: "queue_seed_hist_1",
    driverId: drivers[0].userId,
    plateNumber: drivers[0].plate,
    driverName: `${drivers[0].firstName} ${drivers[0].lastName}`,
    route: ROUTE_AM,
    position: 0,
    status: "completed",
    isCancelled: false,
    time: timeLabel(daysAgo(0, 4, 50)),
    createdAt: ts(daysAgo(0, 4, 50)),
    endTravelAt: ts(daysAgo(0, 12, 30)),
    updatedAt: ts(daysAgo(0, 12, 30)),
  });

  // One cancelled entry (Nakansela example, still visible in the list).
  batch.set(db.collection("queue").doc("queue_seed_cancel_1"), {
    id: "queue_seed_cancel_1",
    driverId: drivers[1].userId,
    plateNumber: drivers[1].plate,
    driverName: `${drivers[1].firstName} ${drivers[1].lastName}`,
    route: ROUTE_AM,
    position: 6,
    status: "cancelled",
    isCancelled: true,
    time: timeLabel(daysAgo(0, 5, 55)),
    createdAt: ts(daysAgo(0, 5, 55)),
    updatedAt: ts(daysAgo(0, 6, 20)),
  });

  await batch.commit();
  console.log("  queue: 5 pila + 1 kabila + 1 tapos + 1 nakansela");
}

async function seedIncidents() {
  const incidents = [
    {
      id: "incident_seed_1",
      open: true,
      title: "Flat tire sa gulong",
      plate: drivers[0].plate,
      driver: drivers[0],
      location: "McArthur Highway, Meycauayan, Bulacan",
      description:
        "Napunit ang gulong sa kaliwang bahagi habang papunta ng Monumento. Nagpapalit na ng gulong.",
      daysBack: 0,
      hour: 7,
      multiya: 0,
      sent: false,
    },
    {
      id: "incident_seed_2",
      open: true,
      title: "Overheating ng makina",
      plate: drivers[2].plate,
      driver: drivers[2],
      location: "Marilao Town Proper, Marilao, Bulacan",
      description:
        "Umaandar ang makina ngunit mabilis uminit. Huminto para magpalamig bago magpatuloy.",
      daysBack: 0,
      hour: 9,
      multiya: 0,
      sent: false,
    },
    {
      id: "incident_seed_3",
      open: false,
      title: "Minor collision sa Monumento",
      plate: drivers[1].plate,
      driver: drivers[1],
      location: "Monumento Circle, Caloocan City",
      description:
        "Dumikit sa panaklong ang harap na bahagi habang pumipila sa biyahe. Walang sugatan.",
      daysBack: 1,
      hour: 15,
      multiya: 150,
      sent: true,
    },
    {
      id: "incident_seed_4",
      open: false,
      title: "Nawalang side mirror",
      plate: drivers[3].plate,
      driver: drivers[3],
      location: "Brgy. Malinta, Valenzuela City",
      description:
        "Natanggal ang kanang side mirror matapos ang siksikang biyahe. Naipalit na.",
      daysBack: 2,
      hour: 11,
      multiya: 0,
      sent: true,
    },
  ];

  let batch = db.batch();
  for (const it of incidents) {
    const at = daysAgo(it.daysBack, it.hour, 20);
    batch.set(db.collection("incidents").doc(it.id), {
      id: it.id,
      driverId: it.driver.userId,
      driverName: `${it.driver.firstName} ${it.driver.lastName}`,
      driverPhone: it.driver.phone,
      plateNumber: it.plate,
      title: it.title,
      location: it.location,
      description: it.description,
      date: dateLabel(at),
      isResolved: !it.open,
      status: it.open ? "open" : "resolved",
      penalty: it.multiya,
      sentToDriver: it.sent,
      createdAt: ts(at),
    });
  }
  await batch.commit();
  console.log("  incidents: 2 bukas + 2 naayos (may multa ang isa)");
}

async function seedKitaAndLedger() {
  let batch = db.batch();
  let n = 0;

  // Manual earnings for the last 3 days (today's records are the ones
  // the "Buong Kita Ngayon" / Kita tab totals show).
  const entries = [
    [0, 8, 30, "Kita", "Pamasahe (umaga)", 1450],
    [0, 13, 10, "Kita", "Pamasahe (hapon)", 1620],
    [0, 12, 45, "Bayad", "Diesel", 500],
    [0, 17, 5, "Bayad", "Toll at parking", 50],
    [1, 8, 25, "Kita", "Pamasahe (umaga)", 1380],
    [1, 14, 0, "Kita", "Pamasahe (hapon)", 1510],
    [1, 12, 30, "Bayad", "Diesel", 480],
    [1, 16, 40, "Multa", "Multa sa counterflow", 150],
    [2, 8, 15, "Kita", "Pamasahe (umaga)", 1420],
    [2, 13, 30, "Bayad", "Diesel", 510],
  ];

  entries.forEach((e, i) => {
    const [back, hh, mm, type, title, amount] = e;
    const d = daysAgo(back, hh, mm);
    const driver = drivers[i % 3];
    const id = `kita_seed_${i + 1}`;
    batch.set(db.collection("kita").doc(id), {
      id,
      driverId: driver.userId,
      title,
      amount,
      type,
      date: dateLabel(d),
      isHistory: back > 0,
      createdAt: ts(d),
    });
    n++;
  });

  // Dispatch ledger records (what the Kita tab merges with the manual
  // entries - one per dispatched queue entry).
  const dispatches = [
    [0, 7, 40, drivers[5], "queue_seed_ma_1", 1320],
    [1, 8, 0, drivers[0], "queue_seed_hist_old", 1120],
    [2, 7, 55, drivers[1], "queue_seed_hist_old2", 1540],
  ];
  dispatches.forEach((e, i) => {
    const [back, hh, mm, driver, queueId, net] = e;
    const d = daysAgo(back, hh, mm);
    const id = `ledger_seed_${i + 1}`;
    // Pareho ng hugis ng dispatch-ledger na sinusulat ng app
    // (QueueViewModel dispatch): may gross/seater/deduction fields.
    const tierBySeater = { 16: 32, 22: 44 }; // settings/deductionTiers
    const tier = tierBySeater[driver.seater] ?? driver.seater * 2;
    batch.set(db.collection("ledger").doc(id), {
      id,
      driverId: driver.userId,
      driverName: `${driver.firstName} ${driver.lastName}`,
      plateNumber: driver.plate,
      queueId,
      date: dateLabel(d),
      amount: net,
      grossAmount: net + tier,
      otherFees: tier,
      netAmount: net,
      seater: driver.seater,
      seaterDeduction: tier,
      deductionCustomized: false,
      feeType: "Dispatch",
      status: "Bayad",
      source: "queue_dispatch",
      createdAt: ts(d),
    });
    n++;
  });

  await batch.commit();
  console.log(`  kita + ledger: ${n} records`);
}

async function seedDonations() {
  let batch = db.batch();
  /*
   * type "donate" = idinagdag sa pondo (boluntaryo, kahit kanino).
   * type "gamit"  = ibinawas mula sa pondo (ginamit ng kooperatiba).
   * HIWALAY ang donasyon sa kita ng mga driver - hindi binabawas.
   */
  const donations = [
    [0, 10, 0, "Tulong para sa gulong", 2000, "Donasyon ng may-ari", "sponsor", "donate"],
    [1, 15, 30, "Ambag para sa ayuda", 1500, "Mula sa kita ng kooperatiba", "kooperatiba", "donate"],
    [3, 9, 0, "Tulong sa pamilya ng driver", 1000, "Donasyon ng mga kasapi", "kasapi", "donate"],
    [1, 11, 0, "Tulong sa miyembro", 500, "Gamit ng pondo - tulong pangkalusugan", "tulong", "gamit"],
    [2, 14, 0, "Gamit sa opisina", 300, "Papel at supplies ng kooperatiba", "opisina", "gamit"],
  ];
  donations.forEach((e, i) => {
    const [back, hh, mm, donor, amount, note, source, type] = e;
    const d = daysAgo(back, hh, mm);
    const id = `donation_seed_${i + 1}`;
    batch.set(db.collection("donations").doc(id), {
      id,
      donor,
      amount,
      date: dateLabel(d),
      note,
      source,
      type,
      createdAt: ts(d),
    });
  });
  await batch.commit();
  console.log("  donations: 5 entries (3 donate, 2 gamit)");
}

async function seedSettings() {
  await db
    .collection("settings")
    .doc("deductionTiers")
    .set({ 16: 32, 22: 44 });

  const cutoffHour = 21; // default 9 PM - editable in Superadmin Settings
  await db
    .collection("settings")
    .doc("queueRotation")
    .set({
      cutoffHour,
      lastQueueDay: queueDayId(new Date(), cutoffHour),
      updatedAt: ts(new Date()),
    });

  console.log("  settings: deductionTiers + queueRotation (cutoffHour 21)");
}

async function seedEmergency() {
  let batch = db.batch();
  const contacts = [
    ["Emergency Hotline (National)", "911"],
    ["Marilao Police Station", "+639171234567"],
    ["Barangay Marilao Hall", "+639181234567"],
  ];
  contacts.forEach((c, i) => {
    batch.set(db.collection("emergencyContacts").doc(`emerg_seed_${i + 1}`), {
      name: c[0],
      number: c[1],
      addedBy: "Ramon Villanueva",
    });
  });
  await batch.commit();
  console.log("  emergencyContacts: 3 entries");
}

async function seedLogs() {
  let batch = db.batch();
  const logs = [
    ["Maria Santos", "Admin", "Dispatch", "TUV 345 → Monumento – Marilao", 0, 7, 40],
    ["Maria Santos", "Admin", "Tapos Na", "TUV 345", 0, 12, 30],
    ["Ramon Villanueva", "Superadmin", "Settings", "Oras ng reset: 9:00 PM | Bawas kada upuan: 16=32, 22=44", 1, 16, 10],
    ["Ramon Villanueva", "Superadmin", "Bagong Account", "Maria Santos (admin)", 20, 9, 0],
    ["Maria Santos", "Admin", "Insidente", "Inayos: Minor collision sa Monumento", 1, 16, 45],
  ];
  logs.forEach((l, i) => {
    const [actorName, actorRole, action, detail, back, hh, mm] = l;
    const d = daysAgo(back, hh, mm);
    batch.set(db.collection("activityLogs").doc(`log_seed_${i + 1}`), {
      actorName,
      actorRole,
      action,
      detail,
      createdAt: ts(d),
    });
  });
  await batch.commit();
  console.log("  activityLogs: 5 entries");
}

// ---------------------------------------------------------------- main
(async () => {
  try {
    console.log("EnerJeep seed — realistic demo dataset");
    if (wipe) {
      console.log("Wiping seeded collections first (--wipe)...");
      await wipeSeeded();
    }
    await seedUsers();
    await seedQueue();
    await seedIncidents();
    await seedKitaAndLedger();
    await seedDonations();
    await seedSettings();
    await seedEmergency();
    await seedLogs();
    console.log("\nDone. Demo login: any seeded phone number, password " + DEMO_PASSWORD);
    console.log("(Use Firebase Phone Auth TEST NUMBERS for the OTP - see seed/README.md)");
    process.exit(0);
  } catch (err) {
    console.error("Seed failed:", err.message);
    process.exit(1);
  }
})();
