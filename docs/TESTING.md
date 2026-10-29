# TESTING.md — Paano ibahagi at i-test ang EnerJeep (walang cable)

Gabay para makatanggap at makapag-install ang mga kasama ng **app mismo**
(APK) sa phone nila — hindi nila kailangang i-clone ang GitHub repo o
mag-Android Studio.

---

## PART A — Para sa nag-setup (ikaw): gumawa ng APK

Wala itong kinalaman sa GitHub — gagawin sa **Android Studio** ninyo:

### Option 1 — Mabilis (debug APK) — sapat na para sa testing

1. Buksan ang project sa Android Studio → menu **Build**
2. **Build App Bundle(s) / APK(s)** → **Build APK(s)**
3. Hintayin ang "BUILD SUCCESSFUL" → i-click ang **locate** link sa popup
4. Nasa: `app\build\outputs\apk\debug\app-debug.apk` — **ito ang ipadadala**

### Option 2 — Malinis (signed release APK) — optional, mas professional

1. **Build** → **Generate Signed Bundle / APK...** → piliin ang **APK** → Next
2. **Create new...** (keystore):
   - Path: hal. `C:\Users\Dave\enerjeep-release-key.jks`
   - Password: gumawa at **i-copy sa maligtas na lugar** (kapag nawala,
     hindi na ma-a-update ang app sa mga phone nila!)
   - Key alias: `enerjeep`, parehong password
3. Next → piliin ang **release** → **Finish**
4. Nasa: `app\release\app-release.apk`

## PART B — Para sa nag-setup (ikaw): i-upload sa "website"

Ang "website" = isang **download page/link** — dalawang paraan:

### Option 1 — GitHub Release (recommended — kasama ng project)

1. Sa browser: `https://github.com/lloyd100322/Capstone-Project/releases`
2. **Draft a new release**
   - Tag: `v1.0-demo` · Title: `EnerJeep — Test Build`
3. **Attach binaries** → i-drag ang `app-debug.apk` (o `app-release.apk`)
4. **Publish release**
5. Kopyahin ang link ng release page → i-send sa group chat

### Option 2 — Google Drive (mas mabilis)

1. I-upload ang APK sa Google Drive
2. Right-click → **Share** → **Anyone with the link → Viewer**
3. I-send ang link (dapat sa phone ang pagbukas para diretso download)

## PART C — Para sa mga kasamang tester (i-send ito sa kanila)

### 1. I-download at i-install

> ⚠️ **ANDROID LANG po ang app na ito (Android 10 pataas).**
> **Hindi ito gagana sa iPhone/iOS** — ang `.apk` ay Android format.
> Kung iPhone ang gamit, humiram na lang ng Android phone para
> masubukan (o panoorin ang demo).

1. Buksan ang link sa **phone browser** → **Download** ang APK
2. Kapag in-install: papayagan ang **"Install unknown apps"** ng browser
   (Settings → allow) → **Install** → **Open**
3. *Ito ay test build — hindi mula sa Play Store, kaya normal ang prompt.*

### 2. Mag-login (gamit ang TEST ACCOUNTS — hindi personal na numero!)

| Role | Numero | Password |
|---|---|---|
| Superadmin | `+639171000001` | `Demo123!` |
| Admin | `+639171000002` | `Demo123!` |
| Driver (Juan) | `+639171000003` | `Demo123!` |
| Driver (Pedro) | `+639171000004` | `Demo123!` |
| Driver (Jose) | `+639171000005` | `Demo123!` |
| Driver (Ana) | `+639171000006` | `Demo123!` |
| Driver (Rafael) | `+639171000007` | `Demo123!` |
| Driver (Miguel) | `+639171000008` | `Demo123!` |

- Ang login ay **numero + password + OTP** (SMS verification).
- Dahil test accounts ang nasa itaas, ang OTP ay **`123456`** lang —
  **PERO** kailangan muna itong naka-setup ng nag-setup (tingnan ang
  **Part D**). Walang totoong SMS na ipapadala.

### 3. Ano ang i-test (mabilis na checklist)

**Driver account (hal. +639171000003):**
- [ ] Home: nakikita ang posisyon sa pila (#) at ang ruta
- [ ] **Tapos Na Bumyahe** → lilitaw ang **Buong Kita Ngayon (₱)**
- [ ] Insidente: may card ng insidente + **Tawag** button
- [ ] Kita tab: listahan ng kita ng mga nakaraang araw

**Admin account (+639171000002):**
- [ ] Home: dashboard (kita, insidente, drivers, donasyon)
- [ ] Pila: mag-dispatch ng #1 sa Marilao–Monumento → mapupunta sa
      kabila bilang #1
- [ ] Kanselahin / Ibalik sa Pila ang isang entry
- [ ] Mga Ulat: Kasaysayan sa taas, charts sa ibaba

**Superadmin account (+639171000001):**
- [ ] Mga Account: kita ang 8 accounts
- [ ] **Profile tab → Mga Setting** (sa ilalim ng "Seguridad") →
      baguhin ang **Oras ng Reset** (12-hour, hal. "9:00 PM") → I-save →
      buksan ang Pila → dapat nagbago ang "Reset ng Pila • HH:00 __"
- [ ] **Profile tab → Talaan ng Aktibidad**: may listahan ng lahat ng
      ginawa (may pangalan)

### 4. Kapag may problema (troubleshooting)

| Problema | Sanhi | Solusyon |
|---|---|---|
| "App Check"/403 error, hindi makakita ng datos | Naka-ON ang App Check enforcement | Sa Firebase console → App Check → Enforcement → i-allow muna ang unverified requests (testing lang) |
| Hindi natatanggap ang OTP | Hindi naka-setup ang test numbers | Tingnan ang **Part D** (Part A ng `seed/README.md`) |
| Blangko ang app / walang laman | Hindi pa na-seed ang data | Patakbuhin ang `seed/seed.js --wipe` (tingnan `seed/README.md`) |
| "Invalid password" | Mali ang password | `Demo123!` (may `!` sa dulo) |

---

## PART D — Para sa nag-setup (ikaw): i-setup ang OTP test numbers

**Kailangan ito** — kung wala ito, ang OTP ay ipapadala sa TOTONG
numero ng test account (na hindi pagmamay-ari ng mga tester).

1. Firebase console → **Authentication** → **Sign-in method** → tab
   **Phone**
2. Sa ilalim ng **Phone numbers for testing**, i-add ang bawat test
   numero at code:

   | Phone number | Code |
   |---|---|
   | `+639171000001` | `123456` |
   | `+639171000002` | `123456` |
   | `+639171000003` | `123456` |
   | ... (hanggang `+639171000008`) | `123456` |

3. **Save** — walang SMS na ipapadala kahit kailan, walang bayad.

Bago i-share ang APK, tiyaking: (1) na-run ang `seed/seed.js`, (2)
naka-setup ang test numbers, (3) na-check na makaka-login kayo sa
inyong sariling phone gamit ang test account.
