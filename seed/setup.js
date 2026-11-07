#!/usr/bin/env node
/**
 * EnerJeep operational dataset refresh.
 *
 * Builds synthetic presentation/test records using the existing Firestore accounts.
 *
 * Safety:
 * - Preserves users account profiles, userSecrets, Firebase Auth users,
 *   driverPlateClaims, and secureSessions.
 * - Cleans every other Firestore root collection recursively, including all
 *   subcollections beneath users/{account}, then writes a fresh three-month dataset.
 * - Dry-run is the default. Pass --apply to replace/delete non-account data.
 * - Keep service-account credentials outside this repository.
 */

const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

// --------------------------------------------------------------------- CLI
function readArg(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

const args = new Set(process.argv.slice(2));
const applyChanges = args.has("--apply");
const showHelp = args.has("--help") || args.has("-h");
const projectId = readArg("--project");
const serviceAccountPath = readArg("--serviceAccount");
const EXPECTED_PROJECT_ID = "enerjeep-b8f3a";

if (showHelp) {
  console.log(
    "Usage:\n" +
    "  node setup.js --project " + EXPECTED_PROJECT_ID + "\n" +
    "  node setup.js --project " + EXPECTED_PROJECT_ID + " --apply\n" +
    "  node setup.js --serviceAccount C:\\\\path\\\\serviceAccount.json " +
      "--project " + EXPECTED_PROJECT_ID + " --apply\n\n" +
    "Without --apply the script only previews the planned changes."
  );
  process.exit(0);
}

if (!projectId) {
  console.error("Kailangan ang --project para matiyak ang target Firebase project.");
  process.exit(1);
}

if (projectId !== EXPECTED_PROJECT_ID) {
  console.error("Hindi tugma ang project. Inaasahan: " + EXPECTED_PROJECT_ID);
  process.exit(1);
}

try {
  if (serviceAccountPath) {
    const absolutePath = path.resolve(serviceAccountPath);
    if (!fs.existsSync(absolutePath)) {
      throw new Error("Hindi makita ang service-account file: " + absolutePath);
    }
    const serviceAccount = JSON.parse(fs.readFileSync(absolutePath, "utf8"));
    if (serviceAccount.project_id !== projectId) {
      throw new Error(
        "Ang service-account key ay para sa " + serviceAccount.project_id +
        ", hindi sa " + projectId + "."
      );
    }
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: projectId,
    });
  } else {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: projectId,
    });
  }
} catch (error) {
  console.error("Hindi ma-initialize ang Firebase Admin SDK:", error.message);
  process.exit(1);
}

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;
const Timestamp = admin.firestore.Timestamp;
const TIME_ZONE = "Asia/Manila";
const DATA_WINDOW_MONTHS = 3;
const INDEX_REF = db.collection("settings").doc("operationsIndex");
const ROUTE_AM = "Marilao – Monumento";
const ROUTE_MA = "Monumento – Marilao";

// Verified corridor locations from the LTFRB route feed for
// "Heritage Homes - Monumento via NLEX". Keep incident locations within
// these listed stops/terminal points; do not substitute nearby off-route roads.
const NLEX_ROUTE_LOCATIONS = Object.freeze({
  marilaoNlex: "North Luzon Expressway, Marilao, Bulacan",
  meycauayanCamalig: "Maycauayan Camalig, City of Meycauayan, Bulacan",
  malhacanNlex: "Malhacan Road / North Luzon Expressway Intersection, City of Meycauayan, Bulacan",
  valenzuelaMindanao: "North Luzon Expressway / Mindanao Avenue, Valenzuela City",
  valenzuelaArty: "North Luzon Expressway near Arty Subdivision, Valenzuela City",
  caloocanNlexComplex: "North Luzon Expressway near NLEX Complex, Caloocan City",
  caloocanEdsa: "North Luzon Expressway / Epifanio de los Santos Avenue Intersection, Caloocan City",
  caloocan8thStreet: "Epifanio de los Santos Avenue / 8th Street Intersection, Caloocan City",
  monumentoSideTerminal: "Lapu-Lapu / Samson Road Intersection, Malabon City",
});

// --------------------------------------------------------------------- dates
function manilaParts(date) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const value = {};
  parts.forEach(function (part) {
    if (part.type !== "literal") value[part.type] = Number(part.value);
  });
  return value;
}

function daysAgo(days, hour, minute) {
  const p = manilaParts(new Date());
  return new Date(Date.UTC(
    p.year,
    p.month - 1,
    p.day - days,
    hour - 8,
    minute,
    0,
    0
  ));
}

function recentDate(daysBack, hour, minute) {
  const current = manilaParts(new Date());
  const safeDaysBack = Math.min(
    Math.max(0, Math.trunc(daysBack)),
    Math.max(0, current.day - 1)
  );
  let safeHour = hour;
  let safeMinute = minute;

  if (safeDaysBack === 0 &&
      (safeHour > current.hour ||
       (safeHour === current.hour && safeMinute > current.minute))) {
    safeHour = current.hour;
    safeMinute = current.minute;
  }

  return daysAgo(safeDaysBack, safeHour, safeMinute);
}

function monthDate(monthsAgo, day, hour, minute) {
  const p = manilaParts(new Date());
  const monthIndex = (p.year * 12 + (p.month - 1)) - monthsAgo;
  const year = Math.floor(monthIndex / 12);
  const month = monthIndex % 12;
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  const safeDay = Math.min(Math.max(day, 1), lastDay);
  return new Date(Date.UTC(year, month, safeDay, hour - 8, minute, 0, 0));
}

function addMinutes(date, minutes) {
  return new Date(date.getTime() + minutes * 60 * 1000);
}

function timestamp(date) {
  return Timestamp.fromDate(date);
}

function dateLabel(date) {
  return date.toLocaleDateString("en-US", {
    timeZone: TIME_ZONE,
    month: "short",
    day: "2-digit",
    year: "numeric",
  });
}

function timeLabel(date) {
  return date.toLocaleTimeString("en-US", {
    timeZone: TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

function queueDayId(date, cutoffHour) {
  const p = manilaParts(date);
  const shift = p.hour >= cutoffHour ? 1 : 0;
  const operatingDay = new Date(Date.UTC(
    p.year, p.month - 1, p.day + shift
  ));
  return String(operatingDay.getUTCFullYear()) +
    String(operatingDay.getUTCMonth() + 1).padStart(2, "0") +
    String(operatingDay.getUTCDate()).padStart(2, "0");
}

function safeId(value) {
  return String(value || "")
    .trim()
    .replace(/[^A-Za-z0-9_-]/g, "_");
}

function fullName(account) {
  return [account.firstName, account.lastName]
    .filter(Boolean)
    .join(" ")
    .trim();
}

/**
 * Account document IDs are also Firebase Auth phone identities.
 * Keep only valid Philippine mobile numbers in E.164 form; never fabricate
 * replacement numbers in the operational seeder.
 */
function isPhilippineE164MobileNumber(value) {
  return /^\+63[89]\d{9}$/.test(String(value || "").trim());
}

function accountRoleLabel(role) {
  if (role === "superadmin") return "Superadmin";
  if (role === "admin") return "Admin";
  return "Driver";
}

function isAccountActive(data) {
  return String(data.status || "active").toLowerCase() !== "inactive";
}

function isMemberRole(role) {
  return role === "admin" || role === "superadmin" || role === "driver";
}

function amountForSeat(seater, tiers) {
  const key = String(seater);
  const configured = Number(tiers[key]);
  if (Number.isFinite(configured) && configured >= 0) return configured;
  return seater > 0 ? seater * 2 : 0;
}

// --------------------------------------------------------------------- read accounts
async function loadExistingAccounts() {
  const snapshot = await db.collection("users").get();
  const accounts = snapshot.docs.map(function (document) {
    const data = document.data() || {};
    const role = String(data.role || "").trim().toLowerCase();
    return {
      docId: document.id,
      phoneNumber: String(data.phoneNumber || document.id).trim(),
      userId: String(data.userId || "").trim(),
      firstName: String(data.firstName || "").trim(),
      lastName: String(data.lastName || "").trim(),
      role: role,
      status: String(data.status || "active").trim().toLowerCase(),
      adminStation: String(data.adminStation || "").trim().toLowerCase(),
      plateNumber: String(data.plateNumber || "").trim().toUpperCase(),
      seater: Math.max(0, Math.trunc(Number(data.seater || 0))),
      isVehicleActive: data.isVehicleActive !== false,
    };
  });

  const snapshotByDocId = new Map(snapshot.docs.map(function (document) {
    return [document.id, document.data() || {}];
  }));

  const recognized = accounts.filter(function (account) {
    return isMemberRole(account.role);
  });

  // Cloud Functions resolve /users/{phone} against Firebase Auth by the
  // exact E.164 phone number. Replacing it with a random-looking number would
  // disconnect the account from its Auth identity and could collide with a
  // real subscriber. Validate, then preserve the existing account identity.
  const invalidAuthPhoneAccounts = recognized.filter(function (account) {
    return !isPhilippineE164MobileNumber(account.docId);
  });
  if (invalidAuthPhoneAccounts.length > 0) {
    throw new Error(
      "May account document ID na hindi valid Philippine mobile E.164 format. " +
      "Hindi papalitan ng seed ang account phone identities. Gamitin ang " +
      "existing verified Firebase Auth number (+63...), o mag-set up muna " +
      "ng dedicated Firebase fictional test numbers at hiwalay na migration. " +
      "Walang account o operational data ang binago."
    );
  }
  const activeMembers = recognized.filter(function (account) {
    return account.status !== "inactive";
  });
  const activeDrivers = activeMembers.filter(function (account) {
    return account.role === "driver" && account.isVehicleActive;
  });
  const admins = activeMembers.filter(function (account) {
    return account.role === "admin";
  });
  const superadmins = activeMembers.filter(function (account) {
    return account.role === "superadmin";
  });

  if (superadmins.length === 0) {
    throw new Error("Walang active superadmin account sa /users.");
  }
  if (admins.length === 0) {
    throw new Error("Walang active admin account sa /users.");
  }
  if (activeDrivers.length < 4) {
    throw new Error(
      "Kailangan ng hindi bababa sa 4 active driver accounts. " +
      "Nakita: " + activeDrivers.length + "."
    );
  }

  const invalidDrivers = activeDrivers.filter(function (driver) {
    const source = snapshotByDocId.get(driver.docId) || {};
    return !driver.userId || !driver.phoneNumber ||
      !driver.firstName || !driver.lastName || !driver.plateNumber ||
      !String(source.licenseNumber || "").trim() ||
      !String(source.emergencyContactName || "").trim() ||
      !String(source.emergencyContactNumber || "").trim() ||
      driver.seater <= 0;
  });
  if (invalidDrivers.length > 0) {
    throw new Error(
      "May active driver account na kulang ang userId, pangalan, phoneNumber, " +
      "plaka, lisensya, seater o emergency contact. Kumpletuhin muna ang " +
      "Driver Profile at ulitin. Walang account ang binago ng script."
    );
  }

  const invalidAdmins = admins.filter(function (account) {
    return account.adminStation !== "marilao" &&
      account.adminStation !== "monumento";
  });
  if (invalidAdmins.length > 0) {
    throw new Error(
      "May admin account na walang valid adminStation (marilao o monumento). " +
      "Itakda muna ang tamang terminal assignment bago mag-refresh."
    );
  }

  const userIds = recognized.map(function (account) {
    return account.userId;
  }).filter(Boolean);
  if (new Set(userIds).size !== userIds.length) {
    throw new Error("May duplicate userId sa /users. Ayusin muna ang account records.");
  }

  const plates = activeDrivers.map(function (driver) {
    return driver.plateNumber.replace(/[^A-Z0-9]/g, "");
  });
  if (new Set(plates).size !== plates.length) {
    throw new Error("May duplicate plateNumber sa active driver accounts.");
  }

  activeDrivers.sort(function (a, b) {
    return a.userId.localeCompare(b.userId);
  });
  admins.sort(function (a, b) {
    return a.userId.localeCompare(b.userId);
  });
  superadmins.sort(function (a, b) {
    return a.userId.localeCompare(b.userId);
  });

  return {
    all: recognized,
    activeMembers: activeMembers,
    activeDrivers: activeDrivers,
    admins: admins,
    superadmins: superadmins,
  };
}

// --------------------------------------------------------------------- settings
async function loadSettings(drivers) {
  // Reset all non-account settings with the operational dataset refresh.
  // These are the system defaults, not values copied from the old test data.
  const tiers = {
    "16": 32,
    "22": 44,
  };

  drivers.forEach(function (driver) {
    if (driver.seater > 0 && !Number.isFinite(Number(tiers[String(driver.seater)]))) {
      tiers[String(driver.seater)] = driver.seater * 2;
    }
  });

  return {
    tiers: tiers,
    cutoffHour: 21,
    nextQueueRevision: 1,
  };
}

// --------------------------------------------------------------------- dataset generation
function validateDatasetWindow(dataset, asOf) {
  const local = manilaParts(asOf);
  // Start of the first day of the current plus previous two Manila calendar months.
  const windowStart = new Date(Date.UTC(
    local.year,
    local.month - DATA_WINDOW_MONTHS,
    1,
    -8,
    0,
    0,
    0
  ));
  const records = [];

  Object.keys(dataset.collections).forEach(function (collection) {
    dataset.collections[collection].forEach(function (item) {
      records.push({
        collection: collection,
        id: item.id,
        createdAt: item.data.createdAt,
      });
    });
  });
  dataset.notifications.forEach(function (item) {
    records.push({
      collection: "users/{phone}/notifications",
      id: item.id,
      createdAt: item.data.createdAt,
    });
  });

  const outsideWindow = records.filter(function (record) {
    const createdAt = record.createdAt &&
      typeof record.createdAt.toDate === "function"
      ? record.createdAt.toDate()
      : null;
    return !createdAt || createdAt < windowStart || createdAt > asOf;
  });
  if (outsideWindow.length > 0) {
    throw new Error(
      "May generated record na wala sa " + DATA_WINDOW_MONTHS +
      "-month data window: " +
      outsideWindow.slice(0, 5).map(function (record) {
        return record.collection + "/" + record.id;
      }).join(", ")
    );
  }
}

function buildDataset(accounts, settings) {
  const drivers = accounts.activeDrivers;
  const admins = accounts.admins;
  const superadmin = accounts.superadmins[0];
  const adminActor = admins[0];

  const collections = {
    queue: [],
    incidents: [],
    ledger: [],
    donations: [],
    emergencyContacts: [],
    activityLogs: [],
  };
  const notifications = [];
  const add = function (collection, id, data) {
    collections[collection].push({
      id: id,
      ref: db.collection(collection).doc(id),
      data: data,
    });
  };
  const notification = function (account, id, data) {
    notifications.push({
      userDocumentId: account.docId,
      id: id,
      ref: db.collection("users")
        .doc(account.docId)
        .collection("notifications")
        .doc(id),
      data: data,
    });
  };

  const driverName = function (driver) {
    return fullName(driver) || "Driver";
  };

  // One current queue entry for every active driver, split across both routes.
  const routePositions = { marilao: 0, monumento: 0 };
  drivers.forEach(function (driver, index) {
    const station = index % 2 === 0 ? "marilao" : "monumento";
    const route = station === "marilao" ? ROUTE_AM : ROUTE_MA;
    routePositions[station] += 1;
    const joinedAt = recentDate(0, 5, Math.min(55, index));
    const id = "queue_" + safeId(driver.userId) + "_daily";
    add("queue", id, {
      id: id,
      driverId: driver.userId,
      plateNumber: driver.plateNumber,
      driverName: driverName(driver),
      route: route,
      position: routePositions[station],
      status: "Nakapila",
      isCancelled: false,
      time: timeLabel(joinedAt),
      createdAt: timestamp(joinedAt),
    });
  });

  // Dispatch and earnings history stay within the current and previous two calendar months.
  // Every active driver receives at least one matching queue + ledger record.
  const eventCount = Math.max(drivers.length, 36);
  const dispatchEvents = [];
  for (let i = 0; i < eventCount; i++) {
    const driver = drivers[i % drivers.length];
    const monthOffset = i % DATA_WINDOW_MONTHS;
    const cycle = Math.floor(i / 6);
    const dispatchAt = monthOffset === 0
      ? recentDate(
          cycle % 6,
          6 + ((cycle * 2) % 11),
          (10 + cycle * 7) % 60
        )
      : monthDate(monthOffset, [5, 13, 22][cycle % 3], 7 + (cycle % 8), (cycle * 11) % 60);

    const joinedAt = addMinutes(dispatchAt, -35);
    const route = (i + 1) % 2 === 0 ? ROUTE_AM : ROUTE_MA;
    const seater = driver.seater > 0 ? driver.seater : 16;
    const deduction = amountForSeat(seater, settings.tiers);
    const gross = 1050 + ((i * 73) % 620);
    const net = Math.max(0, gross - deduction);
    const status = monthOffset === 0 && cycle < 2
      ? "dispatched"
      : "completed";

    const queueId = "queue_" + safeId(driver.userId) +
      "_history_" + String(monthOffset + 1).padStart(2, "0") +
      "_" + String(cycle + 1).padStart(2, "0");

    const queueData = {
      id: queueId,
      driverId: driver.userId,
      plateNumber: driver.plateNumber,
      driverName: driverName(driver),
      route: route,
      position: 0,
      status: status,
      isCancelled: false,
      income: gross,
      otherFees: deduction,
      netAmount: net,
      time: timeLabel(joinedAt),
      createdAt: timestamp(joinedAt),
      dispatchedAt: timestamp(dispatchAt),
      updatedAt: timestamp(dispatchAt),
    };
    if (status === "completed") {
      queueData.endTravelAt = timestamp(addMinutes(dispatchAt, 150 + (i % 5) * 20));
    }
    add("queue", queueId, queueData);

    const ledgerId = queueId + "_dispatch";
    add("ledger", ledgerId, {
      id: ledgerId,
      driverId: driver.userId,
      driverName: driverName(driver),
      plateNumber: driver.plateNumber,
      queueId: queueId,
      date: dateLabel(dispatchAt),
      amount: net,
      grossAmount: gross,
      otherFees: deduction,
      netAmount: net,
      seater: seater,
      seaterDeduction: deduction,
      deductionCustomized: false,
      feeType: "Dispatch",
      status: "Bayad",
      source: "queue_dispatch",
      createdAt: timestamp(dispatchAt),
    });

    dispatchEvents.push({
      id: queueId,
      driver: driver,
      route: route,
      at: dispatchAt,
      gross: gross,
      deduction: deduction,
      net: net,
      ledgerId: ledgerId,
    });
  }

  // A cancelled entry remains in history, not among the active queue rows.
  const cancelledDriver = drivers[1];
  const cancelledAt = recentDate(0, 5, 45);
  const cancelledId = "queue_" + safeId(cancelledDriver.userId) + "_cancelled";
  add("queue", cancelledId, {
    id: cancelledId,
    driverId: cancelledDriver.userId,
    plateNumber: cancelledDriver.plateNumber,
    driverName: driverName(cancelledDriver),
    route: ROUTE_AM,
    position: 0,
    status: "cancelled",
    isCancelled: true,
    time: timeLabel(recentDate(0, 5, 15)),
    createdAt: timestamp(recentDate(0, 5, 15)),
    cancelledAt: timestamp(cancelledAt),
    updatedAt: timestamp(cancelledAt),
  });

  // Incident records use actual stops/terminal points on the NLEX corridor.
  const incidentTemplates = [
    {
      title: "Flat tire sa likurang gulong",
      location: NLEX_ROUTE_LOCATIONS.marilaoNlex,
      description: "Nabutas ang likurang gulong habang bumibiyahe sa NLEX. Naipatawag ang roadside assistance at naipasuri ang gulong bago ipinagpatuloy ang biyahe.",
      daysBack: 0, hour: 7, minute: 20, penalty: 0, resolved: false,
    },
    {
      title: "Pag-init ng makina",
      location: NLEX_ROUTE_LOCATIONS.malhacanNlex,
      description: "Tumaas ang temperatura ng makina malapit sa Malhacan Road interchange. Itinigil muna ang biyahe sa ligtas na lugar at sinuri ang cooling system.",
      daysBack: 0, hour: 9, minute: 10, penalty: 0, resolved: false,
    },
    {
      title: "Minor collision sa expressway",
      location: NLEX_ROUTE_LOCATIONS.caloocanNlexComplex,
      description: "Nagkaroon ng bahagyang banggaan habang mabagal ang takbo ng trapiko sa bahagi ng NLEX Complex. Walang naiulat na nasaktan at na-inspeksyon ang sasakyan.",
      daysBack: 2, hour: 14, minute: 35, penalty: 250, resolved: true,
    },
    {
      title: "Nasirang side mirror",
      location: NLEX_ROUTE_LOCATIONS.valenzuelaMindanao,
      description: "Natamaan ang kanang side mirror habang dumaraan sa bahagi ng Mindanao Avenue interchange. Napalitan ang piyesa at nasuri ang pagkakakabit.",
      daysBack: 5, hour: 11, minute: 5, penalty: 0, resolved: true,
    },
    {
      title: "Babala sa sistema ng preno",
      location: NLEX_ROUTE_LOCATIONS.valenzuelaArty,
      description: "Napansin ng driver na lumambot ang pedal ng preno habang nasa NLEX. Inihinto muna ang operasyon at ipinatingin ang brake line bago muling bumiyahe.",
      daysBack: 1, hour: 8, minute: 40, penalty: 0, resolved: false,
    },
    {
      title: "Minor traffic citation",
      location: NLEX_ROUTE_LOCATIONS.monumentoSideTerminal,
      description: "Nakatanggap ng citation matapos hindi masunod ang itinakdang loading at unloading area malapit sa Samson Road terminal.",
      daysBack: 18, hour: 10, minute: 25, penalty: 200, resolved: true,
    },
  ];

  const allowedRouteLocations = new Set(Object.values(NLEX_ROUTE_LOCATIONS));
  const offRouteIncidents = incidentTemplates.filter(function (template) {
    return !allowedRouteLocations.has(template.location);
  });
  if (offRouteIncidents.length > 0) {
    throw new Error(
      "May incident location na wala sa verified NLEX route stops: " +
      offRouteIncidents.map(function (template) {
        return template.location;
      }).join(", ")
    );
  }

  const incidents = incidentTemplates.map(function (template, index) {
    const driver = drivers[index % drivers.length];
    const id = "incident_" + String(index + 1).padStart(2, "0");
    const createdAt = recentDate(
      template.daysBack,
      template.hour,
      template.minute
    );
    const resolvedAt = template.resolved
      ? addMinutes(createdAt, 95 + index * 13)
      : null;
    const statusLabel = template.resolved ? "Naayos" : "Hindi pa naayos";
    const incidentData = {
      id: id,
      driverId: driver.userId,
      driverName: driverName(driver),
      plateNumber: driver.plateNumber,
      title: template.title,
      location: template.location,
      description: template.description,
      date: dateLabel(createdAt),
      penalty: template.penalty,
      reportedBy: adminActor.userId,
      sentToDriver: true,
      isResolved: template.resolved,
      status: template.resolved ? "resolved" : "open",
      createdAt: timestamp(createdAt),
      updatedAt: timestamp(resolvedAt || createdAt),
    };
    if (resolvedAt) incidentData.resolvedAt = timestamp(resolvedAt);
    add("incidents", id, incidentData);

    return {
      id: id,
      driver: driver,
      title: template.title,
      location: template.location,
      date: createdAt,
      resolved: template.resolved,
      penalty: template.penalty,
      statusLabel: statusLabel,
    };
  });

  // Fine ledger entries are separate from the driver's dispatch earnings.
  incidents.filter(function (incident) {
    return incident.penalty > 0;
  }).forEach(function (incident, index) {
    const fineId = "fee_" + safeId(incident.driver.userId) + "_" +
      String(index + 1).padStart(2, "0");
    const paidAt = addMinutes(incident.date, 120);
    add("ledger", fineId, {
      id: fineId,
      driverId: incident.driver.userId,
      plateNumber: incident.driver.plateNumber,
      driverName: driverName(incident.driver),
      date: dateLabel(paidAt),
      amount: incident.penalty,
      grossAmount: incident.penalty,
      otherFees: 0,
      netAmount: incident.penalty,
      feeType: "Multa",
      status: "Bayad",
      source: "ledger",
      createdAt: timestamp(paidAt),
    });
  });

  // Cooperative fund activity with both incoming donations and expenses.
  const donationEntries = [
    [0, 12, 0, "Mga Kasapi ng Kooperatiba", 2500, "Ambag para sa tulong at maintenance ng mga sasakyan", "Mga Kasapi", "donate"],
    [1, 10, 30, "Samahan ng mga Driver", 1500, "Karagdagang pondo para sa agarang pagkukumpuni", "Kooperatiba", "donate"],
    [7, 9, 15, "Mga Kaibigan ng Kooperatiba", 3000, "Suporta para sa kaligtasan at operasyon", "Donasyon", "donate"],
    [14, 14, 0, "Pondo ng Kooperatiba", 650, "Mga gamit sa opisina at dokumentasyon", "Operasyon", "gamit"],
    [45, 11, 20, "Pondo ng Kooperatiba", 1200, "Tulong sa agarang pangangailangan ng isang kasapi", "Tulong sa Kasapi", "gamit"],
    [59, 13, 40, "Mga Kasapi ng Kooperatiba", 1800, "Ambag para sa regular na maintenance", "Mga Kasapi", "donate"],
  ];

  donationEntries.forEach(function (entry, index) {
    const date = recentDate(entry[0], entry[1], entry[2]);
    const id = "donation_cooperative_" + String(index + 1).padStart(2, "0");
    add("donations", id, {
      id: id,
      donor: entry[3],
      amount: entry[4],
      date: dateLabel(date),
      note: entry[5],
      source: entry[6],
      type: entry[7],
      createdAt: timestamp(date),
    });
  });

  // Emergency directory uses the actual phone numbers already stored on admin accounts.
  const superadminName = fullName(superadmin) || "Superadmin";
  add("emergencyContacts", "contact_national_emergency", {
    name: "Pambansang Emergency Hotline",
    number: "911",
    addedBy: superadminName,
    isActive: true,
    createdAt: timestamp(daysAgo(30, 9, 0)),
  });

  admins.forEach(function (account, index) {
    const stationLabel = account.adminStation
      ? " – " + account.adminStation.charAt(0).toUpperCase() +
        account.adminStation.slice(1)
      : "";
    const id = "contact_office_" + safeId(account.userId);
    add("emergencyContacts", id, {
      name: (index === 0 ? "Dispatcher" : "Tanggapan") +
        stationLabel + " — " + fullName(account),
      number: account.phoneNumber,
      addedBy: superadminName,
      isActive: true,
      createdAt: timestamp(daysAgo(Math.min(20 + index, 59), 9, 0)),
    });
  });

  // Notifications are stored under each account. IDs match Cloud Function
  // conventions so retries do not create duplicates.
  accounts.activeMembers.forEach(function (member) {
    incidents.forEach(function (incident) {
      const title = incident.resolved
        ? "Bagong Insidente • Naayos"
        : "Bagong Insidente sa Kooperatiba";
      const body = [
        incident.driver.plateNumber,
        incident.title,
        incident.location,
        incident.statusLabel,
      ].filter(Boolean).join(" • ").slice(0, 180);
      const id = "incident_" + incident.id;

      notification(member, id, {
        type: "incident",
        title: title,
        body: body,
        entityId: incident.id,
        incidentId: incident.id,
        driverId: incident.driver.userId,
        plateNumber: incident.driver.plateNumber,
        incidentTitle: incident.title,
        location: incident.location,
        status: incident.resolved ? "resolved" : "open",
        statusLabel: incident.statusLabel,
        read: false,
        createdAt: timestamp(incident.date),
      });
    });

    const latestDispatch = dispatchEvents[0];
    const isTargetDriver =
      member.role === "driver" &&
      member.userId === latestDispatch.driver.userId;
    const dispatchTitle = isTargetDriver
      ? "Na-dispatch ka!"
      : "May Na-dispatch na Driver";
    const dispatchBody = [
      fullName(latestDispatch.driver),
      latestDispatch.driver.plateNumber,
      latestDispatch.route,
    ].filter(Boolean).join(" • ").slice(0, 180);
    const dispatchNotificationId = "dispatch_" + latestDispatch.id;

    notification(member, dispatchNotificationId, {
      type: "dispatch",
      title: dispatchTitle,
      body: dispatchBody,
      entityId: latestDispatch.id,
      driverId: latestDispatch.driver.userId,
      driverName: fullName(latestDispatch.driver),
      plateNumber: latestDispatch.driver.plateNumber,
      route: latestDispatch.route,
      read: false,
      createdAt: timestamp(latestDispatch.at),
    });
  });

  // Superadmin activity trail uses real account identities and each writer's own phone.
  const activityEntries = [
    {
      actor: adminActor,
      action: "Dispatch",
      detail: dispatchEvents[0].driver.plateNumber + " → " + dispatchEvents[0].route,
      date: dispatchEvents[0].at,
    },
    {
      actor: dispatchEvents[1].driver,
      action: "Tapos Na Bumyahe",
      detail: dispatchEvents[1].driver.plateNumber,
      date: addMinutes(dispatchEvents[1].at, 150),
    },
    {
      actor: adminActor,
      action: "Bagong Insidente",
      detail: incidents[0].driver.plateNumber + " • " + incidents[0].title,
      date: incidents[0].date,
    },
    {
      actor: adminActor,
      action: "Naayos na Insidente",
      detail: incidents[2].driver.plateNumber + " • " + incidents[2].title,
      date: incidents[2].date,
    },
    {
      actor: superadmin,
      action: "Settings",
      detail: "Oras ng reset: " +
        (((settings.cutoffHour + 11) % 12) + 1) + ":00 " +
        (settings.cutoffHour < 12 ? "AM" : "PM") +
        " | Bawas kada upuan: " +
        Object.keys(settings.tiers).sort(function (a, b) {
          return Number(a) - Number(b);
        }).map(function (key) {
          return key + "=" + settings.tiers[key];
        }).join(", "),
      date: daysAgo(1, 16, 10),
    },
    {
      actor: adminActor,
      action: "I-record ang Bayad ng Multa",
      detail: incidents[2].driver.plateNumber + " • ₱" + incidents[2].penalty,
      date: addMinutes(incidents[2].date, 120),
    },
  ];

  activityEntries.forEach(function (entry, index) {
    const id = "log_operations_" + String(index + 1).padStart(2, "0");
    add("activityLogs", id, {
      actorName: fullName(entry.actor),
      actorPhone: entry.actor.phoneNumber,
      actorRole: accountRoleLabel(entry.actor.role),
      action: entry.action,
      detail: entry.detail,
      createdAt: timestamp(entry.date),
    });
  });

  return {
    collections: collections,
    notifications: notifications,
    counts: {
      activeDrivers: drivers.length,
      activeAdmins: admins.length,
      activeSuperadmins: accounts.superadmins.length,
      activeMembers: accounts.activeMembers.length,
      activeQueue: drivers.length,
      historyQueue: dispatchEvents.length,
      incidents: incidents.length,
      dispatchLedger: dispatchEvents.length,
      fineLedger: incidents.filter(function (incident) {
        return incident.penalty > 0;
      }).length,
      donations: donationEntries.length,
      emergencyContacts: admins.length + 1,
      activityLogs: activityEntries.length,
      notifications: notifications.length,
    },
  };
}

// --------------------------------------------------------------------- clean-slate cleanup
const PRESERVED_ACCOUNT_COLLECTIONS = new Set([
  "users",
  "userSecrets",
  "driverPlateClaims",
  "secureSessions",
]);

/**
 * Collect every existing document in a collection and all of its
 * subcollections. Root account documents are handled separately so that
 * their profile documents are never included in the deletion plan.
 */
async function collectCollectionTree(collectionRef, refsByPath, counts, groupName) {
  const snapshot = await collectionRef.get();
  counts[groupName] = (counts[groupName] || 0) + snapshot.size;

  for (const document of snapshot.docs) {
    refsByPath.set(document.ref.path, document.ref);

    const childCollections = await document.ref.listCollections();
    for (const childCollection of childCollections) {
      await collectCollectionTree(
        childCollection,
        refsByPath,
        counts,
        groupName
      );
    }
  }
}

/**
 * Preserve account profiles, password hashes, Firebase Auth identities,
 * plate claims, and secure login sessions. Clear every other root collection
 * recursively. Under /users, retain each account document but clear every
 * nested collection (including notifications).
 */
async function collectNonAccountDocumentRefs() {
  const refsByPath = new Map();
  const counts = {};
  const rootCollections = await db.listCollections();

  for (const rootCollection of rootCollections) {
    if (rootCollection.id === "users") {
      const accountSnapshot = await rootCollection.get();
      for (const accountDocument of accountSnapshot.docs) {
        const childCollections = await accountDocument.ref.listCollections();
        for (const childCollection of childCollections) {
          await collectCollectionTree(
            childCollection,
            refsByPath,
            counts,
            "users/{account} subcollections"
          );
        }
      }
      continue;
    }

    if (PRESERVED_ACCOUNT_COLLECTIONS.has(rootCollection.id)) {
      continue;
    }

    await collectCollectionTree(
      rootCollection,
      refsByPath,
      counts,
      rootCollection.id
    );
  }

  return {
    refs: Array.from(refsByPath.values()),
    counts: counts,
  };
}

function cleanupGroupForPath(documentPath) {
  const parts = documentPath.split("/");
  return parts[0] === "users"
    ? "users/{account} subcollections"
    : parts[0];
}

/**
 * Documents which are also targets of the new dataset are overwritten with
 * fresh records. All remaining non-account documents are deleted.
 */
function buildCleanSlatePlan(existingData, writeOperations) {
  const writePaths = new Set(
    writeOperations.map(function (operation) {
      return operation.ref.path;
    })
  );
  const deleteRefs = [];
  const replacedCounts = {};
  const deleteCounts = {};

  existingData.refs.forEach(function (ref) {
    const group = cleanupGroupForPath(ref.path);
    if (writePaths.has(ref.path)) {
      replacedCounts[group] = (replacedCounts[group] || 0) + 1;
    } else {
      deleteRefs.push(ref);
      deleteCounts[group] = (deleteCounts[group] || 0) + 1;
    }
  });

  return {
    deleteRefs: deleteRefs,
    replacedCounts: replacedCounts,
    deleteCounts: deleteCounts,
  };
}

function logCleanupCounts(title, counts) {
  const entries = Object.keys(counts).sort();
  console.log(title);
  if (entries.length === 0) {
    console.log("  (none)");
    return;
  }
  entries.forEach(function (name) {
    console.log("  " + name + ": " + counts[name]);
  });
}

async function commitDeletes(refs) {
  for (let start = 0; start < refs.length; start += 450) {
    const batch = db.batch();
    refs.slice(start, start + 450).forEach(function (ref) {
      batch.delete(ref);
    });
    await batch.commit();
  }
}

async function commitWrites(operations) {
  for (let start = 0; start < operations.length; start += 450) {
    const batch = db.batch();
    operations.slice(start, start + 450).forEach(function (operation) {
      if (operation.merge) {
        batch.set(operation.ref, operation.data, { merge: true });
      } else {
        batch.set(operation.ref, operation.data);
      }
    });
    await batch.commit();
  }
}

function buildWriteOperations(dataset, settings, oldQueueRevision) {
  const operations = [];
  Object.keys(dataset.collections).forEach(function (collection) {
    dataset.collections[collection].forEach(function (item) {
      operations.push({
        ref: item.ref,
        data: item.data,
        merge: false,
      });
    });
  });

  dataset.notifications.forEach(function (item) {
    operations.push({
      ref: item.ref,
      data: item.data,
      merge: false,
    });
  });

  const currentTime = new Date();
  const tiersRef = db.collection("settings").doc("deductionTiers");
  const rotationRef = db.collection("settings").doc("queueRotation");
  const queueControlRef = db.collection("settings").doc("queueControl");

  operations.push({
    ref: tiersRef,
    data: settings.tiers,
    merge: false,
  });
  operations.push({
    ref: rotationRef,
    data: {
      cutoffHour: settings.cutoffHour,
      lastQueueDay: queueDayId(currentTime, settings.cutoffHour),
      updatedAt: FieldValue.serverTimestamp(),
    },
    merge: false,
  });
  operations.push({
    ref: queueControlRef,
    data: {
      revision: oldQueueRevision,
      updatedAt: FieldValue.serverTimestamp(),
    },
    merge: false,
  });

  return operations;
}

function buildManifest(dataset) {
  const collectionIds = {};
  Object.keys(dataset.collections).forEach(function (collection) {
    collectionIds[collection] = dataset.collections[collection].map(function (item) {
      return item.id;
    });
  });

  const groupedNotifications = new Map();
  dataset.notifications.forEach(function (item) {
    if (!groupedNotifications.has(item.userDocumentId)) {
      groupedNotifications.set(item.userDocumentId, []);
    }
    groupedNotifications.get(item.userDocumentId).push(item.id);
  });

  return {
    version: 2,
    collectionIds: collectionIds,
    notifications: Array.from(groupedNotifications.entries()).map(function (entry) {
      return {
        userDocumentId: entry[0],
        ids: entry[1],
      };
    }),
    updatedAt: FieldValue.serverTimestamp(),
  };
}

async function main() {
  const accounts = await loadExistingAccounts();
  const settings = await loadSettings(accounts.activeDrivers);
  const dataset = buildDataset(accounts, settings);
  validateDatasetWindow(dataset, new Date());

  const operations = buildWriteOperations(
    dataset,
    settings,
    settings.nextQueueRevision
  );
  const manifest = buildManifest(dataset);
  const existingData = await collectNonAccountDocumentRefs();
  const cleanup = buildCleanSlatePlan(existingData, operations);

  console.log("");
  console.log("EnerJeep — Full Operational Data Reset");
  console.log("Firebase project: " + projectId);
  console.log("Mode: " + (applyChanges ? "APPLY" : "PREVIEW ONLY"));
  console.log("");
  console.log("Accounts preserved:");
  console.log("  Superadmin profiles: " + dataset.counts.activeSuperadmins);
  console.log("  Admin profiles: " + dataset.counts.activeAdmins);
  console.log("  Active driver profiles: " + dataset.counts.activeDrivers);
  console.log("  Existing Philippine E.164 phone identities: " + accounts.all.length + " (PRESERVED)");
  console.log("  Password hashes, Firebase Auth, plate claims, secure sessions: PRESERVED");
  console.log("  No random or unverified account phone numbers are generated.");
  console.log("");
  console.log("New generated operational records:");
  console.log("  Date scope: current Manila calendar month + previous two months only");
  console.log("  Current queue entries: " + dataset.counts.activeQueue);
  console.log("  Dispatch history: " + dataset.counts.historyQueue);
  console.log("  Incident reports: " + dataset.counts.incidents);
  console.log("  Dispatch earnings: " + dataset.counts.dispatchLedger);
  console.log("  Paid fines: " + dataset.counts.fineLedger);
  console.log("  Cooperative fund entries: " + dataset.counts.donations);
  console.log("  Emergency contacts: " + dataset.counts.emergencyContacts);
  console.log("  Activity records: " + dataset.counts.activityLogs);
  console.log("  Per-account notifications: " + dataset.counts.notifications);
  console.log("");
  logCleanupCounts("Existing non-account records to be replaced:", cleanup.replacedCounts);
  logCleanupCounts("Existing non-account records to be deleted:", cleanup.deleteCounts);
  console.log("");
  console.log("Old Kita collection documents to delete: " +
    (cleanup.deleteCounts.kita || 0));
  const financeCollections = ["kita", "donations", "ledger"];
  const financeRecordsChanged = financeCollections.reduce(function (total, name) {
    return total + (cleanup.deleteCounts[name] || 0) +
      (cleanup.replacedCounts[name] || 0);
  }, 0);
  console.log("Existing cooperative-fund/earnings records replaced or deleted: " +
    financeRecordsChanged);
  console.log("Old incident records replaced or deleted: " +
    (cleanup.deleteCounts.incidents || 0) +
    (cleanup.replacedCounts.incidents || 0));
  console.log("Total old documents to delete: " + cleanup.deleteRefs.length);
  console.log("Total existing documents to replace with fresh records: " +
    Object.keys(cleanup.replacedCounts).reduce(function (total, key) {
      return total + cleanup.replacedCounts[key];
    }, 0));
  console.log("");
  console.log("No account profile or account credential document is in the deletion plan.");
  console.log("All other Firestore root collections are cleaned recursively, including legacy test collections.");
  console.log("All subcollections beneath each /users/{account} document are cleaned (including old notifications).");

  if (!applyChanges) {
    console.log("");
    console.log("PREVIEW ONLY — WALANG BINAGO SA FIRESTORE.");
    console.log("Suriin ang project ID at bilang sa itaas. Mag-backup muna bago mag-apply.");
    console.log("Kapag tama ang preview, ulitin ang command at idagdag ang --apply.");
    return;
  }

  // Write the fresh replacement records first. This makes a failed write
  // recoverable without first removing the currently stored operational data.
  console.log("");
  console.log("Isinusulat ang bagong three-month operational records...");
  await commitWrites(operations);

  console.log("Nililinis ang lahat ng lumang non-account records...");
  await commitDeletes(cleanup.deleteRefs);

  console.log("Ina-update ang bagong internal operations index...");
  await INDEX_REF.set(manifest, { merge: false });

  console.log("");
  console.log("Matagumpay ang full operational data reset.");
  console.log("Napanatili ang lahat ng account profiles, credentials, Auth users, plate claims at secure sessions.");
  console.log("Ang natitirang operational records ay bagong dataset lamang na sakop ang kasalukuyan at nakaraang dalawang calendar month.");
  console.log("I-restart ang EnerJeep app upang i-refresh ang lahat ng screens.");
}
main()
  .then(function () {
    return admin.app().delete();
  })
  .catch(async function (error) {
    console.error("");
    console.error("Hindi natapos ang data refresh:", error.stack || error.message);
    try {
      await admin.app().delete();
    } catch (_) {
      // Ignore shutdown errors after reporting the original failure.
    }
    process.exitCode = 1;
  });
