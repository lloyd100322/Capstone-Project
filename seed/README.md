# Seed data (demo dataset)

Realistic test data for demonstrating EnerJeep: 35 accounts, queue
entries on both routes, incidents, earnings, donations, settings,
emergency contacts, and audit-log samples — so the app can be shown
working immediately, without manual data entry.

## What gets created

| Collection | Contents |
|---|---|
| `users` + `userSecrets` | 1 superadmin, 1 admin, 33 drivers — tunay na listahan ng terminal (pangalan at plate totoo, galing sa listahan ng terminal; apelyido/cellphone/lisensya/seater/emergency ay demo-filled) — all login-ready |
| `queue` | 5 pila sa **Marilao – Monumento**, 1 dispatched (#1 ng **Monumento – Marilao**), 1 completed (Kasaysayan), 1 Nakansela |
| `incidents` | 2 bukas + 2 naayos (may multa ang isa) — mga lugar sa Monumento/Marilao |
| `kita` + `ledger` | Pamasahe/Diesel/Multa/Bawas records + dispatch ledger (3 araw) |
| `donations` | 5 entries (3 donate, 2 gamit - fund up/down) |
| `settings` | `deductionTiers` (16→₱32, 22→₱44) + `queueRotation` (cutoffHour 21) |
| `emergencyContacts` | 911 + 2 lokal na contact |
| `activityLogs` | 5 audit entries |

## Demo logins

Password for **all** seeded accounts: `Demo123!`

| Role | Phone number |
|---|---|
| Superadmin | `+639171000001` (Ramon Villanueva) |
| Admin | `+639171000002` (Maria Santos) |
| Drivers | `+639171000003` … `+639171000035` (33 tunay na drayber: Roger TXA 448, Jess DGT 463, Joel PWU 904, … , Marlon DLH 781 — tingnan ang `roster` sa `seed.js`) |

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

## How to run (Windows / PowerShell)

0. **Kailangan ng Node.js** — i-download/install ang LTS mula sa
   https://nodejs.org (kung wala pa). I-check sa PowerShell:
   `node -v` — dapat may laman.

1. **Kunin ang service account key**: Firebase console →
   **Project settings → Service accounts → Generate new private key**.
   I-save ito sa **labas** ng repository (hal. `C:\Users\Dave\Downloads\`)
   — **huwag na huwag** i-commit.

2. Sa PowerShell:

   ```powershell
   cd C:\Users\Dave\AndroidStudioProjects\Capstone-Project3\seed
   npm install
   node seed.js --serviceAccount C:\Users\Dave\Downloads\serviceAccount.json
   ```

   (O kaya: i-set ang `GOOGLE_APPLICATION_CREDENTIALS` at patakbuhin
   lang ang `node seed.js`.)

3. Options:
   - `--wipe` — buburahin muna ang laman ng seeded collections bago
     magsulat. **Huwag** gagamitin sa may totoong datos.

4. Pagkatapos: i-restart ang app — dapat puno na ng datos (pila,
   insidente, kita, donations, Talaan).

## Notes

- Re-running the script without `--wipe` overwrites the same seeded
  documents (fixed IDs) — safe to repeat.
- The seed writes with the Admin SDK, which bypasses Firestore rules —
  this is a maintainer tool, not an app feature.
- Do not mix seed data with real records (see
  `docs/SCOPE-AND-LIMITATIONS.md`).
