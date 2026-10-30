#!/usr/bin/env node
/**
 * EnerJeep — paghahanda ng datos ng Firestore (tunay na datos ng
 * kooperatiba para sa unang takbo ng app).
 *
 * Lumilikha ng handa-nang-gamitin na mga account, queue entries sa
 * dalawang ruta, mga insidente, dispatch ledger records, mga donasyon,
 * settings, emergency contacts at audit log — kumpleto ang bawat
 * feature nang walang manwal na pag-encode.
 *
 * Usage:
 *   node setup.js --serviceAccount /path/to/serviceAccount.json
 *   node setup.js                      (uses GOOGLE_APPLICATION_CREDENTIALS)
 *
 * Options:
 *   --wipe     delete existing docs in the collections first
 *              (DANGEROUS - never on a project with real data)
 *
 * Ang password hash ay kapareho ng PasswordHasher ng app:
 * PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte salt, 256-bit
 * output, hex-encoded. Ang password ay plain ASCII kaya pareho ang
 * Java at Node encoding.
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
// Must match PasswordHasher.ITERATIONS in the Android app.
const PBKDF2_ITERATIONS = 210_000;

function pbkdf2(password, saltHex) {
  return crypto
    .pbkdf2Sync(
      password,
      Buffer.from(saltHex, "hex"),
      PBKDF2_ITERATIONS,
      32,
      "sha256"
    )
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
    iterations: PBKDF2_ITERATIONS,
  };
}

/** Firestore Timestamp from a JS Date. */
function ts(date) {
  return admin.firestore.Timestamp.fromDate(date);
}

function daysAgo(back, hh = 9, mm = 0) {
  const d = new Date();
  d.setDate(d.getDate() - back);
  d.setHours(hh, mm, 0, 0);
  return d;
}

function timeLabel(d) {
  return d.toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

function dateLabel(d) {
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric",
  });
}

/** Maikling pantay na suffix para sa mga tunay na doc id (deterministic). */
function tag(n) {
  return (0xa100 + n).toString(16);
}

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
 * numero) - "Bong" at "Marlon" ang mga pansamantalang pangalan.
 */
const roster = [
  ["Roger", "TXA 448"],
  ["Jess", "DGT 463"], // may pangalawang plate sa listahan: APT 255
  ["Joel", "PWU 904"], // may pangalawang plate sa listahan: DKP 874
  ["Gadaw", "CLJ 406"],
  ["Bobet", "DDZ 726"],
  ["Ryan", "PWD 501"],
  ["Jhomson", "DKZ 231"],
  ["Bong", "TWL 530"], // walang pangalan sa listahan - pansamantalang pangalan
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
  ["Marlon", "DLH 781"], // walang numero/pangalan sa listahan - pansamantalang pangalan
];

// Mga apelyidong pantulong lang (wala sa listahan) - pinunan para kumpleto.
const LAST_NAMES = [
  "Santos", "Reyes", "Cruz", "Bautista", "Mendoza", "Garcia", "Ramos",
  "Torres", "Dela Cruz", "Fernandez", "Aquino", "Castillo", "Ramirez",
  "Gonzales", "Francisco", "Diaz", "Lopez", "Rivera", "Gomez", "Alvarez",
  "Romero", "Navarro", "Pascual", "Sandoval", "Marquez", "Estrada",
  "Domingo", "Aguirre", "Padilla", "Manalo", "Mercado", "Del Rosario",
  "Valdez",
];

const ACCOUNT_PASSWORD = "Enerjeep@2026";

const users = [
  {
    phone: "+639171000001",
    userId: "USR-0001",
    firstName: "Ramon",
    lastName: "Villanueva",
    role: "superadmin",
  },
  {
    phone: "+639171000002",
    userId: "USR-0002",
    firstName: "Maria",
    lastName: "Santos",
    role: "admin",
    adminStation: "marilao", // "Admin - Marilao" ang badge
  },
  // Drivers - tunay na pangalan at plate mula sa listahan ng terminal.
  ...roster.map(([name, plate], i) => ({
    phone: "+639171" + String(3 + i).padStart(6, "0"),
    userId: "USR-" + String(3 + i).padStart(4, "0"),
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
async function wipeCollections() {
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
    "kitaHistory",
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
 * Linisin ang mga lumang doc (mga nakaraang bersyon ng datos) para
 * hindi na kailangan ng --wipe tuwing muling tatakbo laban sa may
 * datos nang database. Kasama rito ang:
 *   - mga doc na may "seed" sa id (lumang scheme ng id);
 *   - LAHAT ng doc sa "kita"/"kitaHistory" (lumang manual entries -
 *     gastos/multa/pamasahe na binabawas sa kita; ang kita ng driver
 *     ay dispatch records lang sa "ledger" ngayon);
 *   - mga ledger record na nag-uugnay ng donasyon sa kita ng driver
 *     (DAPAT hiwalay ang donasyon - hindi ito binabawas sa sinuman).
 * Walang epekto kung wala na ang mga doc na ito.
 */
async function cleanupLegacyDocs() {
  const collections = [
    "users",
    "userSecrets",
    "queue",
    "incidents",
    "ledger",
    "kita",
    "donations",
    "activityLogs",
    "emergencyContacts",
    "kitaHistory",
  ];

  const donationWord = /donasyon|donation|ambag/i;

  for (const name of collections) {
    const snap = await db.collection(name).limit(500).get();
    if (snap.empty) continue;

    const doomed = snap.docs.filter((doc) => {
      if (doc.id.toLowerCase().includes("seed")) return true;

      // Ang "kita"/"kitaHistory" ay hindi na ginagamit - lahat ng
      // laman ay legacy (manual entries na binabawas sa kita).
      if (name === "kita" || name === "kitaHistory") return true;

      // Ang donasyon ay nasa /donations lang - walang record sa kita
      // o ledger na dapat magbanggit nito (lalo na bilang bawas).
      if (name === "kita" || name === "ledger") {
        const x = doc.data();
        const text = [x.title, x.type, x.feeType, x.note, x.source]
          .filter(Boolean)
          .join(" ");
        if (donationWord.test(text)) return true;
      }
      return false;
    });

    if (doomed.length === 0) continue;
    const batch = db.batch();
    doomed.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    console.log(`  cleanup: inalis ang ${doomed.length} lumang doc sa ${name}/`);
  }
}

async function writeUsers() {
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
    if (u.adminStation) {
      doc.adminStation = u.adminStation;
    }
    await db.collection("users").doc(u.phone).set(doc);

    await db
      .collection("userSecrets")
      .doc(u.phone)
      .set({
        ...makeSecrets(ACCOUNT_PASSWORD),
        createdAt: ts(daysAgo(30)),
      });
  }
  console.log(`  users + userSecrets: ${users.length} accounts`);
}

async function writeQueue() {
  /*
   * Tunay na pila ng terminal: lahat ng 33 drayber mula sa listahan,
   * hinati sa dalawang ruta (17 sa Marilao-Monumento, 16 sa
   * Monumento-Marilao). May dagdag na isang tapos na biyahe kahapon at
   * isang nakansela para makita ang estado ng Kasaysayan at Nakansela.
   *
   * Ang bawat na-dispatch na biyahe ay may tatlong doc, gaya ng
   * sinusulat ng app: ang na-dispatch na entry (kasaysayan), ang
   * muling pila (`queue_<driverId>_after_<entryId>`), at ang ledger
   * record (`<entryId>_dispatch`).
   */
  const amDrivers = drivers.slice(0, 17);
  const maDrivers = drivers.slice(17);
  let batch = db.batch();

  // ---- kahapon: kumpletong biyahe ni Roger (dispatch -> requeue -> tapos)
  const rogerDispatchId = `queue_${drivers[0].userId}_${tag(201)}`;
  batch.set(db.collection("queue").doc(rogerDispatchId), {
    id: rogerDispatchId,
    driverId: drivers[0].userId,
    plateNumber: drivers[0].plate,
    driverName: `${drivers[0].firstName} ${drivers[0].lastName}`.trim(),
    route: ROUTE_AM,
    position: 0,
    status: "dispatched",
    isCancelled: false,
    income: 1152,
    otherFees: 32,
    netAmount: 1120,
    time: timeLabel(daysAgo(1, 4, 50)),
    createdAt: ts(daysAgo(1, 4, 50)),
    dispatchedAt: ts(daysAgo(1, 8, 0)),
    updatedAt: ts(daysAgo(1, 8, 0)),
  });

  const rogerRequeueId = `queue_${drivers[0].userId}_after_${rogerDispatchId}`;
  batch.set(db.collection("queue").doc(rogerRequeueId), {
    id: rogerRequeueId,
    driverId: drivers[0].userId,
    plateNumber: drivers[0].plate,
    driverName: `${drivers[0].firstName} ${drivers[0].lastName}`.trim(),
    route: ROUTE_MA,
    position: 0,
    status: "completed",
    isCancelled: false,
    time: timeLabel(daysAgo(1, 8, 0)),
    createdAt: ts(daysAgo(1, 8, 0)),
    endTravelAt: ts(daysAgo(1, 12, 30)),
    updatedAt: ts(daysAgo(1, 12, 30)),
  });

  // ---- 2 araw na nakalipas: na-dispatch si Jess (nasa aktibong pila
  //      muli ngayon sa ibaba).
  const jessDispatchId = `queue_${drivers[1].userId}_${tag(202)}`;
  batch.set(db.collection("queue").doc(jessDispatchId), {
    id: jessDispatchId,
    driverId: drivers[1].userId,
    plateNumber: drivers[1].plate,
    driverName: `${drivers[1].firstName} ${drivers[1].lastName}`.trim(),
    route: ROUTE_AM,
    position: 0,
    status: "dispatched",
    isCancelled: false,
    income: 1584,
    otherFees: 44,
    netAmount: 1540,
    time: timeLabel(daysAgo(2, 6, 30)),
    createdAt: ts(daysAgo(2, 6, 30)),
    dispatchedAt: ts(daysAgo(2, 7, 55)),
    updatedAt: ts(daysAgo(2, 7, 55)),
  });

  // ---- ngayong umaga: na-dispatch si Jimmy mula sa Marilao-Monumento,
  //      kaya #1 na siya ngayon sa Monumento-Marilao.
  const jimmyDispatchId = `queue_${drivers[17].userId}_${tag(203)}`;
  batch.set(db.collection("queue").doc(jimmyDispatchId), {
    id: jimmyDispatchId,
    driverId: drivers[17].userId,
    plateNumber: drivers[17].plate,
    driverName: `${drivers[17].firstName} ${drivers[17].lastName}`.trim(),
    route: ROUTE_AM,
    position: 0,
    status: "dispatched",
    isCancelled: false,
    income: 1364,
    otherFees: 44,
    netAmount: 1320,
    time: timeLabel(daysAgo(0, 5, 30)),
    createdAt: ts(daysAgo(0, 5, 30)),
    dispatchedAt: ts(daysAgo(0, 7, 40)),
    updatedAt: ts(daysAgo(0, 7, 40)),
  });

  const jimmyRequeueId = `queue_${drivers[17].userId}_after_${jimmyDispatchId}`;

  amDrivers.forEach((d, i) => {
    const at = daysAgo(0, 5 + (i % 12), 10 + (i % 6) * 8);
    const id = `queue_${d.userId}_${tag(i)}`;
    batch.set(db.collection("queue").doc(id), {
      id,
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
    // Ang unang entry ng kabilang ruta = ang muling pila ni Jimmy pagkatapos
    // ng dispatch (7:40 AM) - gaya ng ginagawa ng app.
    if (i === 0) {
      batch.set(db.collection("queue").doc(jimmyRequeueId), {
        id: jimmyRequeueId,
        driverId: d.userId,
        plateNumber: d.plate,
        driverName: `${d.firstName} ${d.lastName}`.trim(),
        route: ROUTE_MA,
        position: 1,
        status: "Nakapila",
        isCancelled: false,
        time: timeLabel(daysAgo(0, 7, 40)),
        createdAt: ts(daysAgo(0, 7, 40)),
      });
      return;
    }

    const at = daysAgo(0, 6 + (i % 10), 5 + (i % 6) * 9);
    const id = `queue_${d.userId}_${tag(100 + i)}`;
    batch.set(db.collection("queue").doc(id), {
      id,
      driverId: d.userId,
      plateNumber: d.plate,
      driverName: `${d.firstName} ${d.lastName}`.trim(),
      route: ROUTE_MA,
      position: i + 1,
      status: "Nakapila",
      isCancelled: false,
      time: timeLabel(at),
      createdAt: ts(at),
    });
  });

  /*
   * Isang nakanselang entry (Nakansela, nakikita pa rin sa listahan).
   * Gaya ng ginagawa ng app: nananatiling "Nakapila" ang status,
   * ang `isCancelled` flag ang nagtatakda ng "Nakansela", at nasa
   * pinakadulo ng pila.
   */
  const cancelledId = `queue_${drivers[1].userId}_${tag(204)}`;
  batch.set(db.collection("queue").doc(cancelledId), {
    id: cancelledId,
    driverId: drivers[1].userId,
    plateNumber: drivers[1].plate,
    driverName: `${drivers[1].firstName} ${drivers[1].lastName}`.trim(),
    route: ROUTE_AM,
    position: 34,
    status: "Nakapila",
    isCancelled: true,
    time: timeLabel(daysAgo(0, 5, 55)),
    createdAt: ts(daysAgo(0, 5, 55)),
    cancelledAt: ts(daysAgo(0, 6, 20)),
    updatedAt: ts(daysAgo(0, 6, 20)),
  });

  await batch.commit();
  console.log(
    "  queue: 33 drayber sa dalawang ruta (17 + 16) + 4 kasaysayan + 1 nakansela"
  );

  return {
    rogerDispatchId,
    jessDispatchId,
    jimmyDispatchId,
  };
}

async function writeIncidents() {
  const incidents = [
    {
      id: `incident_${tag(301)}`,
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
      sent: true, // lahat ng insidente ay naipadala na -> nakikita ng driver
    },
    {
      id: `incident_${tag(302)}`,
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
      sent: true,
    },
    {
      id: `incident_${tag(303)}`,
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
      id: `incident_${tag(304)}`,
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
  console.log(
    "  incidents: 2 bukas + 2 naayos (may multa ang isa) — " +
      "sentToDriver: true sa lahat = nakikita ng driver"
  );
}

async function writeLedger(dispatchIds) {
  let batch = db.batch();
  let n = 0;

  /*
   * Ang kita ng driver ay nagmumula lang sa mga dispatch record sa
   * "ledger" (mga "<entryId>_dispatch"). WALANG manual na entries sa
   * "kita" collection: hindi nire-record sa app ang sariling gastos
   * ng driver (diesel, toll, atbp.) at walang binabawas sa kita
   * ("walang bawas na kahit ano").
   *
   * TANDAAN: ang donasyon ay HIWALAY sa kita ng mga driver - nasa
   * /donations lang ito at hindi kailanman binabawas sa kita.
   *
   * Dispatch ledger records (kaakibat ng mga na-dispatch na queue
   * entry sa itaas). Ang id ay `<entryId>_dispatch` - eksaktong hugis
   * na sinusulat ng app (QueueViewModel.dispatchVehicle), kaya kahit
   * ulitin ang dispatch ay isang record lang ang mananatili.
   */
  const dispatches = [
    [dispatchIds.jimmyDispatchId, drivers[17], daysAgo(0, 7, 40), 1320],
    [dispatchIds.rogerDispatchId, drivers[0], daysAgo(1, 8, 0), 1120],
    [dispatchIds.jessDispatchId, drivers[1], daysAgo(2, 7, 55), 1540],
  ];
  dispatches.forEach(([queueId, driver, d, net]) => {
    const id = `${queueId}_dispatch`;
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

  /*
   * Mga bayad ng multa (Talaan -> "I-record ang Bayad ng Multa").
   * Nasa ledger ang mga ito (feeType "Multa") - para sa "Ulat ng
   * Multa" ng admin. HIWALAY ang mga ito sa kita ng driver: hindi
   * binabawas sa kita kahit na kano. Eksaktong hugis na sinusulat ng
   * LedgerViewModel.addCollection, kaya lumalabas din ang mga ito sa
   * Talaan gaya ng totoong record.
   */
  const fines = [
    // Bayad sa multa ng "Minor collision sa Monumento" (insidente).
    [drivers[1], daysAgo(1, 16, 0), 150],
    // Multa sa trapiko na diretsong inirekord sa Talaan.
    [drivers[2], daysAgo(2, 9, 30), 150],
  ];
  fines.forEach(([driver, d, amount], i) => {
    const id = `fee_${tag(801 + i)}`;
    batch.set(db.collection("ledger").doc(id), {
      id,
      driverId: driver.userId,
      plateNumber: driver.plate,
      driverName: `${driver.firstName} ${driver.lastName}`.trim(),
      date: dateLabel(d),
      amount,
      grossAmount: amount,
      otherFees: 0.0,
      netAmount: amount,
      feeType: "Multa",
      status: "Bayad",
      source: "ledger",
      createdAt: ts(d),
    });
    n++;
  });

  await batch.commit();
  console.log(
    `  ledger: ${n} records (${dispatches.length} dispatch + ${fines.length} bayad ng multa)`
  );
}

async function writeDonations() {
  let batch = db.batch();
  /*
   * type "donate" = idinagdag sa pondo (boluntaryo, kahit kanino).
   * type "gamit"  = ibinawas mula sa pondo (ginamit ng kooperatiba).
   * HIWALAY ang donasyon sa kita ng mga driver - hindi binabawas sa
   * kita ng sinuman.
   */
  const donations = [
    [0, 10, 0, "Tulong para sa gulong", 2000, "Donasyon ng may-ari", "sponsor", "donate"],
    [1, 15, 30, "Ambag para sa ayuda", 1500, "Mula sa pondo ng kooperatiba", "kooperatiba", "donate"],
    [3, 9, 0, "Tulong sa pamilya ng driver", 1000, "Donasyon ng mga kasapi", "kasapi", "donate"],
    [1, 11, 0, "Tulong sa miyembro", 500, "Gamit ng pondo - tulong pangkalusugan", "tulong", "gamit"],
    [2, 14, 0, "Gamit sa opisina", 300, "Papel at supplies ng kooperatiba", "opisina", "gamit"],
  ];
  donations.forEach((e, i) => {
    const [back, hh, mm, donor, amount, note, source, type] = e;
    const d = daysAgo(back, hh, mm);
    const id = `donation_${tag(501 + i)}`;
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

async function writeSettings() {
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

function queueDayId(date, cutoffHour) {
  const cal = new Date(date);
  if (cal.getHours() >= cutoffHour) {
    cal.setDate(cal.getDate() + 1);
  }
  const y = cal.getFullYear();
  const m = String(cal.getMonth() + 1).padStart(2, "0");
  const d = String(cal.getDate()).padStart(2, "0");
  return `${y}${m}${d}`;
}

async function writeEmergency() {
  let batch = db.batch();
  const contacts = [
    ["Emergency Hotline (National)", "911"],
    ["Dispatcher", "+639171234502"],
    ["Marilao Police Station", "+639171234567"],
    ["Barangay Marilao Hall", "+639181234567"],
  ];
  contacts.forEach((c, i) => {
    batch.set(db.collection("emergencyContacts").doc(`contact_${tag(601 + i)}`), {
      name: c[0],
      number: c[1],
      addedBy: "Ramon Villanueva",
    });
  });
  await batch.commit();
  console.log("  emergencyContacts: 4 entries (kasama ang Dispatcher)");
}

/*
 * I-verify ang aktwal na laman ng incidents collection pagkatapos
 * mag-encode: ipinapakita kung alin ang nakikita ng driver
 * (sentToDriver) at kung resolved o hindi. Ito ang totoong estado sa
 * database.
 */
async function verifyIncidents() {
  const snap = await db.collection("incidents").get();
  console.log("  verify (aktwal sa database):");
  snap.docs.forEach((d) => {
    const x = d.data();
    const resolved = x.isResolved === true || x.status === "resolved";
    const sent = x.sentToDriver !== false;
    console.log(
      `    ${d.id} | ${resolved ? "Naayos" : "Di-naayos"} | sentToDriver: ${x.sentToDriver === undefined ? "wala" : x.sentToDriver} -> ${sent ? "NAKIKITA ng driver" : "HIDDEN (draft)"}`
    );
  });
}

/*
 * Tseke: walang record sa kita/ledger na nagbabawas mula sa kita ng
 * driver dahil sa donasyon. Ang donasyon ay nasa /donations lang.
 */
async function verifyNoDonationDeductions() {
  const donationWord = /donasyon|donation|ambag/i;
  const bad = [];
  for (const name of ["kita", "ledger"]) {
    const snap = await db.collection(name).limit(500).get();
    snap.docs.forEach((doc) => {
      const x = doc.data();
      const text = [x.title, x.type, x.feeType, x.note, x.source]
        .filter(Boolean)
        .join(" ");
      if (donationWord.test(text)) bad.push(`${name}/${doc.id}`);
    });
  }
  if (bad.length === 0) {
    console.log(
      "  verify: WALANG donasyon na nakabawas sa kita ng driver (tama)"
    );
  } else {
    console.log("  BABALA: may record na nag-uugnay ng donasyon sa kita:");
    bad.forEach((id) => console.log(`    ${id}`));
  }
}

/*
 * Tseke: ang kita ng driver ay dispatch records lang. Walang manual
 * entries sa "kita" collection (walang gastos/multa na binabawas) at
 * walang ledger record na iba pa sa dispatch o bayad ng multa.
 */
async function verifyDriverEarnings() {
  const stray = [];

  const kita = await db.collection("kita").limit(500).get();
  kita.docs.forEach((d) => stray.push(`kita/${d.id}`));

  const ledger = await db.collection("ledger").limit(500).get();
  ledger.docs.forEach((d) => {
    const x = d.data();
    const source = String(x.source || "").toLowerCase();
    const feeType = String(x.feeType || "").toLowerCase();
    const isDispatch = source === "queue_dispatch" || feeType === "dispatch";
    const isMulta = feeType === "multa";
    if (!isDispatch && !isMulta) stray.push(`ledger/${d.id}`);
  });

  if (stray.length === 0) {
    console.log(
      "  verify: kita ng driver = dispatch records lang; multa ay nasa Talaan (walang bawas sa kita) ✓"
    );
  } else {
    console.log("  BABALA: may record na hindi dispatch/bayad ng multa:");
    stray.forEach((id) => console.log(`    ${id}`));
  }
}

async function writeLogs() {
  let batch = db.batch();
  const logs = [
    ["Maria Santos", "Admin", "Dispatch", `${drivers[17].plate} → Monumento – Marilao`, 0, 7, 40],
    [`${drivers[0].firstName} ${drivers[0].lastName}`, "Driver", "Tapos Na Bumyahe", drivers[0].plate, 1, 12, 30],
    ["Ramon Villanueva", "Superadmin", "Settings", "Oras ng reset: 9:00 PM | Bawas kada upuan: 16=32, 22=44", 1, 16, 10],
    ["Ramon Villanueva", "Superadmin", "Bagong Account", "Maria Santos (admin)", 20, 9, 0],
    ["Maria Santos", "Admin", "Insidente", "Inayos: Minor collision sa Monumento", 1, 16, 45],
  ];
  logs.forEach((l, i) => {
    const [actorName, actorRole, action, detail, back, hh, mm] = l;
    const d = daysAgo(back, hh, mm);
    batch.set(db.collection("activityLogs").doc(`log_${tag(701 + i)}`), {
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
    console.log("EnerJeep — paghahanda ng datos");
    if (wipe) {
      console.log("Binubura ang lumang datos muna (--wipe)...");
      await wipeCollections();
    }
    await cleanupLegacyDocs();
    await writeUsers();
    const dispatchIds = await writeQueue();
    await writeIncidents();
    await writeLedger(dispatchIds);
    await writeDonations();
    await writeSettings();
    await writeEmergency();
    await writeLogs();
    await verifyNoDonationDeductions();
    await verifyDriverEarnings();
    await verifyIncidents();
    console.log("\nTapos. Login: alinmang numero ng account, password " + ACCOUNT_PASSWORD);
    console.log("(Gamitin ang naka-configure na phone numbers sa Firebase para sa OTP - tingnan ang seed/README.md)");
    process.exit(0);
  } catch (err) {
    console.error("Nabigo ang paghahanda ng datos:", err.message);
    process.exit(1);
  }
})();
