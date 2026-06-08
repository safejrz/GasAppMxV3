# GasAppMxV3 — Progress Tracker

> **Living document.** Update this in the **same commit** as any code change: flip a checkbox, set the
> date, jot a note, and rewrite "Current focus" + "Next step". If a session is interrupted, start here.

---

## ⏱️ Current focus
**Phase 1 plan verified complete (2026-06-07).** All F1–F11 are done and validated in this tracker.
App is on Play Store Internal Testing and **testers can sign in** (multiple devices confirmed
2026-05-31). Remaining work is **post-plan release work**: finish Play Console store listing,
testing-country setup, and submit for Production access.

> **Fix that unblocked tester sign-in (2026-05-31):** Google Play re-signs the AAB with its own key.
> Registered Google's signing SHA-1 `33:AD:F6:...:C0:BD` in Firebase → sign-in worked for all testers,
> no rebuild needed. See BUILD_GUIDE §6b.

## ▶️ Next step (post-plan release sequence)

### 1. Complete Play Console store listing
- Finalize text/assets checklist in `docs/STORE_LISTING.md`.
- Confirm privacy policy URL is published and matches app behavior.

### 2. Finalize Internal Testing rollout settings
- Confirm tester groups, country availability, and release notes are set.
- Keep one active internal track build while collecting sign-in and map stability feedback.

### 3. Request Production access
- Submit the required Play Console declarations and production access request.
- After approval, promote the validated internal build to Production.

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
| F2 | CNE data pipeline (`cneIngest` → Storage) | ✅ | 2026-05-31 | Running every 30 min, 13,727 stations. Confirmed in logs + live on device. |
| F3 | Full-screen map + draggable bottom sheet | ✅ | 2026-05-31 | Working on emulator + Samsung S23 FE. |
| F4 | Station data (`CloudStationRepository`) | ✅ | 2026-05-31 | Blob downloads, gunzips, Haversine ranking confirmed working. |
| F5 | Price color tiers (green→red markers) | ✅ | 2026-05-31 | Confirmed visually — green/yellow/orange/red markers showing on map. |
| F6 | Location + Haversine ranking, filters | ✅ | 2026-05-31 | Location, fuel type + Top N filters working on both devices. |
| F7 | Google Sign-In + App Check | ✅ | 2026-05-31 | Working on emulator + real device. Two debug tokens registered. |
| F8 | Places search + Directions ETA gateway | ✅ | 2026-05-31 | Working on S23 FE — search bar + "Ver ruta exacta" confirmed. |
| F9 | Navigation deep-link (Google Maps) | ✅ | 2026-05-31 | Confirmed working on S23 FE. |
| F10 | Polish, error states, offline fallback | ✅ | 2026-05-31 | Done. |
| F11 | Signed release APK/AAB | ✅ | 2026-05-31 | Release APK (3.7 MB) + AAB (6.4 MB) built, signed (v2), verified on Samsung S23 FE. |

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
- **2026-06-07** — Plan verification pass completed: all Phase 1 items in `PLAN.md` map to completed
  checklist entries F1–F11 in this file. Tracker focus switched to post-plan Play Console release work.

## Manual steps completed
- [x] `firebase login` as ingjrz@gmail.com _(2026-05-30)_
- [x] Firebase project `gasappmxv3` created _(2026-05-30)_
- [x] Blaze plan enabled _(2026-05-31)_
- [x] Firestore database created _(2026-05-31)_
- [x] Firebase Storage bucket created _(2026-05-31)_
- [x] `google-services.json` downloaded to `android/app/` _(2026-05-31)_
- [x] **Google Maps Android API key** created + added to `secrets.properties` _(required for F3/F4 done)_
- [x] **Google Sign-In enabled** in Firebase Auth console _(2026-05-31)_
- [x] **Debug SHA-1** registered in Firebase project settings _(2026-05-31)_
- [x] **`google-services.json` re-downloaded** after SHA-1 added _(2026-05-31)_
- [x] **App Check debug token** registered in console _(required for F7 verification)_
- [x] **Maps server key** set as `MAPS_SERVER_KEY` secret + functions redeployed _(required for F8 done)_
- [x] **Release keystore** generated + release APK/AAB built _(required for F11 done)_
