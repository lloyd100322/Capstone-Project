/**
 * Removes legacy driverPhone fields from member-readable incident documents.
 *
 * Dry run (default):
 *   node scripts/remove-incident-phone-fields.js
 *
 * Apply after verifying the Firebase project and taking a suitable backup:
 *   node scripts/remove-incident-phone-fields.js --apply
 *
 * Uses Application Default Credentials / GOOGLE_APPLICATION_CREDENTIALS.
 * Logs document counts only; it never prints phone numbers or incident data.
 */
"use strict";

const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const APPLY = process.argv.includes("--apply");
const PAGE_SIZE = 400;

async function main() {
  const projectId =
    admin.app().options.projectId ||
    process.env.GCLOUD_PROJECT ||
    process.env.GOOGLE_CLOUD_PROJECT ||
    "unknown";

  console.log(`Project: ${projectId}`);
  console.log(
    APPLY
      ? "Mode: APPLY (will remove driverPhone fields)"
      : "Mode: DRY RUN (no documents will be changed)"
  );

  let lastDocument = null;
  let total = 0;

  while (true) {
    let query = db.collection("incidents")
      .where("driverPhone", "!=", null)
      .orderBy("driverPhone")
      .limit(PAGE_SIZE);

    if (lastDocument) query = query.startAfter(lastDocument);

    const snapshot = await query.get();
    if (snapshot.empty) break;

    total += snapshot.size;
    if (APPLY) {
      const batch = db.batch();
      snapshot.docs.forEach((document) => {
        batch.update(document.ref, {
          driverPhone: admin.firestore.FieldValue.delete(),
        });
      });
      await batch.commit();
    }

    lastDocument = snapshot.docs[snapshot.docs.length - 1];
  }

  console.log(`${APPLY ? "Removed" : "Found"} ${total} legacy incident phone field(s).`);
  if (!APPLY) {
    console.log("Review the project, back up production data, then rerun with --apply.");
  }
}

main().catch((error) => {
  console.error("Incident phone-field cleanup failed:", error.message || error);
  process.exitCode = 1;
});
