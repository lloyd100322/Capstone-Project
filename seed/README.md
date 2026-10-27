# Seed data (demo dataset)

Realistic test data for demonstrating EnerJeep: 8 accounts, queue
entries on both routes, incidents, earnings, donations, settings,
emergency contacts, and audit-log samples — so the app can be shown
working immediately, without manual data entry.

## What gets created

| Collection | Contents |
|---|---|
| `users` + `userSecrets` | 1 superadmin, 1 admin, 6 drivers (Filipino names, PH plates, seaters 16/22) — all login-ready |
| `queue` | 5 pila sa **Marilao – Monumento**, 1 dispatched (#1 ng **Monumento – Marilao**), 1 completed (Kasaysayan), 1 Nakansela |
| `incidents` | 2 bukas + 2 naayos (may multa ang isa) — mga lugar sa Monumento/Marilao |
| `kita` + `ledger` | Pamasahe/Diesel/Multa/Bawas records + dispatch ledger (3 araw) |
| `donations` | 3 donations |
| `settings` | `deductionTiers` (16→₱32, 22→₱44) + `queueRotation` (cutoffHour 21) |
| `emergencyContacts` | 911 + 2 lokal na contact |
| `activityLogs` | 5 audit entries |

## Demo logins

Password for **all** seeded accounts: `Demo123!`

| Role | Phone number |
|---|---|
| Superadmin | `+639171000001` (Ramon Villanueva) |
| Admin | `+639171000002` (Maria Santos) |
| Drivers | `+639171000003` … `+639171000008` (Juan dela Cruz, Pedro Reyes, Jose Ramos, Ana Lim, Rafael Garcia, Miguel Torres) |

> The password hash is generated with the same algorithm as the app
> (PBKDF2-HMAC-SHA256, 12,000 iterations) — the accounts work on the
> real login screen.

## OTP without SMS cost (IMPORTANT for demos)

Phone login needs an SMS OTP. For development and presentations, use
**Firebase test numbers** (no SMS is sent, no cost):

1. Firebase console → **Authentication → Sign-in method → Phone** →
   **Phone numbers for testing**.
2. Add e.g. `+639171000001` with code `123456` (do the same for the
   other numbers you will demo).
3. On the login screen, type the number — the OTP `123456` will be
   accepted instantly.

## How to run

1. Get a **service account key**: Firebase console → Project settings →
   Service accounts → *Generate new private key*. Save it **outside**
   the repository (never commit it).
2. Install and run:

   ```bash
   cd seed
   npm install
   node seed.js --serviceAccount /path/to/serviceAccount.json
   ```

   (Or set `GOOGLE_APPLICATION_CREDENTIALS` and run `node seed.js`.)

3. Options:
   - `--wipe` — deletes existing docs in the seeded collections first.
     **Never** use this on a project with real data.

## Notes

- Re-running the script without `--wipe` overwrites the same seeded
  documents (fixed IDs) — safe to repeat.
- The seed writes with the Admin SDK, which bypasses Firestore rules —
  this is a maintainer tool, not an app feature.
- Do not mix seed data with real records (see
  `docs/SCOPE-AND-LIMITATIONS.md`).
