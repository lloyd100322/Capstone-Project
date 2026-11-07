# EnerJeep — Full Firestore Reset and Three-Month Dataset

Ginagamit ng seeder ang mga kasalukuyang EnerJeep accounts bilang source ng pangalan, phone number, role, user ID at plate number. Hindi ito gumagawa ng bagong login accounts.

**Mahalaga:** Synthetic presentation/test records ang bagong queue history, incident scenarios, ledger entries, donations at activity logs. Hindi ito aktuwal na cooperative transactions o bookkeeping evidence.

## Ano ang pinananatili

Hindi gagalawin ang mga sumusunod:

- `users` — lahat ng kasalukuyang account profile at account fields
- `userSecrets` — password hashes at salts
- Firebase Authentication users — lahat ng kasalukuyang login identities
- `driverPlateClaims` — unique plate ownership na nakatali sa accounts
- `secureSessions` — kasalukuyang secure login sessions

Hindi babaguhin ang account status, roles, admin stations, profile details, password credentials o Firebase Auth users.

**Tatanggalin ang lahat ng subcollections sa ilalim ng bawat `users/{account}` document**, kabilang ang lumang notifications. Mananatili ang parent account documents.

## Ano ang lilinisin

Sa `--apply`, recursively lilinisin ang lahat ng existing Firestore root collections maliban sa limang account/auth collections sa itaas. Kasama rito ang lahat ng lumang documents at nested subcollections sa:

- `queue` — kasalukuyang pila, cancelled entries at lahat ng lumang dispatch/history
- `incidents` — lahat ng lumang incident reports at incident test records
- `ledger` — lahat ng lumang dispatch earnings at fine/payment records
- `kita` — lahat ng lumang Kita records
- `donations` — lahat ng lumang cooperative fund entries
- `emergencyContacts`, `activityLogs`, `notifications`
- `settings` — lahat ng lumang settings at operations index
- iba pang non-account collections na makita sa Firestore project, kahit hindi nakalista rito

Pagkatapos, gagawa ang seeder ng bagong operational dataset at bagong default settings. Ibig sabihin, mare-reset din ang dating deduction-tier at queue-rotation settings sa defaults ng seeder.

Ang mga existing document IDs na kapareho ng bagong dataset ay papalitan ng bagong records. Lahat ng iba pang non-account documents ay buburahin. Kaya walang maiiwang lumang queue, Kita, incident, ledger, donation o dating test data.

## Saklaw ng bagong records

**Tatlong Manila calendar months lang:** kasalukuyang buwan at ang nakaraang dalawang buwan. Halimbawa, kung Nobyembre ang kasalukuyang buwan, September 1 hanggang sa kasalukuyang oras/petsa ng Nobyembre ang saklaw.

Lahat ng generated timestamped records ay vine-validate bago mag-preview o magsulat. Hindi kasama sa three-month limit ang account profiles, password credentials, Firebase Auth identities, plate claims at secure sessions dahil pinananatili ang mga iyon.

Ang bagong dataset ay bumubuo ng:
- current queue entries at three-month dispatch history
- incident reports sa loob ng date window
- dispatch earnings at paid-fine ledger entries
- cooperative fund/donation entries
- emergency contacts, activity logs at account notifications
- bagong deduction tiers, queue rotation at queue revision settings

## Bago patakbuhin

1. Siguraduhing may active Superadmin, Admin at hindi bababa sa apat na active Driver accounts.
2. Siguraduhing kumpleto ang required driver profile fields at valid ang bawat admin station.
3. Kailangan ang Node.js at internet connection.
4. Gumawa ng Firebase service-account key sa Firebase Console → Project settings → Service accounts.
5. Itago ang JSON key sa labas ng repository. Huwag itong i-commit o i-upload sa GitHub.

## Windows / PowerShell

Pumunta sa `seed` folder ng local project:

~~~powershell
cd C:\Users\Dave\AndroidStudioProjects\Capstone-Project3\seed
npm install
~~~

Palitan ang service-account path ayon sa aktuwal na lokasyon ng JSON key.

### 1. Preview muna — walang mababago sa Firestore

~~~powershell
node setup.js --serviceAccount "C:\Users\Dave\Downloads\serviceAccount.json" --project enerjeep-b8f3a
~~~

Ipinapakita ng preview ang:
- target Firebase project
- bilang ng account profiles na pananatilihin
- bagong three-month dataset na gagawin
- bilang ng lumang Kita at incident records
- bilang ng ibang non-account records na papalitan o buburahin

**Walang isinusulat o binubura ang preview.** Suriin ang project ID at lahat ng bilang. Gumawa muna ng Firestore backup/export bago mag-apply dahil lahat ng non-account data sa project ay lilinisin.

### 2. I-apply ang clean reset

Kapag tama ang preview at may backup ka na, patakbuhin ang kaparehong command at idagdag ang `--apply`:

~~~powershell
node setup.js --serviceAccount "C:\Users\Dave\Downloads\serviceAccount.json" --project enerjeep-b8f3a --apply
~~~

Isinusulat muna ang bagong records; pagkatapos, buburahin ang lahat ng lumang non-account documents at gagawa ng bagong `settings/operationsIndex`. Hindi gagalawin ang account profiles at credentials.

### 3. Pagkatapos ng successful run

1. Isara at buksan muli ang EnerJeep app.
2. Mag-login gamit ang dati nang account credentials.
3. I-check ang Queue, Incidents, Kita/Cooperative Fund, Ledger, Reports at Notifications ayon sa role.

Hindi kailangan ng Firebase Functions o Firestore Rules deployment para lamang patakbuhin ang seeder. Direktang sumusulat ang Firebase Admin SDK sa Firestore.

## Importanteng paalala

- Ang pag-install o pag-update ng APK ay hindi naglilinis ng Firestore. Kailangan ang preview at saka ang `--apply`.
- Huwag i-share ang service-account JSON file o ang laman nito.
- Kapag may validation error sa account records, hihinto ang script bago simulan ang writes. Ayusin muna ang account data at ulitin ang preview.
