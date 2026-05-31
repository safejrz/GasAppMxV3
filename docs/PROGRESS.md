# GasAppMxV3 — Progress Tracker

> **Living document.** Update this in the **same commit** as any code change: flip a checkbox, set the
> date, jot a note, and rewrite "Current focus" + "Next step". If a session is interrupted, start here.

---

## ⏱️ Current focus
**First debug build succeeded** (2026-05-31, `app-debug.apk` 18 MB). All Android code compiles.
Backend is live on Blaze — `cneIngest` scheduled and deployed. The app is not yet functional
end-to-end because the Maps API key is a placeholder and Google Sign-In is not yet wired to real
credentials.

## ▶️ Next step (ordered — do these in sequence)

### 1. Verify the CNE data pipeline is running (F2)
- Open **Firebase console → Storage** for project `gasappmxv3`.
- Confirm `stations/latest.json.gz` exists. If not, check Cloud Logging →
  Cloud Run → `cneingest` service for errors.

### 2. Get a Google Maps API key (F3/F4 unblock)
- Google Cloud Console → APIs & Services → Credentials → **Create credential → API key**.
- Restrict it: Application restrictions → **Android apps** → add package `mx.gasappmx` +
  SHA-1 `FA:15:46:2E:C4:FF:15:32:83:2E:B8:6E:67:67:5B:99:9C:D3:C9:82`.
- Enable only **Maps SDK for Android**.
- Add the key to `android/secrets.properties` (gitignored):
  ```
  GOOGLE_MAPS_API_KEY=AIza...your-android-key-here
  ```
- Rebuild: `cd android && ./gradlew assembleDebug`

### 3. Wire Google Sign-In (F7 unblock)
Already done in code — just needs these console steps:
- **Firebase console → Authentication → Sign-in method → Google → Enable** (set support email → Save).
- **Firebase console → Project settings → Your apps → `mx.gasappmx` → Add fingerprint**:
  - Debug SHA-1: `FA:15:46:2E:C4:FF:15:32:83:2E:B8:6E:67:67:5B:99:9C:D3:C9:82`
- **Re-download `google-services.json`** after adding the fingerprint (it gets baked in) →
  replace `android/app/google-services.json` → rebuild.

### 4. Register App Check debug token (F7 unblock)
- Install the debug APK on a device/emulator and open Logcat.
- Search for `DebugAppCheckToken` — copy the token printed there.
- **Firebase console → App Check → Apps → `mx.gasappmx` → Manage debug tokens → Add**.
- Without this, Storage reads and function calls will be rejected in dev.

### 5. Install and smoke-test on a device
```bash
# USB-connected Android device with USB Debugging enabled:
~/Android/Sdk/platform-tools/adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
Expected: sign-in screen → Google auth → map loads with colored price markers → tap station → Navegar opens Google Maps navigation.

### 6. Enable the paid API gateway (F8 backend — do after smoke test)
- Create a **server-side Maps API key** (no app restriction; restrict to Places API +
  Directions API only) in Google Cloud Console.
- Set the secret: `firebase functions:secrets:set MAPS_SERVER_KEY` (paste key when prompted).
- Uncomment lines in `firebase/functions/src/index.ts`:
  ```typescript
  export { placesSearch } from "./places";
  export { directions } from "./directions";
  ```
- Redeploy: `cd firebase && firebase deploy --only functions --project gasappmxv3 --force`
- Test: search for a place in the app → map recenters; tap "Ver ruta exacta" in station detail.

### 7. Build the signed release APK (F11 — final step)
```bash
# Generate a release keystore (do this once, keep the file safe — not in git):
keytool -genkey -v -keystore android/release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias gasappmx

# Add to android/secrets.properties:
# RELEASE_KEYSTORE_PATH=release.jks
# RELEASE_KEY_ALIAS=gasappmx
# RELEASE_STORE_PASSWORD=yourpassword
# RELEASE_KEY_PASSWORD=yourpassword
```
Then ask Claude to wire the signingConfig into `android/app/build.gradle.kts` and build the AAB.
- Register the **release SHA-1** in Firebase (same place as debug SHA-1) before publishing.

---

## 🏃 How to build and run today

```bash
# Build debug APK (from repo root):
cd android && export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk

# Install on connected device:
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check device is connected:
~/Android/Sdk/platform-tools/adb devices

# Deploy Firebase functions (from repo root):
export PATH="$HOME/.npm-global/bin:$PATH"
cd firebase && firebase deploy --only functions --project gasappmxv3 --force
```

---

## Phase 1 — feature checklist

Legend: ⬜ not started · 🟨 in progress · ✅ done · 🔵 blocked (needs interactive/cloud step)

| ID | Feature | Status | Updated | Notes |
|----|---------|--------|---------|-------|
| F1 | Scaffold & infra (repo, docs, GitHub) | ✅ | 2026-05-30 | Done. |
| F2 | CNE data pipeline (`cneIngest` → Storage) | 🟨 | 2026-05-31 | Deployed + scheduled. **Verify** `stations/latest.json.gz` exists in Firebase Storage. |
| F3 | Full-screen map + draggable bottom sheet | 🟨 | 2026-05-31 | Code done, builds. 🔵 Needs real Maps API key in `secrets.properties` to show map. |
| F4 | Station data (`CloudStationRepository`) | 🟨 | 2026-05-31 | Code done. 🔵 Needs F2 verified + F7 auth to read Storage blob. |
| F5 | Price color tiers (green→red markers) | 🟨 | 2026-05-31 | Code done (ported `MarkerPriceTier` + bitmap factory). Needs F3 + F4 to verify visually. |
| F6 | Location + Haversine ranking, filters | 🟨 | 2026-05-31 | Code done. Needs device test with location permission. |
| F7 | Google Sign-In + App Check | 🟨 | 2026-05-31 | Code done. 🔵 Needs: Google sign-in enabled in console, SHA-1 registered, `google-services.json` re-downloaded, App Check debug token registered. |
| F8 | Places search + Directions ETA gateway | 🟨 | 2026-05-31 | Android done. 🔵 Backend needs `MAPS_SERVER_KEY` secret + uncomment + redeploy (Step 6 above). |
| F9 | Navigation deep-link (Google Maps) | 🟨 | 2026-05-31 | Code done. Needs device test. |
| F10 | Polish, error states, offline fallback | ✅ | 2026-05-31 | Done (loading/error states, in-memory cache, test fixes). |
| F11 | Signed release APK/AAB | ⬜ | — | See Step 7 above. |

---

## Decisions & deviations log

- **2026-05-30** — Stack/architecture/quotas locked per `PLAN.md`.
- **2026-05-30** — **Data model deviation:** station dataset cached as a single gzipped JSON blob in
  Cloud Storage (`stations/latest.json.gz`), not per-document in Firestore. Reason: ~13.7k docs ×
  48 ingests/day would exceed Firestore free write tier. App downloads + gunzips + ranks on-device.
- **2026-05-30** — Auth uses modern **Credential Manager** (`androidx.credentials` + `googleid`), not
  deprecated `GoogleSignInClient`.
- **2026-05-31** — First build succeeded. Fixed `FunctionsClient.getData()` — `HttpsCallableResult`
  in firebase-functions-ktx BOM 33 exposes `getData()` returning `Any?`, not a typed generic.

## Manual steps completed
- [x] `firebase login` as ingjrz@gmail.com _(2026-05-30)_
- [x] Firebase project `gasappmxv3` created _(2026-05-30)_
- [x] Blaze plan enabled _(2026-05-31)_
- [x] Firestore database created _(2026-05-31)_
- [x] Firebase Storage bucket created _(2026-05-31)_
- [x] `google-services.json` downloaded to `android/app/` _(2026-05-31)_
- [ ] **Google Maps Android API key** created + added to `secrets.properties` _(Step 2)_
- [ ] **Google Sign-In enabled** in Firebase Auth console _(Step 3)_
- [ ] **Debug SHA-1** registered in Firebase project settings _(Step 3)_
- [ ] **`google-services.json` re-downloaded** after SHA-1 added _(Step 3)_
- [ ] **App Check debug token** registered in console _(Step 4)_
- [ ] **Maps server key** set as `MAPS_SERVER_KEY` secret + functions redeployed _(Step 6)_
- [ ] **Release keystore** generated + release APK/AAB built _(Step 7)_
