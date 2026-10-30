# Firestore Security Rules Tests

Automated checks for `../firestore.rules` — one test per security promise
(end-travel contract, queue-jump block, superadmin-only cut-off hour,
immutable audit log, owner-only password secrets, role-change protection).

## Prerequisites

- Node.js 18+
- Firebase CLI (`npm install -g firebase-tools`)
- A local Java runtime (the Firestore emulator needs it)

## Run

From this folder:

```bash
npm install
cd ..
firebase emulators:exec --only firestore \
  --project enerjeep-rules-test \
  "cd firestore-tests && npm test" \
  -c firebase.json
```

`--project` can be any id **except** your production project — the tests
run entirely against the local emulator and never touch real data.

## Manual verification after deploying rules

After `firebase deploy --only firestore:rules`, confirm in the app:

1. **Tapos Na Bumyahe (driver)** — driver ends travel, the button turns
   red, the entries behind move up. *(This was broken for drivers when
   the rules forbade renumbering other entries.)*
2. **Queue-jump block** — no client should be able to set its own
   `position` to anything but 0 while active.
3. **Mga Setting** — an admin account must NOT be able to change the
   reset hour (superadmin only).
4. **Talaan ng Aktibidad** — the log must now contain the driver's
   "Tapos Na Bumyahe" entries.
