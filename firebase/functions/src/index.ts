import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

// Scheduled CNE feed ingest → cached dataset in Cloud Storage + freshness in Firestore.
export { cneIngest } from "./cneIngest";

// Gated, quota-limited proxies to paid Google Maps APIs (F8). Re-enable these once the
// Secret Manager API is on and MAPS_SERVER_KEY is set:
//   firebase functions:secrets:set MAPS_SERVER_KEY
// They are commented out so `cneIngest` (F2) can deploy before the Maps key exists, since
// the CLI evaluates defineSecret() across the whole codebase during source analysis.
// export { placesSearch } from "./places";
// export { directions } from "./directions";
