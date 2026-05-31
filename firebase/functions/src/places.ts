import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { enforceQuota } from "./quota";

const MAPS_SERVER_KEY = defineSecret("MAPS_SERVER_KEY");

interface PlacesRequest {
  query: string;
  // Optional bias center so results are near the user.
  lat?: number;
  lon?: number;
}

/**
 * Gated Places "Text Search" proxy. Requires an authenticated user + valid App Check token,
 * enforces the daily Places quota, then calls Google with the server-side key (never shipped
 * in the app). Returns a trimmed list of {name, address, lat, lon}.
 */
export const placesSearch = onCall(
  {
    enforceAppCheck: true,
    secrets: [MAPS_SERVER_KEY],
    region: "us-central1",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const data = request.data as PlacesRequest;
    const query = (data?.query ?? "").trim();
    if (!query) {
      throw new HttpsError("invalid-argument", "query is required.");
    }

    await enforceQuota(request.auth.uid, "places");

    const url = new URL("https://maps.googleapis.com/maps/api/place/textsearch/json");
    url.searchParams.set("query", query);
    url.searchParams.set("region", "mx");
    url.searchParams.set("language", "es-419");
    if (Number.isFinite(data?.lat) && Number.isFinite(data?.lon)) {
      url.searchParams.set("location", `${data.lat},${data.lon}`);
      url.searchParams.set("radius", "20000");
    }
    url.searchParams.set("key", MAPS_SERVER_KEY.value());

    const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
    if (!res.ok) {
      throw new HttpsError("internal", `Places API error: ${res.status}`);
    }
    const body = (await res.json()) as any;

    const results = (body.results ?? []).slice(0, 8).map((r: any) => ({
      name: r.name,
      address: r.formatted_address,
      lat: r.geometry?.location?.lat,
      lon: r.geometry?.location?.lng,
    }));

    return { results };
  }
);
