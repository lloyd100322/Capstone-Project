# EnerJeep — Capstone Project

EnerJeep is an Android app (Kotlin + Jetpack Compose) for a jeepney
cooperative: phone-OTP login, a rotational dispatch queue (Pila),
income and expense tracking (Kita / Ledger), incident reports,
donations, and an emergency contact system. Data is stored in
**Firebase** (Authentication + Firestore).

The app has **three roles**: **Driver**, **Admin** (daily operator), and
**Superadmin** (full package).

**Project documentation**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — design decisions and
  their justification (user levels, dynamic settings, security model)
- [`docs/SCOPE-AND-LIMITATIONS.md`](docs/SCOPE-AND-LIMITATIONS.md) —
  what the system covers and what it deliberately does not
- [`docs/TESTING.md`](docs/TESTING.md) — how to share the APK with
  group testers (no cable needed) + test accounts and OTP setup
- [`seed/README.md`](seed/README.md) — realistic demo data (accounts,
  queue, incidents, earnings) for presentations

---

## Tech stack

| Part | Technology |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Hilt (dependency injection) |
| Backend | Firebase Auth (phone OTP), Cloud Firestore |
| Security | App Check (Play Integrity + debug), salted password hashes (`/userSecrets`) |
| Tests | JUnit 4 (unit tests, no device needed) |

- `minSdk` 29 (Android 10) — `targetSdk`/`compileSdk` 36
- Gradle 9.4 / AGP 8.13 (use Android Studio with JDK 17+)

---

## Features by role

### Driver — tabs: Home | Insidente | Kita | Profile
- **Home**: greeting, in-app dispatch alert (sound + banner + bell),
  blue status card (position #, plate, route, "Posisyon BUKAS" badge),
  "Pila Ngayon" (#1 and next in line), open-incident card, and the
  **"Tapos Na Bumyahe"** button — after confirming, the button turns
  **red** and the **whole-day earnings ("Buong Kita Ngayon")** appear
  right below it (same net figure as the Kita tab), so there is no need
  to open the Kita tab. The **Emergency** button dials 911 and the
  cooperative's emergency list.
- **Insidente**: Di-Naayos / Naayos lists with search and date filter.
  Every incident card shows the complete details (title, plate,
  location, description, date, status) plus a **"Tawag"** call button
  (phone dialer — the driver sees the number before calling).
- **Kita**: earnings list with a date filter (default: today) and plate
  filter; add manual Kita / Bayad / Multa entries.

### Admin — tabs: Home | Pila | Insidente | Mga Ulat | Profile
- **Home**: daily dashboard (income, incidents, drivers, jeeps,
  donations) with quick actions.
- **Pila (dispatch queue)**: two route tabs (**Marilao–Monumento** first,
  then **Monumento–Marilao**) plus Kasaysayan. Strict FIFO: dispatch
  only from the front (arrows + drag-reorder allowed), cancel/restore
  ("Nakansela" = out, "Ibalik sa Pila" = back at the end), tiered
  per-seater deduction (editable at dispatch).
- **Insidente**: resolve incidents ("Ayusin"), fines via incidents,
  "Tawag" for the involved driver.
- **Mga Ulat**: one stacked reports screen — Kita (Kasaysayan list on
  top, monthly/yearly charts at the bottom), incident count per plate
  (**plain counts, no peso sign**), fines chart, and the Donasyon
  section.

### Superadmin — everything the admin has, plus the toolbox
Inside **Mga Account** (only the superadmin sees these):
- **Emergency** — manages the cooperative emergency-contact list shown
  to drivers.
- **Talaan** (Talaan ng Aktibidad) — the audit log: which admin did
  what, and when.
- **Settings** — the **automatic queue-reset hour** (any hour 0–23,
  default 21 = 9 PM) and the **per-seater deduction tiers**.

Also superadmin-only:
- Creating **admin** accounts (an admin can create driver accounts
  only).
- **Account management** — each card shows the name, role badge, status
  ("Aktibo") and a trash icon (upper right). Deleting an account is a
  **real delete** (app data + saved password + open queue entries, with
  history preserved); the signed-in account cannot delete itself.

---

## Queue rules (Pila)

- New vehicles/drivers enter **Marilao – Monumento**. The
  **Monumento – Marilao** tab starts empty and only receives drivers
  **after the first dispatch** from Marilao – Monumento (the dispatched
  driver enters the other route at **#1**).
- The queue is **strict FIFO** — only the front is dispatched; arrows
  and drag-reorder (in compact/zoomed-out view) adjust the order.
- **Automatic daily reset** at the cut-off hour (superadmin-editable,
  default 9 PM): today's #1 goes to the back, everyone else moves up.
  It is fully automatic (catch-up check on open + a timer) — there is no
  manual reset button.
- The queue is transaction-guarded: dispatch/cancel/restore fail safely
  instead of corrupting the queue when two admins act at once.

---

## 1. Setup in Android Studio

1. **Clone or download** the repository, then open the project folder in
   Android Studio (File → Open).
2. Make sure the branch is the working branch:
   `git checkout arena/bbab583d-capstone-project`
3. `app/google-services.json` is already included (Firebase project
   `enerjeep-b8f3a`), so no extra Firebase file setup is needed.
4. Press **Sync Project with Gradle Files** (elephant icon) and wait for
   the first sync to finish (internet required).
5. Press **Run ▶** on an emulator (with Google Play) or a real device
   (Android 10+). For SMS OTP and calls, use a **real phone**.

## 2. Running the tests

```bash
./gradlew test
```

Or in Android Studio: run the tests under `app/src/test/`. Covered:
password hashing, emergency-contact parsing, Philippine identifiers,
dashboard totals, kita history merging, history date filters.

## 3. Firebase setup (project owners only)

These are done in the Firebase console / with the Firebase CLI — they
are **not** in the code:

1. **Deploy the Firestore security rules** (important — the file is
   ready in the repo):

   ```bash
   firebase deploy --only firestore:rules
   ```

2. **Phone Authentication**: enable the Phone provider (Sign-in method)
   and make sure SMS quota/billing allows +63 numbers. Register the
   app's **SHA-1 and SHA-256** fingerprints (Project settings → Your
   apps → Add fingerprint; get them with Android Studio's
   `signingReport` Gradle task or `keytool -list -v`), then
   re-download `google-services.json`.

3. **App Check**: the app registers Play Integrity + a debug provider.
   After testing, turn on **Enforcement** for Firestore in the console
   (App Check → Settings). Register debug tokens for developer devices
   (the token is printed in Logcat as "Firebase App Check debug token").
   A 403 on a new device is normal until the token is registered.

4. **Cloud Function — delete Auth user on account deletion** (optional
   but recommended; requires the Blaze/pay-as-you-go plan):

   ```bash
   cd functions && npm install && cd ..
   firebase deploy --only functions
   ```

   Without it, deleting an account in the app removes the Firestore
   data but leaves the Firebase Auth record behind (the phone number
   then cannot be reused). With it, `functions/index.js` deletes the
   Auth user automatically.

5. **Firestore indexes**: if the app log prints a "requires an index"
   link, open it once while signed in to the Firebase console to create
   the index.

## 4. Where things live

```
app/src/main/java/com/mobile/enerjeep/
├── data/            # Firebase helpers, UserSession, password hashing
├── ui/
│   ├── login/       # phone OTP + password login, reset password
│   ├── home/        # dashboards, driver home, accounts, superadmin toolbox
│   ├── queue/       # dispatch queue (pila), rotation, history
│   ├── kita/        # earnings (kita + ledger merged, date filter)
│   ├── ledger/      # admin income/fees (Ledger)
│   ├── report/      # Mga Ulat: income/incidents/fines/donations + charts
│   ├── incident/    # incident reports (admin + driver)
│   ├── profile/     # profile editing (emergency contact only), security
│   └── components/  # shared UI: PesoIcon, LabeledBarChart, date filter, ...
├── EnerJeepApp.kt   # App Check initialization
└── MainActivity.kt
functions/           # Cloud Function (delete Auth user on account delete)
firestore.rules      # Firestore security rules (deploy with the CLI)
docs/                # architecture justification + scope/limitations
seed/                # realistic demo data (Firestore seed script)
```

### Editing the peso (₱) sign

The peso sign is a **text-based icon**. Open
`app/src/main/java/com/mobile/enerjeep/ui/components/PesoIcon.kt` and
change the four settings at the top of the file (character, font
weight, size ratio, vertical offset). One edit updates every screen.

---

## 5. Notes for reviewers

- Passwords are stored ONLY as salted hashes in `/userSecrets/{phone}`
  (legacy plaintext values are migrated and deleted automatically).
- Deleting an account is a real delete: Firestore user + password
  record + open queue entries (queue is renumbered); dispatch and
  income history are preserved.
- The emergency dialog shows 911 plus the cooperative list managed by
  the superadmin; admins also see their own saved contact.
- Profile editing is limited to the emergency contact name and number —
  name and phone number are locked (used by Firebase Phone
  Authentication).
- Incident charts show **plain counts** (no peso sign) because they are
  counts, not money.
