# GasAppMxV3 — Progress Tracker

> **Living document.** Update this in the **same commit** as any code change: flip a checkbox, set the
> date, jot a note, and rewrite "Current focus" + "Next step". If a session is interrupted, start here.

---

## ⏱️ Current focus
**F1 — Scaffold & infra.** Repo, docs, `.gitignore`, and the Firebase Functions skeleton (with real CNE
ingest logic) are in place locally and being pushed to GitHub. Firebase *cloud* setup (project, Blaze,
API enablement) is still pending and requires interactive Google login.

## ▶️ Next step
1. Install Firebase CLI (`npm i -g firebase-tools`) and `firebase login` (interactive — user must run).
2. Create the Firebase project, upgrade to **Blaze**, set the **$10 budget alert**.
3. Enable Auth (Google), Firestore, Cloud Functions, App Check.
4. Deploy `cneIngest` and verify the `stations` collection populates (F2).

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
| F3 | App shell & big-map UI (full-screen map + bottom sheet) | ⬜ | — | Port V2 `app/` into `android/`, rework layout |
| F4 | Station data source (Firestore repo, freshness) | ⬜ | — | Replace V2's Ktor repo with Firestore reads |
| F5 | Price color tiers (green→red markers) | ⬜ | — | Port `MarkerPriceTier` + `PriceLabelBitmapFactory` |
| F6 | Location & ranking (Haversine, fuel + Top-N filters) | ⬜ | — | FusedLocation + on-device sort |
| F7 | Auth (Google Sign-In + App Check) | ⬜ | — | Needs SHA-1 in Firebase console |
| F8 | Paid API gateway (Places + Directions + quota) | ⬜ | — | Conservative caps: 20 dir / 30 places per user/day |
| F9 | Navigation deep-link (open Google Maps app) | ⬜ | — | `google.navigation:q=<lat>,<lng>` |
| F10 | Polish & resilience (states, offline cache, copy) | ⬜ | — | — |
| F11 | Release (keystore, signed AAB/APK, verify cost guard) | ⬜ | — | Phase-1 finish line |

---

## Decisions & deviations log
_Record anything that diverges from `PLAN.md`, with a date._

- **2026-05-30** — Project created. Stack/architecture/quotas locked per `PLAN.md`. Firebase CLI not yet
  installed on the dev machine; cloud provisioning deferred to an interactive session.
- **2026-05-30** — **Deviation from PLAN.md data model:** the bulk station dataset is cached as a single
  gzipped JSON blob in **Cloud Storage** (`stations/latest.json`), not as a `stations/*` Firestore
  collection. Reason: ~13.7k stations × 48 ingests/day would far exceed Firestore's free write tier and
  add cost. The app downloads the blob (authed read) and runs Haversine locally — same UX, near-zero
  write cost. Firestore still holds `meta/freshness` + per-user `usage`. Functions verified to compile;
  parser verified against the live CNE feed.

## Manual / interactive steps owed by the user
_Things Claude cannot do non-interactively — track them here so they aren't forgotten._

- [ ] `firebase login` (Google account) and pick/create the Firebase project.
- [ ] Upgrade Firebase project to **Blaze** + create **$10 budget alert** in Google Cloud Billing.
- [ ] Create two Google Maps API keys: (a) restricted **Android SDK** key for map display, (b)
      server-side key stored in **Secret Manager** for the Places/Directions functions.
- [ ] Register debug + release **SHA-1/SHA-256** fingerprints in Firebase (for Google Sign-In).
- [ ] Place `google-services.json` in `android/app/` (gitignored).
