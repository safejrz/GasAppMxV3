import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { getStorage } from "firebase-admin/storage";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { gzipSync } from "zlib";
import { buildStations } from "./cneParser";
import { StationDataset } from "./types";

const PLACES_URL =
  process.env.CNE_PLACES_URL ??
  "https://publicacionexterna.azurewebsites.net/publicaciones/places";
const PRICES_URL =
  process.env.CNE_PRICES_URL ??
  "https://publicacionexterna.azurewebsites.net/publicaciones/prices";

// Gzipped JSON stored as opaque bytes (NO contentEncoding metadata) so the client always
// gunzips deterministically and GCS never applies decompressive transcoding.
const DATASET_PATH = "stations/latest.json.gz";

async function fetchText(url: string): Promise<string> {
  const res = await fetch(url, { signal: AbortSignal.timeout(60_000) });
  if (!res.ok) throw new Error(`Fetch ${url} failed: ${res.status}`);
  return res.text();
}

/**
 * Scheduled CNE ingest. Fetches both feeds once globally, normalizes, and writes a single
 * gzipped JSON blob to Cloud Storage (one write per cycle — avoids per-station Firestore
 * write costs at ~13k stations) plus a small Firestore freshness doc. Keeps last-good data:
 * on any failure it logs and leaves the existing blob untouched.
 */
export const cneIngest = onSchedule(
  {
    schedule: "every 30 minutes",
    timeoutSeconds: 300,
    memory: "512MiB",
    region: "us-central1",
  },
  async () => {
    const [placesXml, pricesXml] = await Promise.all([
      fetchText(PLACES_URL),
      fetchText(PRICES_URL),
    ]);

    const stations = buildStations(placesXml, pricesXml);
    if (stations.length === 0) {
      throw new Error("CNE ingest produced 0 stations — keeping last-good dataset.");
    }

    const dataset: StationDataset = {
      updatedAt: new Date().toISOString(),
      source: "cne-publicacionexterna",
      count: stations.length,
      stations,
    };

    const gzipped = gzipSync(Buffer.from(JSON.stringify(dataset)));
    await getStorage()
      .bucket()
      .file(DATASET_PATH)
      .save(gzipped, {
        contentType: "application/gzip",
        metadata: { cacheControl: "no-cache" },
      });

    // Freshness metadata is best-effort: don't fail the ingest if Firestore isn't set up yet.
    try {
      await getFirestore().doc("meta/freshness").set({
        lastIngestAt: FieldValue.serverTimestamp(),
        lastIngestIso: dataset.updatedAt,
        stationCount: dataset.count,
        source: dataset.source,
        datasetPath: DATASET_PATH,
      });
    } catch (e) {
      logger.warn("Skipped meta/freshness write (Firestore not ready?)", e);
    }

    logger.info(`CNE ingest ok: ${stations.length} stations cached to ${DATASET_PATH}`);
  }
);
