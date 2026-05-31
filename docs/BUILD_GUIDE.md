# GasAppMxV3 — Build Guide (rebuild from zero)

Everything needed to stand up this project from scratch. Pair with [PLAN.md](PLAN.md) (design rationale)
and [PROGRESS.md](PROGRESS.md) (current status).

---

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | Required by Android Gradle Plugin |
| Android Studio | latest stable | SDK Platform 35, Build-Tools, an emulator (API 35) |
| Node.js | 20 LTS | For Cloud Functions (TypeScript) |
| Firebase CLI | latest | `npm i -g firebase-tools` |
| Google Cloud SDK | optional | for advanced billing/Secret Manager work |
| A Google account | — | for Firebase + Google Maps Platform |
| A GitHub account | — | repo is `GasAppMxV3` |

---

## 2. Firebase project setup (one-time, interactive)

1. `firebase login`
2. Create a project: `firebase projects:create gasappmxv3` (or via the console).
3. **Upgrade to the Blaze (pay-as-you-go) plan** — required because Cloud Functions make outbound calls
   to Google Maps and the CNE feed.
4. In **Google Cloud Billing → Budgets & alerts**, create a **$10/month budget alert** (50/90/100%).
5. Enable products in the Firebase console:
   - **Authentication** → Sign-in method → enable **Google**.
   - **Firestore Database** → create (production mode; rules below).
   - **Functions** (auto-enabled on first deploy).
   - **App Check** → register the Android app with the **Play Integrity** provider; for dev, register a
     **debug token** (printed in Logcat on first run).
6. `firebase init` in `firebase/` → select Functions (TypeScript), Firestore. Set `.firebaserc` default
   to your project id.

---

## 3. Google Maps Platform keys

Create **two** API keys (Google Cloud Console → APIs & Services → Credentials):

1. **Android SDK key** (for map display): restrict to *Android apps* with the app's package name +
   SHA-1. Enable **Maps SDK for Android**. This key ships in the app (it's safe when restricted) via
   `secrets.properties` → manifest placeholder. Map display has no meaningful per-call cost.
2. **Server key** (for paid APIs): restrict to the **Places API** + **Directions API** (and/or Distance
   Matrix). Store it in **Secret Manager** (`firebase functions:secrets:set MAPS_SERVER_KEY`) — it is
   **never** shipped in the APK. Only the Cloud Functions read it.

> Why two keys: the app needs a key to draw tiles, but the *paid* key must stay server-side so usage can
> be gated by auth + per-user quota. See PLAN.md "How this controls cost".

---

## 4. CNE/CRE data feed

Public XML, no key required:

- Places (metadata + coordinates): `https://publicacionexterna.azurewebsites.net/publicaciones/places`
- Prices: `https://publicacionexterna.azurewebsites.net/publicaciones/prices`

Both share a `<places><place>…</place></places>` shape:

```xml
<places>
  <place>
    <place_id>12345</place_id>
    <name>Estación X</name>
    <cre_id>PL/12345/EXP/ES/2017</cre_id>
    <location><x>-103.41</x><y>20.65</y></location>   <!-- x=lon, y=lat (places feed) -->
    <gas_price type="regular">23.05</gas_price>        <!-- prices feed -->
    <gas_price type="premium">24.99</gas_price>
    <gas_price type="diesel">24.30</gas_price>
  </place>
</places>
```

The **places** feed carries name/cre_id/coordinates; the **prices** feed carries the `gas_price` entries.
`cneIngest` fetches both, joins by `place_id`, normalizes, and caches the result. (Reference parsers:
V1 `~/git/GasAppMx/mobile/App.js` `parsePlaces`/`parsePrices`; V2
`~/git/CodexGasAppMx/backend-service/.../CneFeedParser.kt`.)

### Data model

The ~13.7k-station dataset is **not** stored per-document (that would blow past Firestore's free write
tier at 48 ingests/day). Instead:

```
Cloud Storage:  stations/latest.json.gz   # one gzipped JSON blob: { updatedAt, source, count, stations[] }
Firestore:      meta/freshness            { lastIngestAt, lastIngestIso, stationCount, source, datasetPath }
Firestore:      usage/{uid}/days/{yyyy-mm-dd}  { directions, places }   # server-written only
```

The app downloads the blob (authed read), gunzips it, and runs Haversine ranking on-device
(`CloudStationRepository`). The blob is stored as opaque gzip (no `contentEncoding` metadata) so GCS
never applies decompressive transcoding and the client always gunzips deterministically.

---

## 5. Cloud Functions

Location: `firebase/functions/`. Install + build:

```bash
cd firebase/functions
npm install
npm run build        # tsc type-check / compile to lib/
```

Functions (TypeScript):
- `cneIngest` — **scheduled** (every 30 min via Cloud Scheduler) — fetch + parse + write the gzipped
  blob to Storage + `meta/freshness`. Idempotent; keeps last-good data if a fetch fails or yields 0.
- `placesSearch` — **callable** — verifies Auth + App Check, checks/increments the daily Places quota,
  proxies Places Text Search with `MAPS_SERVER_KEY`, returns results or a resource-exhausted error.
- `directions` — **callable** — same gating, for real driving ETA to a selected station.
- `quota.ts` — shared helper enforcing the per-user daily caps (default **20 directions / 30 places**).

Set the server key secret first: `firebase functions:secrets:set MAPS_SERVER_KEY`.
Deploy: `firebase deploy --only functions,firestore:rules,storage:rules`.

Local dev: `firebase emulators:start` (Functions + Firestore + Storage + Auth). Android emulator reaches
the host at `10.0.2.2`.

### Security rules (summary)
- Storage `stations/**` and Firestore `meta/**`: **read** only for authenticated users; no client writes.
- Firestore `usage/**`: no client access — written only by functions (Admin SDK bypasses rules).

---

## 6. Android app

Location: `android/`. Built on V2's Compose app (`~/git/CodexGasAppMx/app`).

1. In the Firebase console, register an **Android app** with package **`mx.gasappmx`**, download
   `google-services.json`, and put it in `android/app/` (gitignored). The `google-services` Gradle
   plugin **fails the build if this file is missing**, so add it before the first build.
2. Put the Android SDK Maps key in `android/secrets.properties` (gitignored), injected into the manifest:
   ```
   GOOGLE_MAPS_API_KEY=AIza...androidKey
   ```
3. Reused/ported modules (see PLAN.md "Reusable assets"):
   `MarkerPriceTier`, `PriceLabelBitmapFactory`, `GasStation`/`FuelType`/`ResultLimit`, the Material 3
   theme, and the screen skeleton (relaid out as full-screen map + draggable bottom sheet).
4. Build/run: open `android/` in Android Studio, or
   ```bash
   cd android && ./gradlew assembleDebug
   ```

### Signing a release APK/AAB
```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gasappmx
# add signingConfig to app/build.gradle.kts referencing keystore props (keep keystore + passwords OUT of git)
cd android && ./gradlew bundleRelease assembleRelease
```
Register the release keystore's **SHA-1** in Firebase (Google Sign-In) and on the Maps SDK key.

---

## 7. Gotchas / checklist

- **App Check in debug:** register the debug token (Logcat) or sign-in + functions return 403 in dev.
- **Google Sign-In:** both debug and release SHA-1/SHA-256 must be in the Firebase console.
- **Emulator networking:** use `10.0.2.2` to reach host-run emulators from the Android emulator.
- **Key not in APK:** after a release build, verify the server key is absent:
  `unzip -p app-release.apk | strings | grep -i <server-key-prefix>` → should find nothing.
- **CNE feed flakiness:** the Azure host can be slow/down — ingest must keep last-good data; the app must
  tolerate stale data and show a "updated X min ago" indicator.
- **Quota tuning:** caps live in `quota.ts`; raise/lower without redeploying the app.
