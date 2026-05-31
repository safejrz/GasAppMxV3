import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { enforceQuota } from "./quota";

const MAPS_SERVER_KEY = defineSecret("MAPS_SERVER_KEY");

interface DirectionsRequest {
  originLat: number;
  originLon: number;
  destLat: number;
  destLon: number;
}

function requireCoord(value: unknown, label: string): number {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    throw new HttpsError("invalid-argument", `${label} must be a finite number.`);
  }
  return n;
}

/**
 * Gated Directions proxy for real driving distance/ETA to a *selected* station only
 * (the list ranking uses free on-device Haversine). Requires auth + App Check and
 * consumes the daily Directions quota. Returns {distanceMeters, durationSeconds, durationText}.
 */
export const directions = onCall(
  {
    enforceAppCheck: true,
    secrets: [MAPS_SERVER_KEY],
    region: "us-central1",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const d = request.data as DirectionsRequest;
    const originLat = requireCoord(d?.originLat, "originLat");
    const originLon = requireCoord(d?.originLon, "originLon");
    const destLat = requireCoord(d?.destLat, "destLat");
    const destLon = requireCoord(d?.destLon, "destLon");

    await enforceQuota(request.auth.uid, "directions");

    const url = new URL("https://maps.googleapis.com/maps/api/directions/json");
    url.searchParams.set("origin", `${originLat},${originLon}`);
    url.searchParams.set("destination", `${destLat},${destLon}`);
    url.searchParams.set("mode", "driving");
    url.searchParams.set("language", "es-419");
    url.searchParams.set("key", MAPS_SERVER_KEY.value());

    const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
    if (!res.ok) {
      throw new HttpsError("internal", `Directions API error: ${res.status}`);
    }
    const body = (await res.json()) as any;
    const leg = body.routes?.[0]?.legs?.[0];
    if (!leg) {
      throw new HttpsError("not-found", "No route found.");
    }

    return {
      distanceMeters: leg.distance?.value ?? null,
      durationSeconds: leg.duration?.value ?? null,
      durationText: leg.duration?.text ?? null,
    };
  }
);
