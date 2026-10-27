# Demo Script — Paano i-present ang EnerJeep

Handa nang sundan hakbang-hakbang. Ang layunin: makita ng panel ang
**lahat ng panig** (driver muna!), ang **lumang datos** (hindi blangko),
at ang **dalawang sagot sa mahihirap na tanong** (operating hours +
audit/liability) nang live.

---

## Bago mag-present (checklist — gawin isang araw bago!)

- [ ] `git pull origin arena/bbab583d-capstone-project` — dapat
      `911ab31` pataas ang build
- [ ] Sync + Build sa Android Studio; i-run sa **totoong cellphone**
      (mas kapani-paniwala kaysa emulator)
- [ ] **Patakbuhin ang `seed/seed.js`** (tingnan ang `seed/README.md`)
      — hindi pwedeng blangko ang app ("minus points" sabi ng adviser)
- [ ] Firebase console → Authentication → Phone → **Phone numbers for
      testing** (para walang SMS cost sa OTP)
- [ ] I-charge ang phone; **i-share ang screen** nang maayos (dapat
      kitang-kita — na-banggit ito ng adviser!)
- [ ] I-print/open ang `docs/ARCHITECTURE.md` at
      `docs/SCOPE-AND-LIMITATIONS.md` (pang-Q&A)

---

## 1. DRIVER muna (ang perspective na hiniling ng adviser) — ~5 min

Login bilang driver (hal. `+639171000003`, password `Demo123!`, OTP sa
test code).

1. **Home** — "Musta, Juan!" → ipakita ang **blue status card**
   (posisyon #, plaka, ruta), **"Posisyon BUKAS"**, at ang
   **"Pila Ngayon"** (#1 at kasunod — sino ang mauuna).
2. **"Tapos Na Bumyahe"** — pindutin → "Oo" → **pula** ang button at
   lalabas sa ibaba ang **"Buong Kita Ngayon"** (₱) — *"hindi na
   kailangan pumunta sa Kita tab."*
3. **Insidente tab** — ipakita ang card na may kumpletong detalye +
   **"Tawag"** button (dialer ang lalabas — kitang-kita ang numero bago
   tumawag). *"Hindi ito GCash o online payment — tawag lang talaga
   ang kailangan ng driver."*
4. **Kita tab** — ang listahan ng kita (date filter + plate filter).
5. **Emergency button** — 911 + ang listahan mula sa kooperatiba.

> **Talking point:** *"Ang driver ay walang ibang gagawin kundi
> mag-drive, mag-log ng kita, at mag-report ng insidente — ganyan
> ka-simple ang UI."*

## 2. ADMIN (pang-araw-araw na operator) — ~5 min

Login bilang admin (`+639171000002`).

1. **Home** — dashboard stats (kita, insidente, drivers, donasyon).
2. **Pila** — ang dalawang ruta (**Marilao–Monumento** muna;
   **Monumento–Marilao** ay may laman na dahil may na-dispatch na sa
   seed). Mag-**dispatch** ng #1 → mapupunta siya sa kabila bilang #1.
   Ipakita ang **Kanselahin** (Nakansela = out) at **Ibalik sa Pila**
   (dulo).
3. **Insidente** — "Ayusin" ang isang bukas na insidente.
4. **Mga Ulat** — **Kasaysayan** sa taas, charts sa ibaba (buwanang/
   taunang); ang **bilang ng insidente = walang ₱** (bilang, hindi
   pera); ang multa at donasyon ay may ₱.

> **Talking point:** *"Ito ang buong trabaho ng operator — pero WALA
> siyang access sa patakaran (rates, oras) at WALA sa audit log."*

## 3. SUPERADMIN (patakaran + pananagutan) — ~5 min

Login bilang superadmin (`+639171000001`) → **Mga Account**.

### 3a. LIVE na sagot sa "operating hours" challenge
1. Pindutin ang **Settings** → ipakita ang **"Oras ng Reset (0-23)"**
   na naka-21.
2. Baguhin sa **22** → **I-save**.
3. Bumalik sa **Pila** tab: *"Kitang-kita — 'Reset ng Pila • 10:00 PM'
   na. **Walang binagong code. Walang rebuild.** Kapag dumami ang
   commuter hanggang 11 PM, ilagay lang ang 23."*

### 3b. LIVE na sagot sa "sino ang liable?"
1. Sa **Mga Account**, gamitin ang basurahan ng isang account →
   burahin (hindi ang sarili).
2. Pindutin ang **Talaan** → ipakita ang bagong entry:
   **"Binura ang Account — Ni: [pangalan]"** may oras.
3. *"Ang log na ito ay **append-only** — kahit ako bilang superadmin,
   hindi ko ito kayang i-edit o burahin (Firestore rules mismo ang
   humaharang). Kaya kung may mawala, may mananagot."*

### 3c. Ang user levels (maikling sagot)
- *"Hindi ito dagdag na antas para sa dami ng features — ito ay
  para hindi sa superadmin account ginagawa ang pang-araw-araw na
  trabaho. Lahat ng ginawa ng admin ay may pangalan sa Talaan."*

## 4. Q&A — saan hahanapin ang sagot

| Tanong ng panel | Saan sagot |
|---|---|
| "Bakit 3 user levels? / Anong benefit?" | `docs/ARCHITECTURE.md` §1 (Q1–Q3) |
| "Paano kung superadmin ang lumabag?" | `docs/ARCHITECTURE.md` §1 Q2 (append-only log) |
| "May pera ba dyan? Cashless? GCash?" | `docs/SCOPE-AND-LIMITATIONS.md` #1 (bookkeeping lang ng pisikal na pera) |
| "Pwede bang palitan ang oras nang walang code?" | `docs/ARCHITECTURE.md` §2 + live demo 3a |
| "Ano ang limitasyon?" | `docs/SCOPE-AND-LIMITATIONS.md` (13 items) |
| "Saan galing ang datos?" | `seed/README.md` (realistic demo data) |

---

## Mga dapat iwasan sa demo

- Huwag puro admin screens lang (ang #1 na puna ng adviser).
- Huwag i-demo sa blangkong database (laging may seed data).
- Huwag sabihing "pwede pa itong idagdag" — sabihin ang nasa scope
  doc: kung ano ang hindi sakop, bakit hindi sakop.
