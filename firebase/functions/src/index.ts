import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

// Scheduled CNE feed ingest → cached dataset in Cloud Storage + freshness in Firestore.
export { cneIngest } from "./cneIngest";

// Gated, quota-limited proxies to paid Google Maps APIs.
export { placesSearch } from "./places";
export { directions } from "./directions";
