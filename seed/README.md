# Datos ng kooperatiba (database setup)

Realistic starting data for EnerJeep: 35 accounts, queue entries on
both routes, incidents, earnings, donations, settings, emergency
contacts, and audit-log records — so the app can be shown working
immediately, without manual data entry.

## What gets created

| Collection | Contents |
|---|---|
| `users` + `userSecrets` | 1 superadmin, 1 admin, 33 drivers — tunay na listahan ng terminal (pangalan at plate totoo, galing sa listahan ng terminal; apelyido/cellphone/lisensya/seater/emergency ay pinunan) — all login-ready |
| `queue` | 17 sa **Marilao – Monumento**, 16 sa **Monumento – Marilao** (kasama ang muling pila ng na-dispatch), 3 kasaysayan (dispatched/completed), 1 Nakansela |
| `incidents` | 2 bukas + 2 naayos (may multa ang isa) — mga lugar sa Monumento/Marilao |
| `ledger` | Dispatch records ng 3 kumpletong biyahe (kita ng driver — **dispatch lang, walang bawas**) + 2 bayad ng multa (Talaan, para sa Ulat ng Multa — **hindi binabawas sa kita**) — **walang donasyon na nakabawas sa kita** (hiwalay ang donasyon) |
| `donations` | 5 entries (3 donate, 2 gamit - fund up/down) |
| `settings` | `deductionTiers` (16→₱32, 22→₱44) + `queueRotation` (cutoffHour 21) |
| `emergencyContacts` | 911 + Dispatcher + 2 lokal na contact |
| `activityLogs` | 5 audit entries |

## Mga login

Password for **all** accounts: `Enerjeep@2026`

| Role | Phone number |
|---|---|
| Superadmin | `+639171000001` (Ramon Villanueva) |
| Admin | `+639171000002` (Maria Santos — **Admin - Marilao**) |
| Drivers | `+639171000003` … `+639171000035` (33 tunay na drayber: Roger TXA 448, Jess DGT 463, Joel PWU 904, … , Marlon DLH 781 — tingnan ang `roster` sa `setup.js`) |

> The password hash is generated with the same algorithm as the app
> (PBKDF2-HMAC-SHA256, 210,000 iterations) — the accounts work on the
> real login screen.

## OTP without SMS cost (IMPORTANT for presentations)

Phone login needs an SMS OTP. For development and presentations, use
**Firebase test numbers** (no SMS is sent, no cost):

1. Firebase console → **Authentication → Sign-in method → Phone** →
   **Phone numbers for testing**.
2. Add e.g. `+639171000001` with code `123456` (do the same for the
   other numbers you will use).
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
   node setup.js --serviceAccount C:\Users\Dave\Downloads\serviceAccount.json
   ```

   (O kaya: i-set ang `GOOGLE_APPLICATION_CREDENTIALS` at patakbuhin
   lang ang `node setup.js`.)

3. Options:
   - `--wipe` — buburahin muna ang laman ng mga collections bago
     magsulat. **Huwag** gagamitin sa may totoong datos.

4. Pagkatapos: i-restart ang app — dapat puno na ng datos (pila,
   insidente, kita (dispatch), donations, Talaan).

## Notes

- Re-running the script without `--wipe` overwrites the same documents
  (fixed IDs) — safe to repeat. Older leftover documents (lumang id
  scheme, mga manual na entries sa `kita`/`kitaHistory`, at mga record
  na nag-uugnay ng donasyon sa kita) are cleaned up automatically.
- The script writes with the Admin SDK, which bypasses Firestore rules —
  this is a maintainer tool, not an app feature.
- Ang kita ng driver ay dispatch records lang (nasa `ledger`): walang
  manual entries (gastos/multa) na binabawas sa kita. Ang mga bayad ng
  multa ay nasa `ledger` (feeType "Multa") para sa Talaan at Ulat ng
  Multa — hindi hinahatak sa kita ng driver.
- Ang bawat record ng kita/ledger ay dumadaan sa mga tseke: walang
  donasyon ang binabawas sa kita ng driver (`verifyNoDonationDeductions`)
  at dispatch lang ang laman ng kita (`verifyDriverEarnings`) — parehong
  tumatakbo tuwing pagpatakbo.
