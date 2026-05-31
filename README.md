# GasAppMxV3 — Gasolina MX

Find the **cheapest nearby gas stations in Mexico** on a map, with prices color-coded
**green (cheapest) → red (most expensive)**. Tapping a station opens turn-by-turn navigation in the
Google Maps app. Built on Mexico's public **CNE/CRE** fuel-price feed.

This is the third iteration. It pairs the polished native UI of the previous attempt with a bigger map,
adds Google sign-in + per-user rate limiting to keep cloud costs affordable, and uses **Firebase
(serverless)** instead of a self-hosted backend.

## Stack

| Layer | Tech |
|---|---|
| App | Native **Kotlin + Jetpack Compose** (Material 3), Android-only for Phase 1 |
| Maps | Google Maps SDK (display) + deep-link to Google Maps app (navigation) |
| Cloud | **Firebase**: Auth (Google Sign-In), App Check, Cloud Functions (TS), Firestore, Cloud Scheduler |
| Data | CNE/CRE public XML price feed, ingested + cached server-side every ~30 min |

## Architecture (one-liner)

The app never holds the paid Maps key or calls paid APIs directly. Map display, deep-link navigation,
and straight-line (Haversine) ranking are free and on-device. **Places** (search) and **Directions**
(real ETA) go through Cloud Functions that verify auth + App Check and enforce per-user daily quotas.
A scheduled function ingests the CNE feed once globally and caches it in Firestore for all clients.

See **[docs/PLAN.md](docs/PLAN.md)** for the full design, **[docs/BUILD_GUIDE.md](docs/BUILD_GUIDE.md)**
to build from scratch, and **[docs/PROGRESS.md](docs/PROGRESS.md)** for current status / where to resume.

## Repo layout

```
android/    Kotlin/Compose app
firebase/   Cloud Functions (TS), Firestore rules, config
docs/       PLAN.md · BUILD_GUIDE.md · PROGRESS.md
```

## Status

Phase 1 in progress — see **docs/PROGRESS.md**. Target: a functional, production-ready **signed
Android APK** with real CNE data and working auth + quotas.
