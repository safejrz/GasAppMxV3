# GasAppMxV3 — Build Plan

> **Preserved design record.** This is the original Phase-1 plan, approved 2026-05-30. It is kept for
> historic/documentation reasons — do **not** overwrite it as the project evolves. Track live status in
> [PROGRESS.md](PROGRESS.md) and record design changes as dated addenda at the bottom of this file.

## Context

The user wants a mobile app to find the **cheapest nearby gas stations** in Mexico (like the Google
Maps fuel-price feature in the USA), using the public **CNE/CRE price feed**. Prices are shown on a map
as colored tags: **green = cheapest → red = most expensive**, with a yellow/orange gradient in between.

Two prior attempts exist:
- **V1 `~/git/GasAppMx`** — React Native + Expo + JS. Fetches CNE XML directly on-device, has working
  Haversine, price-tier, deep-link-nav, and caching logic. **Weak UI**, monolithic 796-line `App.js`.
- **V2 `~/git/CodexGasAppMx`** — Native Kotlin + Jetpack Compose + Material 3 (**the professional UI in
  the user's screenshot**) **+ a self-hosted Ktor backend** the user disliked. No auth, no rate-limiting,
  Maps key embedded in the APK, and a cramped 260dp map *card*.

**V3 goal:** combine V2's polished native UI with a *bigger* map, add Google sign-in + per-user
rate-limiting to keep Google Maps API costs affordable (without a server to maintain), and ship a
**functional, production-ready signed Android APK by the end of Phase 1**. All progress pushed to a new
GitHub repo **GasAppMxV3**.

## Locked decisions (from user)

| Decision | Choice |
|---|---|
| Tech stack | **Native Kotlin + Jetpack Compose**, built on V2's `app/` module. Android-only for Phase 1. |
| Backend | **Drop V2's Ktor backend.** Use **Firebase (serverless)**. |
| Auth | **Firebase Auth — Google Sign-In** + **Firebase App Check**. |
| Cloud | Firebase **Cloud Functions** (TypeScript) + **Firestore** + Cloud Scheduler. |
| Billing | **Blaze plan** + a low monthly **budget alert (~$10)**. |
| Paid Google APIs | **Places** (address search) + **Directions/Distance Matrix** (real ETA). |
| Per-user quotas | **Conservative**: ~20 Directions + ~30 Places lookups / user / day. |
| Map UI | **Near-full-screen map** with a **draggable bottom-sheet** results list (Google-Maps-style). |
| Phase-1 target | **Signed Android APK/AAB**, real CNE data, auth + quotas working. |

## Architecture

The app **never** calls paid Google APIs or the CNE feed directly, and **never** ships the paid Maps key.
Everything paid/limited funnels through Firebase, where it is gated.

```
Android app (Kotlin/Compose)
  ├─ Map display (Maps SDK)             → FREE, client-side
  ├─ Deep-link nav (google.navigation:) → FREE, client-side
  ├─ Distance ranking (Haversine)       → FREE, client-side
  ├─ Reads cached CNE stations          → Firestore (read-only rules)
  ├─ Google Sign-In                     → Firebase Auth
  └─ Places / Directions                → Cloud Functions (callable) ONLY
                                             ├─ verify Auth + App Check
                                             ├─ check+increment per-user daily quota (Firestore)
                                             ├─ call Google with server-side key (Secret Manager)
                                             └─ return result / 429 if over quota

Cloud Scheduler ──(every ~30 min)──► cneIngest function
                                       ├─ fetch CNE prices + places XML
                                       ├─ parse + join + normalize
                                       └─ write JSON to Firestore (global cache, hit once, not per-user)
```

**Why this controls cost:** worst-case spend = (signed-in users) × (daily cap) × (per-call price), a
bounded number. App Check blocks scripts from draining the key; the scheduled ingest hits CNE once
globally instead of once per user; map display + deep-link nav are free.

**CNE feed endpoints** (proven in V1 & V2):
- Prices: `https://publicacionexterna.azurewebsites.net/publicaciones/prices`
- Places: `https://publicacionexterna.azurewebsites.net/publicaciones/places`
- Format: XML; `<place>` has `place_id`, `name`, `cre_id`, `location.x` (lon) / `location.y` (lat),
  and `gas_price type="regular|premium|diesel"`.

## Repository layout (`/home/jav/git/GasAppMxV3`)

```
GasAppMxV3/
├── android/                     # Kotlin/Compose app, ported from V2's app/ module
│   └── app/src/main/java/mx/gasappmx/
│       ├── MainActivity.kt
│       ├── auth/                # Firebase Google Sign-In, App Check init
│       ├── data/                # FirestoreStationRepository, FunctionsClient, DTOs
│       ├── domain/              # FuelType, ResultLimit, Haversine, price-tier logic
│       └── ui/                  # MapScreen, BottomSheetList, StationDetail, theme/
├── firebase/
│   ├── functions/src/           # TypeScript Cloud Functions
│   │   ├── cneIngest.ts         # scheduled CNE fetch+parse+cache → Firestore
│   │   ├── directions.ts        # gated Directions/Distance Matrix proxy
│   │   ├── places.ts            # gated Places search proxy
│   │   └── quota.ts             # shared per-user daily quota enforcement
│   ├── firestore.rules          # stations: read-only to authed users; usage: server-only
│   └── firebase.json
├── docs/
│   ├── BUILD_GUIDE.md           # full rebuild-from-scratch instructions (deliverable #1)
│   └── PROGRESS.md              # feature segmentation + live status (deliverable #2)
├── README.md
└── .gitignore                   # excludes secrets.properties, google-services.json, keystores
```

## Reusable assets to carry over (don't rewrite)

From **V2 `~/git/CodexGasAppMx/app`** (port nearly as-is):
- `ui/MarkerPriceTier.kt` — green→red quintile tier logic + ARGB palette.
- `ui/PriceLabelBitmapFactory.kt` — custom price-bubble marker bitmaps.
- `model/GasStation.kt` — `GasStation`, `FuelType` (regular/premium/diesel), `ResultLimit` (5/10/25).
- `ui/theme/Theme.kt` — Material 3 theme baseline.
- `ui/GasViewModel.kt` + `ui/GasApp.kt` — state + screen skeleton (rework layout: big map + sheet).
- `data/ApiStationRepository.kt` — **replace** its HTTP-to-Ktor calls with Firestore reads.
- `backend-service/CneFeedParser.kt` — reference logic for the TypeScript `cneIngest.ts` parser.

From **V1 `~/git/GasAppMx/mobile/App.js`** (logic reference):
- `haversineKm()` distance calc, `priceTier()` bucketing, `openDirections()` deep-link
  (`https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>&travelmode=driving` — or the
  `google.navigation:q=<lat>,<lng>` intent used in V2), 30-min cache TTL pattern.

## Two required documents (deliverables)

1. **`docs/BUILD_GUIDE.md`** — everything needed to rebuild V3 from zero: prerequisites (Android
   Studio, JDK 17, Node, Firebase CLI), Firebase project creation, enabling Auth/App Check/Functions/
   Firestore/Blaze + budget alert, Google Maps API key setup (server-side in Secret Manager + the
   separate restricted SDK key for map display), CNE endpoints + data schema, the architecture diagram
   above, build/sign/release steps, and gotchas (emulator `10.0.2.2`, SHA-1 fingerprints for Google
   Sign-In, App Check debug tokens).

2. **`docs/PLAN.md`** — a copy of **this build plan**, committed to the repo and preserved for
   historic/documentation reasons (kept as the original Phase-1 design record; future revisions append
   rather than overwrite).

3. **`docs/PROGRESS.md`** — the feature segmentation below as a living checklist. **Convention: update
   it in the same commit as any code change** (status, date, notes, "next step"), so an interrupted
   session can resume instantly. Top of file always states *current focus* and *how to run what exists*.

## Phase 1 — feature breakdown (mirrors `PROGRESS.md`)

- **F1 — Scaffold & infra:** create repo + GitHub `GasAppMxV3`, dir layout, `.gitignore`, **commit this
  plan to `docs/PLAN.md`**, Firebase project, enable Auth/Firestore/Functions/App Check, Blaze + $10
  budget alert, write initial docs.
- **F2 — CNE data pipeline:** `cneIngest.ts` scheduled function — fetch both XML feeds, parse+join+
  normalize, write `stations` collection + a `meta/freshness` doc to Firestore. Firestore rules:
  authed read-only.
- **F3 — App shell & big-map UI:** port V2 app into `android/`, Material 3 theme, **near-full-screen
  map** + **draggable bottom-sheet** results list, fuel-type chips (Regular/Premium/Diesel), Top
  5/10/25 chips.
- **F4 — Station data source:** `FirestoreStationRepository` replacing the Ktor repo; render markers
  from cached data; freshness/"updated X min ago" indicator.
- **F5 — Price color tiers:** port `MarkerPriceTier` + `PriceLabelBitmapFactory`; green→red tags on map
  and in the list.
- **F6 — Location & ranking:** FusedLocation permission flow; on-device Haversine ranking; sort by
  price then distance; fuel + Top-N filters.
- **F7 — Auth:** Firebase Google Sign-In screen/gate + App Check (Play Integrity); sign-out; show user.
- **F8 — Paid API gateway:** `places.ts` (address search → recenter map) + `directions.ts` (real ETA
  for a *selected* station, on-demand only to respect quota); `quota.ts` per-user daily caps (20 dir /
  30 places); callable from app via Firebase Functions SDK; 429 handling + friendly "limit reached" UI.
- **F9 — Navigation deep-link:** tapping a station opens the Google Maps app in driving navigation to
  its coordinates.
- **F10 — Polish & resilience:** loading/empty/error states, offline cache of last station list, retry,
  Spanish copy pass.
- **F11 — Release:** generate signing keystore, build signed **AAB + APK**, verify budget alert + App
  Check enforcement on a real device, finalize both docs.

> **Quota guard for F8:** keep list ranking/distance on free Haversine; only call the **Directions**
> proxy when the user *selects* a station (for accurate ETA), and **Places** only on explicit search —
> this keeps usage well under the conservative caps.

## Verification

- **CNE pipeline:** run `cneIngest` locally via Firebase emulator; confirm `stations` collection
  populates with valid lat/lon + 3 fuel prices and a freshness timestamp.
- **App against real data:** run on Android emulator (`10.0.2.2` for emulator-side function calls /
  Firestore emulator), confirm markers render with correct green→red tiers, list sorts by price, fuel +
  Top-N filters work.
- **Auth + App Check:** sign in with Google on a real device (SHA-1 registered); confirm unauthenticated
  calls to the paid functions are rejected.
- **Quota:** script/manually exceed the daily cap; confirm the function returns 429 and the UI shows the
  limit message; confirm map display + deep-link nav still work (they're free).
- **Navigation:** tap a station → Google Maps opens in driving navigation to the right coordinates.
- **Cost guard:** confirm the $10 budget alert is configured and the Maps key is restricted + absent
  from the APK (`unzip -p app-release.apk | strings | grep -i <key>` finds nothing).
- **Release:** install the signed APK on a physical device and run the full happy path end-to-end.

## Notes / risks

- **CNE feed reliability:** the Azure-hosted feed can be slow/down; ingest must cache last-good data and
  the app must tolerate stale data gracefully (carried from V2's fallback behavior).
- **App Check on debug builds** needs a debug token registered, or sign-in/functions will 403 in dev.
- **Google Sign-In** requires the app's SHA-1/SHA-256 fingerprints in the Firebase console for both debug
  and release keystores.
- iOS is explicitly **out of scope** for Phase 1; the architecture (Firebase + deep links) keeps a future
  iOS port open without rework.
