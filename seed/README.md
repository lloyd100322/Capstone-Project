# EnerJeep — Operational Data Refresh

Ginagawa ng tool na ito ang kumpletong operational records na ginagamit ng EnerJeep. Ang mga pangalan, phone number, role, user ID, plaka at iba pang detalye ng account ay kinukuha mula sa kasalukuyang Firestore collection na users, kaya hindi kailangang gumawa ng panibagong login accounts.

Ang mga record na mabubuo ay lalabas sa app gamit ang normal na labels at format na ginagamit ng EnerJeep. Walang karagdagang label sa UI na nagsasabing espesyal na dataset ang record.

Ang mga lokasyon sa incident records ay pinili lamang mula sa mga stop at terminal point na nakatala sa LTFRB route feed para sa [Heritage Homes - Monumento via NLEX](https://www.transit.land/explore/operators/o-wdw-ltfrb/routes/r-wdw5-heritagehomes~monumentovianlex). Kabilang dito ang NLEX Marilao, Meycauayan, Valenzuela, Caloocan, at Samson Road terminal sa Monumento-side corridor. May allowlist validation din ang generator upang hindi makapasok ang ibang lokasyon.

**Paalala:** Ang mga queue history, incident scenario, donation at financial entries na ginagawa ng tool ay synthetic presentation records. Hindi ito kapalit ng aktuwal na cooperative records at hindi dapat gamitin bilang tunay na bookkeeping o operational evidence.

## Ano ang napupunan

| Firestore collection | Mga record na ginagawa |
|---|---|
| users | Lahat ng kasalukuyang account profiles ay ginagamit bilang source; hindi binabago |
| queue | Kasalukuyang pila para sa bawat active driver sa dalawang ruta, dispatch history sa loob ng kasalukuyan at nakaraang dalawang buwan (3 calendar months), at cancelled entry |
| ledger | Dispatch earnings para sa bawat driver, gross/net values at seat deductions, kasama ang hiwalay na bayad ng multa |
| incidents | Mga bukas at naayos na incident reports, may driver, plaka, lokasyon, petsa, detalye at penalty |
| donations | Mga natanggap na ambag at nagamit na pondo na may petsa, source at notes |
| settings | Default deduction tiers kung may kulang, queue rotation metadata at queue revision |
| emergencyContacts | National emergency hotline at dispatcher/admin contact mula sa aktuwal na admin account |
| activityLogs | Mga entry para sa dispatch, incident, settings at iba pang aktibidad |
| users/{phone}/notifications | Unread incident at dispatch notifications para sa bawat active role |

### Saklaw ng bawat account role

**Driver**
- May sariling active queue entry.
- May sariling dispatch earnings history sa ledger.
- Nakikita ang shared incident notices, parehong bukas at naayos.
- May unread notifications at normal na profile details mula sa kasalukuyang account.

**Admin**
- May laman ang dashboard statistics, pila, incident reports, fines, donations at income reports.
- Naka-link sa sariling station kung mayroon nang adminStation ang account.
- May notifications at activity trail.

**Superadmin**
- May dashboard at operational reports.
- May deduction tiers at queue rotation settings.
- May admin/dispatcher contacts at activity logs.
- May unread notifications.

## Mahalagang proteksyon sa account

Hindi binabago ng tool na ito ang mga sumusunod:

- users — lahat ng kasalukuyang account profile at detalye
- userSecrets — lahat ng password hashes at salts
- Firebase Authentication phone accounts
- driverPlateClaims
- FCM tokens at account status
- Existing account roles at admin stations

Hindi rin ito gumagawa ng bagong password at hindi nito nire-reset ang password ng kahit anong account. Gagamitin ng bagong operational records ang eksaktong user ID, pangalan, phone number at plate number na nasa kasalukuyang account documents.

Ang tool ay hindi gumagamit ng broad database wipe. Tinatanggal lamang nito ang:
1. eksaktong operational document IDs na ginawa ng dating setup.js;
2. eksaktong per-account incident notification IDs na ginawa ng dating incident trigger; at
3. dokumentong IDs na naka-record sa internal settings/operationsIndex mula sa naunang takbo ng bagong version.

Ang ibang Firestore records na hindi pag-aari ng dataset refresh ay pinananatili.

## Bago patakbuhin

1. Siguraduhing may existing active account para sa superadmin, admin, at hindi bababa sa apat na driver.
2. Siguraduhing ang bawat active driver ay may userId, first name, last name, phone number, plate number, license number, seater at emergency contact name/number. Kailangan ding may valid na adminStation (marilao o monumento) ang bawat admin account.
3. Kailangan ang Node.js at internet connection.
4. Kumuha ng Firebase service-account key sa Firebase Console → Project settings → Service accounts → Generate new private key.
5. Ilagay ang JSON key sa labas ng repository, halimbawa sa Downloads folder. Huwag itong i-commit o i-upload sa GitHub.

## Windows / PowerShell instructions

Buksan ang PowerShell at pumunta sa seed folder ng local clone mo. Halimbawa:

~~~powershell
cd C:\Users\Dave\AndroidStudioProjects\Capstone-Project5\seed
npm install
~~~

### 1. Preview muna — walang isusulat sa Firestore

Palitan ang file path sa command ng aktuwal na lokasyon ng service-account JSON file mo.

~~~powershell
node setup.js --serviceAccount "C:\Users\Dave\Downloads\serviceAccount.json" --project enerjeep-b8f3a
~~~

Iche-check nito ang kasalukuyang accounts at ipapakita ang bilang ng records na lilinisin at gagawin. **Walang mababago sa Firestore sa preview mode.**

Tiyaking ang target project ay enerjeep-b8f3a at tama ang bilang ng active Superadmin, Admin at Drivers.

### 2. I-apply ang bagong operational records

Kapag tama ang preview, patakbuhin ang kaparehong command at idagdag ang --apply.

~~~powershell
node setup.js --serviceAccount "C:\Users\Dave\Downloads\serviceAccount.json" --project enerjeep-b8f3a --apply
~~~

Gagamit ang script ng Firebase Admin SDK. Dahil dito, kailangan talagang tama ang project at service-account key bago mag-apply.

### 3. Pagkatapos ng successful run

1. Isara at buksan muli ang EnerJeep app.
2. Mag-login gamit ang mga dati nang account credentials.
3. Gamitin pa rin ang nakatakdang Firebase Phone Auth test numbers at OTP codes kung iyon ang kasalukuyang login setup.
4. Tingnan ang Home, Pila, Insidente, Kita, Mga Ulat, Profile, Settings, Talaan at notification bell ayon sa role.

Hindi kailangan ng Firebase Functions o Firestore Rules deployment para lamang maisulat ang records na ito. Ang script ay direktang sumusulat sa Firestore gamit ang Admin SDK.

## Pag-uulit ng refresh

Puwedeng ulitin ang preview at apply. Gumagamit ang script ng stable document IDs at internal index para palitan ang sarili nitong dating records, hindi para magdagdag nang magdagdag ng duplicate entries.

**Huwag magdagdag ng --wipe.** Wala nang broad wipe option ang bagong tool dahil kailangang manatiling ligtas ang kasalukuyang user accounts at credentials.

## Kapag may validation error

Kung may account field na kulang o walang active account para sa isa sa mga role, ititigil ng script ang proseso bago magsulat. Ayusin muna ang kasalukuyang account sa app at patakbuhin muli ang preview.

Kung may ibang operational records na ayaw mong palitan, huwag mong baguhin ang document IDs o ang settings/operationsIndex nang mano-mano.
