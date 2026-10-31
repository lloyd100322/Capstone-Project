# Database Schema — EnerJeep

Ang koleksyon ng Firestore para sa EnerJeep, kasama ang mga ID convention,
kahulugan ng bawat field, at ang mga panuntunan ng integridad. (Talaan ng
datos ito ng sistema — hindi ito bahagi ng app screen.)

**WALANG SUBCOLLECTIONS** — flat lang ang lahat ng koleksyon.

---

## 1. Mga koleksyon

### `/users` — profile ng account (docId = `+639XXXXXXXXX`)
| Field | Kahulugan |
|---|---|
| `userId` | "USR-####" (seed) o UUID (app). **Foreign key** — ito ang `driverId` sa `/queue`, `/incidents`, `/ledger`. |
| `firstName`, `lastName` | Pangalan. |
| `phoneNumber` | Kapareho ng docId. |
| `role` | `superadmin` \| `admin` \| `driver` (maliit na titik; case-sensitive sa rules). |
| `status` | `Activated` \| `Deactivated`. |
| `plateNumber` | Para sa driver; `null` para sa admin. |
| `seater` | Bilang ng upuan (16/22) — batayan ng dispatch deduction. |
| `isVehicleActive` | Aktibo ba ang sasakyan ng driver. |
| `licenseNumber`, `bodyNumber` | Opsyonal. |
| `emergencyContactName`, `emergencyContactNumber`, `emergencyContact` | Emergency contact (ang `emergencyContact` ay pinagsama para sa lumang bersyon). |
| `adminStation` | Para sa admin. |
| `createdAt`, `updatedAt` | Timestamps. |

**Hindi kasama:** password (nasa `/userSecrets` lamang). Walang plaintext
password na dapat manatili sa koleksyong ito — tinatanggal ito ng
`seed/setup.js` (`cleanupLegacyPasswords`).

### `/userSecrets` — password hash (docId = `+639XXXXXXXXX`)
`passwordHash`, `passwordSalt`, `iterations` (PBKDF2 210k), `createdAt`.
Basahin at sulat ng MAY-ARI (at admin) lang.

### `/queue` — pila ng terminal
| Field | Kahulugan |
|---|---|
| `id`, `driverId`, `driverName`, `plateNumber` | Identity (`driverId` → `users.userId`). |
| `route` | `marilao` \| `monumento`. |
| `position` | Posisyon sa pila (1 = unahan). **Sulat: admin lang** (+ end-travel reflow). |
| `status` | `Nakapila` \| `dispatched` \| `completed` — see "Dual flags" sa ibaba. |
| `isCancelled`, `cancelledAt` | Ang TUNAY na flag ng pagkansela (dual flag, tingnan sa ibaba). |
| `income`, `otherFees`, `deduction` | Dispatch amounts. |
| `time`, `dispatchedAt`, `endTravelAt`, `createdAt`, `updatedAt` | Timestamps. |

**ID convention:** `queue_<userId>_<uuid>` (panibagong entry);
`queue_<userId>_after_<entryId>` (requeue). Ang reset queue ay
`queue_<userId>_<uuid>_reset` (sa `dailyQueueReset`).

### `/ledger` — kita at bayarin
| Uri ng record | ID | Fields |
|---|---|---|
| Dispatch (kita ng driver) | `<entryId>_dispatch` | `feeType:"Dispatch"`, `source:"queue_dispatch"`, `driverId`, `plateNumber`, `queueId`, `amount`=netAmount, `grossAmount`=income, `otherFees`, `netAmount`, `seater`, `seaterDeduction`, `deductionTier`, `date` |
| Bayad ng multa (Talaan) | `fee_<hex>` o UUID | `feeType:"Multa"`, `source:"ledger"`, `driverId`, `plateNumber`, `driverName`, `amount`, `date`, `createdAt` |
| Iba pang bayarin | `fee_<hex>` o UUID | `feeType` (hal. "Terminal Fee"), pareho ng iba pang fields |

**Ang kita ng driver = dispatch records lamang.** Hindi kasama ang multa o
donasyon sa kita; walang bawas na kahit ano.

### `/incidents` — mga insidente
`title`, `description`, `severity`, `location`, `status`
(`Ongoing`/`Resolved`), `driverId`, `driverName`, `plateNumber`,
`penalty` (Double), `date`, `createdAt`, `updatedAt`.

### `/donations` — donasyon at gamit
`donor`, `amount`, `date`, `source`, `note`, `type` (`donate` \| `gamit`),
`createdAt`. **Hindi kailanman binabawas sa kita ng driver.**

### `/settings` — mga setting (dalawang doc)
- `settings/queueRotation` — `cutoffHour` (**superadmin lang ang sulat**),
  `lastQueueDay` (`yyyyMMdd` — ito ang guard ng background reset),
  `updatedAt`.
- `settings/deductionTiers` — ang bawas kada upuan (hal.
  `{16: 32, 22: 44}` = 16-seater → ₱32, 22-seater → ₱44).

### `/activityLogs` — audit log (append-only)
`actorName`, **`actorPhone`** (laging ang phone ng SARILING nagsulat —
tine-check ng rules), `actorRole`, `action`, `detail`, `createdAt`.
Basa: superadmin lang. Walang edit o delete.

### `/emergencyContacts` — listahan ng emergency contacts
Pamahalaan: superadmin lang. Basa: lahat ng member.

### RETIRED: `/kita`, `/kitaHistory`
Hindi na ginagamit (2026-10-31). Ang kita ng driver ay galing sa
`/ledger` (dispatch records) lamang. Ang `seed/setup.js` ay burahin ang
mga legacy na dokumento sa bawat takbo; ang rules ay nananatiling
backstop (basa lang, walang sulat).

---

## 2. Mga konbensyon at integridad

- **`driverId` → `users.userId`** — pareho sa lahat ng koleksyon
  (queue, incidents, ledger). Hindi ginagamit ang docId ng user.
- **Dispatch idempotency:** ang ledger id ay `<entryId>_dispatch` — kapag
  nabuo na, hindi na muling makakagawa ng dispatch record.
- **Atomikong dispatch:** `runTransaction` + bawat dokumento ay
  binabasang FRESH sa loob ng transaction (per-doc `transaction.get`).
- **Queue position writes:** admin lang; ang end-travel reflow ay
  pinapayagan lamang ang `position` na field ng ibang entry.
- **Date strings (`date`, `time`):** display lang ang mga ito —
  `Locale.US` format ("MMM dd, yyyy") para pareho sa bawat device.
  Ang filtering/sorting ay sa `createdAt`/`endTravelAt` (timestamps).
  Ang mga reader (HistoryDateFilter, FirestoreDateUtils) ay tumatanggap
  ng maraming legacy formats.
- **Dual flags sa `/queue`** (dokumentado, 2026-10-31): ang status ay
  "overloaded" — `Nakapila` ay parehong "naghihintay" AT "kanselado"
  (kasama ang `isCancelled: true`). Ang pagsala ay laging
  `status == "Nakapila" && isCancelled != true` (tingnan ang
  `isActiveStatus`/`isActiveQueueStatus`). Gusto itong pag-isahin sa
  hinaharap (hal. `status: "Cancelled"`).
- **Full-collection reads** (2026-10-31 audit): ang mga dashboard/ulat ay
  nagbabasa ng buong koleksyon (users/ledger/queue). Ligtas ito sa
  kasalukuyang laki (~35 users, ~300 ledger kada buwan) at walang
  `firestore.indexes.json` na kailangan; sa hinaharap, ang pagination o
  partial queries na may `where` + `orderBy` ang solusyon.

---

## 3. Basahin kung paano (mula sa app)

| Screen | Sanggunian |
|---|---|
| Pila | `/queue` (bawat route) + `/settings/queueRotation` |
| Kita ng driver | `/ledger` (feeType "Dispatch" lamang) |
| Mga Ulat | `/ledger` + `/donations` (+ `/users` para sa summary) |
| Talaan | `/ledger` (lahat ng fee rows) |
| Ulat ng Multa | `/ledger` (feeType "Multa" lamang) |
| Profile | `/users` (sarili) + `/userSecrets` (sarili) |
| Account management | `/users` (lahat, admin) |
| Audit log | `/activityLogs` (superadmin) |
