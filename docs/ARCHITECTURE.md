# System Architecture — Design Decisions and Justification

This document explains **why** EnerJeep is built the way it is, so the
team can defend each decision during the review/presentation. Every
item lists the decision, the reason, and the honest trade-off.

---

## 1. Why three user levels (Driver / Admin / Superadmin)?

**The challenge:** *"Why create multiple restricted user levels when
the superadmin controls already suffice?"*

**Short answer:** the roles are not about *features* — they are about
**who is allowed to change money-related policy** in a real cooperative.
The three levels map to the three kinds of people who actually use a
dispatch system.

| Level | Real-world counterpart | Daily job |
|---|---|---|
| Driver | Jeepney driver | Drive, log earnings, report incidents |
| Admin | Dispatcher / operator on duty | Run the pila for the day: queue, dispatch, incidents, reports |
| Superadmin | Cooperative manager / owner | Policy and accountability: rates, reset hour, who is in the system, the audit trail |

### Reasons

1. **Separation of duties (least privilege).** A person who is only
   dispatching jeeps should not be able to change the per-seater
   deduction rate or the daily reset hour — those decisions change how
   much every driver earns. Keeping policy settings behind the
   superadmin level means an honest mistake by the operator on duty
   cannot silently alter everyone's pay.

2. **Accountability requires non-root actors.** The system keeps an
   audit log ("Talaan ng Aktibidad") of *which account* dispatched,
   cancelled, deleted, or edited something. That log is only meaningful
   if day-to-day work is done by **admin** accounts that can be held
   accountable by someone above them. If everybody used one shared
   superadmin account, the audit trail would be useless — and a single
   leaked credential would expose everything.

3. **Blast-radius control.** Deleting accounts, managing the emergency
   contact list, and setting money-related policy are high-impact
   actions. Restricting them to one level reduces the number of
   accounts that can do damage, and the audit log records those
   actions explicitly.

4. **It maps to the cooperative's real process.** Cooperatives do not
   let the dispatcher rewrite the boundary rules of the day. The
   software mirrors the organization instead of flattening it into one
   all-powerful account.

### Honest trade-off

Yes — everything *could* run under a single superadmin account, and the
app would still function. What would be lost is the process: an
all-powerful shared account means no accountability, no safe division
of work, and one stolen phone could rewrite rates and delete accounts.
The extra role is a small cost (one role check in code and security
rules) for a large process benefit.

If the panel still insists on two levels, the change is deliberately
small: the `admin` role already has its own UI gating
(`UserSession.isSuperadmin()`), so merging is a configuration-level
decision — but we recommend keeping the separation above.

---

## 2. Why is the operating/reset hour a *setting*, not code?

**Earlier review finding:** *"the operating hours cannot be dynamically
updated without rewriting code."*

**Status: fixed.** The daily reset hour is data-driven:

- Stored in Firestore at `settings/queueRotation.cutoffHour`
  (0–23; default 21 = 9 PM).
- Editable by the superadmin in **Mga Account → Settings** ("Oras ng
  Reset") — no code change, no rebuild.
- The queue reads the value on every open and before every scheduled
  rotation, and the UI shows the live value ("Reset ng Pila • 9:00 PM").
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
