# System Architecture — Design Decisions and Justification

This document explains **why** EnerJeep is built the way it is, so the
team can defend each decision during the review/presentation. Every
item lists the decision, the reason, and the honest trade-off.

---

## 1. Why three user levels (Driver / Admin / Superadmin)?

**The challenge:** *"Why create multiple restricted user levels when
the superadmin controls already suffice? Everything the admin can do,
the superadmin can also do — so what is the difference? What if the
superadmin goes rogue?"*

**Short answer:** the extra level is **not** there to give the admin
superpowers the superadmin lacks — it is there so the **daily work does
not run on a superadmin account**. The limit itself IS the benefit.

| Level | Real-world counterpart | Daily job |
|---|---|---|
| Driver | Jeepney driver | Drive, log earnings, report incidents |
| Admin | Dispatcher / operator on duty | Run the pila for the day: queue, dispatch, incidents, reports |
| Superadmin | Cooperative manager / owner | Policy and accountability: rates, reset hour, who is in the system, the audit trail |

### The four questions a panel will ask — and the answers

**Q1. "Kung kaya rin naman ng superadmin, ano ang difference?"**
Yes — the superadmin can do everything the admin can. That is by
design (it is a superset, like a bank manager over a teller). The
point of the **admin** level is what the operator **cannot** do:
change the per-seater deduction, change the daily reset hour, manage
the emergency list, create other admin accounts, or read the audit
trail. Day-to-day work happens on an account whose worst mistake is
contained. A teller does not hold the vault key even though the
manager can open the vault.

**Q2. "Paano kung ang superadmin ang loloko-loko?"**
Then the **audit log still catches them**. The `/activityLogs`
collection is enforced by Firestore security rules as
**append-only**: `allow update, delete: if false` — **not even the
superadmin** can edit or delete an entry (see `firestore.rules`). Any
action (including "Binura ang Account", dispatches, settings changes)
is permanently stamped with **who, what, and when**. The role design
does not pretend to defend against a malicious owner — nothing can —
but it makes every action *traceable*, including theirs.

**Q3. "Anong benefit ng dagdag na user level? Kokonti lang naman ang
users namin (ilan lang ang driver)."**
Roles map to **jobs, not headcount**. Even a four-driver cooperative
has someone who dispatches and someone who owns the business. Three
levels = three real jobs:

1. **Separation of duties** — the dispatcher cannot silently change
   everyone's earnings (rates, reset hour are superadmin-only).
2. **Attribution** — because daily work is done by named operator
   accounts, "sino ang nagbura nito?" always has an answer. With one
   shared superadmin account, nobody is liable.
3. **Blast-radius control** — a lost operator phone does not lose the
   policy controls, the emergency list, or the audit log.

**Q4. "May pera ba dyan? May cashless? Gcash?"**
No — and this is a **stated scope boundary** (see
`SCOPE-AND-LIMITATIONS.md`). Money in this cooperative is **physical
cash handed over** by the driver. The system does **not** hold, move,
or process payments. What it does is **bookkeeping**: it records the
cash amounts (Kita, Bayad, Multa, donations, dispatch fees) so the day can
be accounted for. "Walang pera sa app" is a feature, not a gap — no
stored value means no wallet to hack.

### The deletion-liability walkthrough (demo-ready)

1. Admin "Maria" deletes a driver account at 2 PM.
2. That same minute the app writes an audit entry: action
   **"Binura ang Account"**, actor **"Maria Santos"**, timestamp.
3. Next day the superadmin opens **Mga Account → Talaan** and sees
   exactly who deleted what — and **cannot be edited away** (rules
   block even the superadmin from rewriting it).

This is the concrete answer to *"e liable 'yung isang admin"*: the
system makes liability **provable**.

### Honest trade-off

Yes — everything *could* run under one superadmin account. What would
be lost is the process: no attribution ("sino ang gumawa?"), a single
stolen phone exposes policy + data + logs, and operators work with the
same keys as the owner. The cost of the extra level is one role check
in code and in the security rules — a small price for liability and
containment. If the panel still insists on two levels, merging is a
configuration-level change (`UserSession` gating) — but the
accountability argument disappears with it.

---

## 2. Why is the operating/reset hour a *setting*, not code?

**Earlier review finding:** *"the operating hours cannot be dynamically
updated without rewriting code — what if commuters grow at night and
the pila must run until 10 PM or 11 PM?"*

**Status: fixed — and this exact scenario works today.** The daily
reset hour is data-driven:

- Stored in Firestore at `settings/queueRotation.cutoffHour`
  (0–23; default 21 = 9 PM).
- Editable by the superadmin in **Mga Account → Settings** ("Oras ng
  Reset") — no code change, no rebuild, no redeploy.
- Example (the adviser's exact case): commuters increase at night →
  superadmin changes 21 → 22 (or 23) in Settings → **the Pila screen
  immediately shows "Reset ng Pila • 10:00 PM"** and the rotation timer
  follows the new hour. Changing it back is equally instant.
- The queue reads the value on every open and before every scheduled
  rotation (catch-up included), so an app that was closed during the
  change still respects the new hour.
- The same Settings screen also holds the per-seater deduction tiers
  (`settings/deductionTiers`), which used to be constants.

**Design rule applied:** anything an operator might reasonably change
(hours, rates) is a **document in Firestore**, never a constant. Only
true invariants live in code.

**Scope note (honest):** what the system models is the *operating-day
boundary* (the queue cut-off/rotation time). A full business-hours
schedule (e.g., blocking dispatches outside 4 AM–10 PM) is **out of
scope** — see `docs/SCOPE-AND-LIMITATIONS.md`.

---

## 3. Why MVVM + Hilt (and not plain activities)?

- **MVVM** keeps Firebase/queue logic in ViewModels that survive screen
  rotation and can be unit-tested without a device (the repo has JUnit
  tests for the pure logic: hashing, parsing, totals, merging).
- **Hilt** removes hand-written singletons; `UserSession` and
  `FirebaseFirestore` are injected, so tests can substitute fakes.

## 4. Why Firestore transactions for the queue?

Two admins can act on the pila at the same time (two phones). A
read-then-write without a transaction can double-book position #1 or
lose a driver. Every state change that moves money or position
(dispatch, cancel/restore, "Tapos Na Bumyahe", daily rotation) runs in
`runTransaction`, so a conflicting write simply fails and is retried
instead of corrupting the queue.

## 5. Why phone OTP **plus** a password?

- The **OTP** (Firebase Auth) proves the user owns the SIM.
- The **password** (salted PBKDF2-SHA256 hash in `/userSecrets`) proves
  they own the account — it blocks a different person who briefly holds
  an unlocked phone (or a reused SIM) from opening the account.
- Passwords are never stored in readable form; legacy plaintext values
  are migrated and deleted automatically.

## 6. Why in-app dispatch alerts (and not Firebase push)?

The dispatch alert (sound + banner + bell) works while the app is
open and needs **no** server billing, no Cloud Functions fan-out, and no
per-message infrastructure. True background push (FCM) is listed as a
future enhancement in the scope document. For the cooperative's actual
workflow (drivers sign in before the day starts), in-app alerts are
enough — and they cost nothing.

## 7. Why App Check (Play Integrity + debug)?

Firestore holds money data. App Check makes sure only the real app (not
a script with the API key) can read it. Play Integrity covers real
devices; the debug provider covers development units (registered debug
tokens). Enforcement is turned on in the Firebase console after
testing.

## 8. Why is "delete account" a real delete?

An un-usable account that only looks deleted still occupies its phone
number (the login identity) and its plate number in the queue. Deleting
the Firestore user + the password secret + open queue entries (and
renumbering the queue) keeps the system consistent; dispatch and
income **history** is preserved because those records must stay for
accounting.
