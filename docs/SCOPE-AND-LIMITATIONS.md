# Scope and Limitations

This document defines exactly what EnerJeep covers — and, just as
importantly, what it does **not** cover. Knowing the boundaries is part
of the design: a system that honestly says "that is out of scope" is
stronger in review than one that pretends to do everything.

---

## 1. In scope (what the system does)

### Accounts and security
- Three roles: **Driver**, **Admin** (daily operator), **Superadmin**
  (policy + accountability) — see `docs/ARCHITECTURE.md`.
- Login via **phone OTP + password** (salted PBKDF2-SHA256 hash, never
  stored in readable form).
- Account creation (superadmin can create admins; admins create
  drivers), real account deletion (app data + password secret + open
  queue entries; history preserved), and per-account status
  ("Aktibo"/"Hindi Aktibo").

### Dispatch queue (Pila)
- Two routes: **Marilao – Monumento** (entry route) and
  **Monumento – Marilao** (fills up only after dispatches).
- Strict FIFO with visual reordering (arrows + drag in compact view),
  dispatch from the front only (the dispatched driver enters the other
  route at #1), cancel ("Nakansela") and restore-to-end.
- **Automatic daily rotation** at a cut-off hour that the superadmin
  can change at runtime (0–23; default 9 PM) — catch-up check + timer,
  no manual reset button.
- Per-seater **tiered deduction** (editable in Settings, adjustable at
  dispatch time) with the net amount written to the ledger.

### Earnings and reports
- **Kita**: merged manual entries (Kita / Bayad / Multa) and
  dispatch ledger records, with date filter (default: all dates) and
  plate filter; net earnings per day. **Donations are NOT part of
  this** — they are voluntary and never deducted from anyone's
  earnings.
- **Mga Ulat** (admin): income (Kasaysayan + monthly/yearly charts),
  incident counts per plate (plain counts — no peso sign), fines chart,
  donations (a separate voluntary fund — the balance can go **up or
  down**: donations add to it, recorded "gamit" expenses subtract).
- **Donations** are managed by the **superadmin only** (add donation /
  record "gamit"), with donor, amount, date, source, note.

### Incidents
- Report, resolve ("Ayusin"), fines on incidents, call button on the
  driver's card, Di-Naayos / Naayos lists with search and date filters.

### Emergency and communication
- Emergency dialog: 911 + the cooperative list managed by the
  superadmin (+ the admin's own saved contact on the admin screen).
- In-app dispatch alerts: sound + banner + bell (while the app is open).

### Accountability
- **Talaan ng Aktibidad** (superadmin-only): who did what and when —
  dispatches, cancels, deletions, settings changes.

---

## 2. Out of scope / limitations (what the system does NOT do)

These are deliberate boundaries, not oversights. Each item is a valid
question in review, so each has a stated reason.

1. **No cashless / payment processing (no GCash, no wallet).** Money in
   this cooperative is **physical cash** handed over by the driver. The
   app **holds no money and moves no money** — it is a *record*
   (bookkeeping) of the cash amounts (Kita, Bayad, Multa, donations,
   dispatch fees). "Walang pera sa app" is deliberate: no stored value,
   no wallet to hack, no remittance partner needed.
2. **No true background push notifications.** Dispatch alerts work only
   while the app is open (in-app sound/banner/bell). Firebase Cloud
   Messaging (FCM) is a future enhancement; it needs server-side
   plumbing and possibly billing, and the current workflow (drivers
   sign in before the day starts) does not require it.
3. **No real-time GPS tracking of jeepneys.** The system tracks queue
   state, not vehicle locations.
4. **No payroll, accounting export, or government reporting** (e.g.,
   BIR forms). Kita/Ledger are operational records, not a full
   accounting suite. Data can be exported later (future work).
5. **Single cooperative only.** One dataset, one set of routes
   (Monumento–Marilao). Multi-cooperative support is out of scope.
6. **"Operating hours" = the queue cut-off/rotation time only.** The
   system models the *operating-day boundary* (dynamic, superadmin-
   editable — see `docs/ARCHITECTURE.md`). It does not implement a
   business-hours schedule that blocks dispatches outside a set window,
   driver shift planning, or a calendar/holiday system. (The old
   "Iskedyul" tab was removed as unnecessary.)
7. **No offline mode.** The app requires internet (Firestore). A failed
   write is reported to the user instead of being queued silently.
8. **SMS OTP is unreliable while the app is NOT on the Play Store.**
   Firebase Phone Auth verifies the *app* before it sends real SMS.
   An app installed from the **Play Store** passes this check
   automatically (Play Integrity); an app **sideloaded from an APK**
   (our current distribution) cannot be verified, so Google's abuse
   protection limits it: real SMS may arrive late or not at all, and
   number/quotas can be rejected ("invalid phone number", too many
   requests). This is a **distribution status**, not a bug in the
   login code. **For demos and testing we use Firebase test phone
   numbers with fixed codes** (no SMS is sent, no cost — see
   `seed/README.md`); real SMS OTP becomes reliable once the app is
   published on the Play Store. (Firebase Phone Auth billing,
   ~USD 0.01–0.06 per verification, is on the project owner.)
9. **The reCAPTCHA ("captcha") on OTP is Google's app-integrity check,
   not a bug.** When the app cannot prove its integrity (sideloaded
   APK — see #8), Firebase opens a **reCAPTCHA challenge before
   sending SMS** as an anti-abuse fallback: without it, anyone could
   clone an app and trigger SMS spam. On a Play Store build, Play
   Integrity verifies the install source and the captcha step
   disappears for most users. **App Check enforcement is also a
   console setting** — Play Integrity covers real devices, and
   development devices must register debug tokens (a 403 before
   registration is expected behavior, not a bug).
10. **Android only** (minSdk 29 / Android 10). No iOS or web client.
11. **Password recovery** is handled by an in-app reset flow (not email
    links), because the login identity is a phone number, not an email.
12. **One active vehicle per driver account.** A driver with multiple
    jeepneys uses multiple accounts (keeps the queue unambiguous).
13. **Seed/demo data is not production data.** `seed/` exists so the
    system can be demonstrated realistically (see `seed/README.md`);
    it should not be mixed with real records.

---

## 3. Testing status

- Unit tests (JUnit, no device): password hashing, emergency-contact
  parsing, Philippine identifier formats, dashboard totals, kita
  history merging (de-duplication), history date filters.
- Manual device testing: queue lifecycle (add → dispatch → rotate →
  end-of-day), incident lifecycle, account create/delete, settings
  changes (reset hour, deduction tiers), emergency dialing.
- Known UI-level QA is done on real devices (Android 10+); emulator
  testing does not cover SMS OTP (use Firebase test numbers).
