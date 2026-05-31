# GasAppMxV3 — Progress Tracker

> **Living document.** Update this in the **same commit** as any code change: flip a checkbox, set the
> date, jot a note, and rewrite "Current focus" + "Next step". If a session is interrupted, start here.

---

## ⏱️ Current focus
Project `gasappmxv3` created. Android app ported + **UI reworked (full-screen map + draggable bottom
sheet)** and **auth implemented (Google Sign-In gate + App Check + sign-out)**. All Android code is
written but **uncompiled** — needs Android Studio + `google-services.json`. Backend deploy is blocked on
**Blaze**.

## ▶️ Next step
1. **User:** upgrade `gasappmxv3` to **Blaze** + **$10 budget alert** (console — see manual steps).
2. **User:** register Firebase **Android app** (package `mx.gasappmx`), enable **Google** sign-in
   provider, register debug **SHA-1** + **App Check debug token**, drop `google-services.json` into
   `android/app/`. Then build in Android Studio — fix any compile errors this surfaces (first build of
   uncompiled port).
3. Once Blaze: Claude deploys rules + functions, sets `MAPS_SERVER_KEY`, confirms `cneIngest` writes
   `stations/latest.json.gz` (F2 live).
4. F8: wire the app's Places search + Directions ETA to the gated callable functions.
5. F10/F11: polish, then signed APK.

## 🏃 How to run what exists today
- **Docs only so far.** No app/functions are runnable yet.
- Functions skeleton lives in `firebase/functions/` — `npm install` there once the cloud project exists,
  then `npm run build` to type-check.

---

## Phase 1 — feature checklist

Legend: ⬜ not started · 🟨 in progress · ✅ done · 🔵 blocked (needs interactive/cloud step)

| ID | Feature | Status | Updated | Notes |
|----|---------|--------|---------|-------|
| F1 | Scaffold & infra (repo, docs, gitignore, GitHub push) | 🟨 | 2026-05-30 | Local scaffold + functions skeleton done; cloud setup 🔵 (needs `firebase login`) |
| F2 | CNE data pipeline (`cneIngest` → Storage blob) | 🟨 | 2026-05-30 | Parser **validated on live feed: 13,727 stations**. Needs deploy + scheduler + Storage bucket. Writes gzipped JSON to Storage (not per-station Firestore) for cost. |
| F3 | App shell & big-map UI (full-screen map + bottom sheet) | 🟨 | 2026-05-30 | **Done in code:** `BottomSheetScaffold` — near-full-screen `GoogleMap` + draggable results/detail sheet, top overlay w/ title + Salir. Needs compile/visual verify. |
| F4 | Station data source (`CloudStationRepository`, ranking) | 🟨 | 2026-05-30 | Written: downloads Storage `latest.json.gz`, gunzips, Haversine + cheapest-first rank on-device. Needs live data + auth to verify. |
| F5 | Price color tiers (green→red markers) | ⬜ | — | Port `MarkerPriceTier` + `PriceLabelBitmapFactory` |
| F6 | Location & ranking (Haversine, fuel + Top-N filters) | ⬜ | — | FusedLocation + on-device sort |
| F7 | Auth (Google Sign-In + App Check) | 🟨 | 2026-05-30 | **Done in code:** `GasApplication` installs App Check (debug/Play Integrity); `AuthManager` Google sign-in via Credential Manager; `SignInScreen` gate in `MainActivity`. Needs `google-services.json` + SHA-1 + debug token to run. |
| F8 | Paid API gateway (Places + Directions + quota) | ⬜ | — | Conservative caps: 20 dir / 30 places per user/day |
| F9 | Navigation deep-link (open Google Maps app) | 🟨 | 2026-05-30 | **Done in code:** `openGoogleMapsNavigation` (`google.navigation:q=`) + manifest `<queries>` for Android 11+ visibility. Needs device verify. |
| F10 | Polish & resilience (states, offline cache, copy) | ⬜ | — | — |
| F11 | Release (keystore, signed AAB/APK, verify cost guard) | ⬜ | — | Phase-1 finish line |

---

## Decisions & deviations log
_Record anything that diverges from `PLAN.md`, with a date._

- **2026-05-30** — Project created. Stack/architecture/quotas locked per `PLAN.md`. Firebase CLI not yet
  installed on the dev machine; cloud provisioning deferred to an interactive session.
- **2026-05-30** — F3/F7 implemented (uncompiled). UI reworked to `BottomSheetScaffold` (full-screen
  map + draggable sheet). Auth uses the **modern Credential Manager + Sign in with Google** flow
  (`androidx.credentials` + `googleid`), not the deprecated `GoogleSignInClient`. App Check uses the
  debug provider on debug builds, Play Integrity on release. Sign-out also clears the credential state.
  Avoided `material-icons-extended` (used a text "Salir" button) to keep the APK lean.
- **2026-05-30** — Android baseline ported from V2: package `mx.codexgasapp` → `mx.gasappmx`, theme
  `CodexGasAppTheme` → `GasAppTheme`, style `Theme.GasAppMx`, app id `mx.gasappmx`. Dropped the Ktor
  backend client (`ApiStationRepository`, `StationApiModels`) + its tests. **Naming note:** the
  Firestore-repo from PLAN.md is implemented as **`CloudStationRepository`** (it reads the Cloud
  Storage blob, not a Firestore collection — consistent with the data-model deviation below). Kept
  `SampleStationRepository` for offline UI dev. Blob path is `stations/latest.json.gz` (opaque gzip, no
  `contentEncoding`, so the client gunzips deterministically). Android code is **not yet compiled** —
  needs Android Studio + `google-services.json`.
- **2026-05-30** — **Deviation from PLAN.md data model:** the bulk station dataset is cached as a single
  gzipped JSON blob in **Cloud Storage** (`stations/latest.json`), not as a `stations/*` Firestore
  collection. Reason: ~13.7k stations × 48 ingests/day would far exceed Firestore's free write tier and
  add cost. The app downloads the blob (authed read) and runs Haversine locally — same UX, near-zero
  write cost. Firestore still holds `meta/freshness` + per-user `usage`. Functions verified to compile;
  parser verified against the live CNE feed.

## Manual / interactive steps owed by the user
_Things Claude cannot do non-interactively — track them here so they aren't forgotten._

- [x] `firebase login` (ingjrz@gmail.com) and create project **`gasappmxv3`**. _(done 2026-05-30)_
- [ ] Upgrade `gasappmxv3` to **Blaze** + create **$10 budget alert** in Google Cloud Billing.
      Console: https://console.firebase.google.com/project/gasappmxv3/usage/details → "Modify plan".
- [ ] Create two Google Maps API keys: (a) restricted **Android SDK** key for map display, (b)
      server-side key stored in **Secret Manager** for the Places/Directions functions.
- [ ] Register debug + release **SHA-1/SHA-256** fingerprints in Firebase (for Google Sign-In).
- [ ] Enable the **Google** sign-in provider in Firebase Auth (this also provisions the OAuth web
      client that becomes `default_web_client_id`, which `AuthManager` reads).
- [ ] Register an **App Check debug token** (printed in Logcat on first debug run) in the console, or
      sign-in/Storage/functions will be rejected in dev.
- [ ] Place `google-services.json` in `android/app/` (gitignored).
