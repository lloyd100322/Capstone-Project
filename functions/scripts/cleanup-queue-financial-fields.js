/**
 * Safely removes legacy financial duplicates from driver-readable queue docs.
 *
 * Run from the functions directory:
 *   node scripts/cleanup-queue-financial-fields.js
 *   node scripts/cleanup-queue-financial-fields.js --apply
 *
 * The default is a read-only dry run. --apply is refused unless EVERY
 * affected queue document has a matching deterministic ledger record.
 * A JSON backup is written outside the repository before any update.
 *
 * Authentication: Application Default Credentials (for example,
 * use gcloud auth application-default login or run from Cloud Shell).
 */
"use strict";

const admin = require("firebase-admin");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const dryRun = args.includes("--dry-run");
const projectArg = args.find((arg) => arg.startsWith("--project="));
const projectId =
  (projectArg && projectArg.slice("--project=".length)) ||
  process.env.GCLOUD_PROJECT ||
  process.env.GOOGLE_CLOUD_PROJECT ||
  "enerjeep-b8f3a";

const FINANCIAL_FIELDS = ["income", "otherFees", "netAmount"];
const BATCH_LIMIT = 400;
const NUMBER_TOLERANCE = 0.005;

function hasFinancialFields(data) {
  return FINANCIAL_FIELDS.some((field) =>
    Object.prototype.hasOwnProperty.call(data, field)
  );
}

function sameAmount(left, right) {
  return (
    typeof left === "number" &&
    Number.isFinite(left) &&
    typeof right === "number" &&
    Number.isFinite(right) &&
    Math.abs(left - right) <= NUMBER_TOLERANCE
  );
}

function safeForJson(value) {
  return JSON.parse(JSON.stringify(value));
}

async function findMatchingLedger(db, queueDoc) {
  const queueData = queueDoc.data();
  const ledgerId = queueDoc.id + "_dispatch";
  const ledgerDoc = await db.collection("ledger").doc(ledgerId).get();

  if (!ledgerDoc.exists) {
    return { ok: false, reason: "Missing ledger/" + ledgerId };
  }

  const ledger = ledgerDoc.data() || {};
  const queueIdMatches =
    ledger.queueId === undefined || ledger.queueId === queueDoc.id;

  const matches =
    typeof queueData.driverId === "string" &&
    queueData.driverId.length > 0 &&
    ledger.driverId === queueData.driverId &&
    queueIdMatches &&
    sameAmount(queueData.income, ledger.grossAmount) &&
    sameAmount(queueData.otherFees, ledger.otherFees) &&
    sameAmount(queueData.netAmount, ledger.netAmount);

  return matches
    ? { ok: true, ledgerId }
    : {
        ok: false,
        reason: "Ledger/" + ledgerId + " does not match driverId or financial amounts",
      };
}

async function main() {
  if (apply && dryRun) {
    throw new Error("Use either --apply or --dry-run, not both.");
  }
  if (!projectId || projectId.trim().length === 0) {
    throw new Error("A Firebase project ID is required.");
  }

  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId,
  });

  const db = admin.firestore();
  console.log("Project: " + projectId);
  console.log("Reading queue documents (no data will be changed during this check)...");

  const queueSnapshot = await db.collection("queue").get();
  const affected = queueSnapshot.docs.filter((doc) =>
    hasFinancialFields(doc.data())
  );

  console.log("Queue documents scanned: " + queueSnapshot.size);
  console.log("Documents containing legacy financial fields: " + affected.length);

  if (affected.length === 0) {
    console.log("Nothing to clean up.");
    return;
  }

  const verified = [];
  const unmatched = [];

  for (let offset = 0; offset < affected.length; offset += 50) {
    const group = affected.slice(offset, offset + 50);
    const results = await Promise.all(group.map(async (queueDoc) => ({
      queueDoc,
      match: await findMatchingLedger(db, queueDoc),
    })));

    for (const result of results) {
      if (result.match.ok) verified.push(result);
      else unmatched.push({
        queueId: result.queueDoc.id,
        reason: result.match.reason,
      });
    }
  }

  console.log("Matching ledger records: " + verified.length);
  console.log("Unmatched records: " + unmatched.length);

  if (unmatched.length > 0) {
    console.error("ABORTED: no queue documents were changed.");
    console.error(JSON.stringify(unmatched, null, 2));
    throw new Error(
      "Resolve the unmatched records first. No partial cleanup was attempted."
    );
  }

  if (!apply) {
    console.log("DRY RUN ONLY: no Firestore documents were changed.");
    console.log("After reviewing the counts, run again with --apply to back up and clean these documents.");
    return;
  }

  const backupDirectory = path.join(os.homedir(), "EnerJeepFirestoreBackups");
  fs.mkdirSync(backupDirectory, { recursive: true });

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const backupPath = path.join(
    backupDirectory,
    "queue-financial-fields-" + projectId + "-" + timestamp + ".json"
  );

  const backup = {
    projectId,
    createdAt: new Date().toISOString(),
    note: "Full pre-cleanup queue documents. Financial duplicate fields are removed only after matching ledger verification.",
    documents: verified.map(({ queueDoc }) => ({
      id: queueDoc.id,
      data: safeForJson(queueDoc.data()),
    })),
  };

  fs.writeFileSync(backupPath, JSON.stringify(backup, null, 2), {
    encoding: "utf8",
    flag: "wx",
  });
  console.log("Backup saved: " + backupPath);

  for (let offset = 0; offset < verified.length; offset += BATCH_LIMIT) {
    const group = verified.slice(offset, offset + BATCH_LIMIT);
    const batch = db.batch();

    for (const { queueDoc } of group) {
      const update = {};
      const data = queueDoc.data();
      for (const field of FINANCIAL_FIELDS) {
        if (Object.prototype.hasOwnProperty.call(data, field)) {
          update[field] = admin.firestore.FieldValue.delete();
        }
      }
      batch.update(queueDoc.ref, update);
    }

    await batch.commit();
    console.log("Cleaned " + Math.min(offset + group.length, verified.length) + " of " + verified.length + " queue documents.");
  }

  console.log("Cleanup complete. Ledger records were not modified.");
}

main().catch((error) => {
  console.error("Queue financial cleanup failed:", error.message || error);
  process.exitCode = 1;
});
