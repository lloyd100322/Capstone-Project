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

/*
 * TUNAY NA LISTAHAN NG TERMINAL (mula sa aktwal na listahan ng terminal).
 * Pangalan at plate number lang ang nasa listahan; ang ibang fields
 * (apelyido, cellphone, lisensya, seater, emergency contact) ay
 * pinunan para kompleto ang account sa app.
 *
 * Dalawang pangalan ang blanko sa listahan (#8 at ang DLH 781 na walang
 * numero) - "Bong" at "Marlon" ang mga placeholder.
 */
const roster = [
  ["Roger", "TXA 448"],
  ["Jess", "DGT 463"], // may pangalawang plate sa listahan: APT 255
  ["Joel", "PWU 904"], // may pangalawang plate sa listahan: DKP 874
  ["Gadaw", "CLJ 406"],
  ["Bobet", "DDZ 726"],
  ["Ryan", "PWD 501"],
  ["Jhomson", "DKZ 231"],
  ["Bong", "TWL 530"], // walang pangalan sa listahan - placeholder
  ["Dong", "PIJ 242"],
  ["Loloy", "DGV 885"],
  ["Saludo", "BBG 981"],
  ["Romie", "DGP 374"],
  ["Dexter", "TVV 252"],
  ["Joey", "PWS 831"],
  ["Greg", "TXV 421"],
  ["Narcing", "CWJ 183"],
  ["Abet", "NVV 571"],
  ["Champoy", "DGN 444"],
  ["Jimmy", "DFH 706"],
  ["Efren", "TXB 786"],
  ["Batemg", "TWG 363"],
  ["Ben", "NVW 659"],
  ["Aljon", "CVP 680"],
  ["Mark", "CVR 895"],
  ["Terry", "EFM 592"],
  ["Boyet", "PXE 220"],
  ["Kemmeth", "TWK 437"],
  ["Lamdo", "NYZ 488"],
  ["Billy", "TGM 352"],
  ["Michael", "TCL 133"],
  ["Pat", "CVB 871"],
  ["Jun", "TVA 282"],
  ["Marlon", "DLH 781"], // walang numero/pangalan sa listahan - placeholder
];

// Mga apelyidong pantulong lang (wala sa listahan) - demo data.
const LAST_NAMES = [
  "Santos", "Reyes", "Cruz", "Bautista", "Mendoza", "Garcia", "Ramos",
  "Torres", "Dela Cruz", "Fernandez", "Aquino", "Castillo", "Ramirez",
  "Gonzales", "Francisco", "Diaz", "Lopez", "Rivera", "Gomez", "Alvarez",
  "Romero", "Navarro", "Pascual", "Sandoval", "Marquez", "Estrada",
  "Domingo", "Aguirre", "Padilla", "Manalo", "Mercado", "Del Rosario",
  "Valdez",
];

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
  // Drivers - tunay na pangalan at plate mula sa listahan ng terminal.
  ...roster.map(([name, plate], i) => ({
    phone: "+639171" + String(3 + i).padStart(6, "0"),
    userId: "seed-uid-" + name.toLowerCase().replace(/[^a-z]+/g, "-"),
    firstName: name,
    lastName: LAST_NAMES[i],
    role: "driver",
    plate,
    seater: i % 2 === 0 ? 16 : 22,
    license:
      "N" + String(i + 1).padStart(2, "0") +
      "-" + String(21 + i).padStart(2, "0") +
      "-" + String(100001 + i),
  })),
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

/*
 * Linisin ang mga doc mula sa lumang mock data (bago pa ang tunay na
 * listahan ng terminal). Para hindi na kailangan ng --wipe tuwing
 * magre-run laban sa may datos nang database. Walang epekto kung wala
 * na ang mga doc na ito.
 */
async function cleanupLegacyMockDocs() {
  const legacyUserIds = [
    "seed-uid-juan",
    "seed-uid-pedro",
    "seed-uid-jose",
    "seed-uid-ana",
    "seed-uid-rafael",
    "seed-uid-miguel",
  ];
  const refs = [];
  legacyUserIds.forEach((id) => {
    refs.push(db.collection("users").doc(id));
    refs.push(db.collection("userSecrets").doc(id));
  });
  // Lumang kita doc na walang katumbas sa bagong seed.
  refs.push(db.collection("kitaHistory").doc("queue_seed_ma_2_kita"));
  let batch = db.batch();
  refs.forEach((r) => batch.delete(r));
  await batch.commit();
  console.log("  cleanup: inalis ang lumang mock accounts (kung mayroon)");
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
  /*
   * Tunay na pila ng terminal: lahat ng 33 drayber mula sa listahan,
   * hinati sa dalawang ruta (17 sa Marilao-Monumento, 16 sa
   * Monumento-Marilao). May dagdag na isang tapos na biyahe at isang
   * nakansela para makita ang estado ng Kasaysayan at Nakansela.
   */
  const amDrivers = drivers.slice(0, 17);
  const maDrivers = drivers.slice(17);
  let batch = db.batch();

  amDrivers.forEach((d, i) => {
    const at = daysAgo(0, 5 + (i % 12), 10 + (i % 6) * 8);
    batch.set(db.collection("queue").doc(`queue_seed_am_${i + 1}`), {
      id: `queue_seed_am_${i + 1}`,
      driverId: d.userId,
      plateNumber: d.plate,
      driverName: `${d.firstName} ${d.lastName}`.trim(),
      route: ROUTE_AM,
      position: i + 1,
      status: "Nakapila",
      isCancelled: false,
      time: timeLabel(at),
      createdAt: ts(at),
    });
  });

  maDrivers.forEach((d, i) => {
    const at = daysAgo(0, 6 + (i % 10), 5 + (i % 6) * 9);
    const entry = {
      id: `queue_seed_ma_${i + 1}`,
      driverId: d.userId,
      plateNumber: d.plate,
      driverName: `${d.firstName} ${d.lastName}`.trim(),
      route: ROUTE_MA,
      position: i + 1,
      status: "Nakapila",
      isCancelled: false,
      time: timeLabel(at),
      createdAt: ts(at),
    };
    // Ang unang entry ng kabilang ruta: nag-dispatch na ngayong umaga.
    if (i === 0) entry.dispatchedAt = ts(daysAgo(0, 7, 40));
    batch.set(db.collection("queue").doc(`queue_seed_ma_${i + 1}`), entry);
  });

  // Isang tapos na biyahe ngayong araw (Kasaysayan).
  batch.set(db.collection("queue").doc("queue_seed_hist_1"), {
    id: "queue_seed_hist_1",
    driverId: drivers[0].userId,
    plateNumber: drivers[0].plate,
    driverName: `${drivers[0].firstName} ${drivers[0].lastName}`.trim(),
    route: ROUTE_AM,
    position: 0,
    status: "completed",
    isCancelled: false,
    time: timeLabel(daysAgo(0, 4, 50)),
    createdAt: ts(daysAgo(0, 4, 50)),
    endTravelAt: ts(daysAgo(0, 12, 30)),
    updatedAt: ts(daysAgo(0, 12, 30)),
  });

  // Isang nakanselang entry (Nakansela, nakikita pa rin sa listahan).
  batch.set(db.collection("queue").doc("queue_seed_cancel_1"), {
    id: "queue_seed_cancel_1",
    driverId: drivers[1].userId,
    plateNumber: drivers[1].plate,
    driverName: `${drivers[1].firstName} ${drivers[1].lastName}`.trim(),
    route: ROUTE_AM,
    position: 34,
    status: "cancelled",
    isCancelled: true,
    time: timeLabel(daysAgo(0, 5, 55)),
    createdAt: ts(daysAgo(0, 5, 55)),
    updatedAt: ts(daysAgo(0, 6, 20)),
  });

  await batch.commit();
  console.log(
    "  queue: 33 tunay na drayber sa dalawang ruta (17 + 16) + 1 tapos + 1 nakansela"
  );
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
  // Ang una ay ang na-dispatch na entry sa kabilang ruta (queue_seed_ma_1).
  const dispatches = [
    [0, 7, 40, drivers[17], "queue_seed_ma_1", 1320],
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
    ["Maria Santos", "Admin", "Dispatch", `${drivers[17].plate} → Monumento – Marilao`, 0, 7, 40],
    ["Maria Santos", "Admin", "Tapos Na", drivers[0].plate, 0, 12, 30],
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
    await cleanupLegacyMockDocs();
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
