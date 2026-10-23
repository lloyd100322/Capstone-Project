# EnerJeep — Capstone Project

EnerJeep is an Android app (Kotlin + Jetpack Compose) for a jeepney
cooperative: phone-OTP login, a rotational dispatch queue, income and
expense tracking (Kita / Ledger), incident reports, and an emergency
contact system. Data is stored in **Firebase** (Authentication +
Firestore).

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
   and make sure SMS quota/billing allows +63 numbers.

3. **App Check**: the app registers Play Integrity + a debug provider.
   After testing, turn on **Enforcement** for Firestore in the console
   (App Check → Settings). Register debug tokens for developer devices.

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
│   ├── home/        # admin dashboard, driver home, account management
│   ├── queue/       # dispatch queue (pila), cancel/restore, history
│   ├── kita/        # driver earnings (Ngayon / Kasaysayan)
│   ├── ledger/      # admin income/fees (Ledger)
│   ├── report/      # reports: income, incidents, fines, donations
│   ├── incident/    # incident reports (admin + driver)
│   ├── profile/     # profile editing, security (password)
│   └── components/  # shared UI: PesoIcon, history date filter, ...
├── EnerJeepApp.kt   # App Check initialization
└── MainActivity.kt
functions/           # Cloud Function (delete Auth user on account delete)
firestore.rules      # Firestore security rules (deploy with the CLI)
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
- The queue is transaction-guarded: dispatch/cancel/restore fail safely
  instead of corrupting the queue when two admins act at once.
- The emergency dialog shows 911, the emergency contacts entered when
  driver accounts were created (admin dashboard), and the account's own
  contact (driver screen).
