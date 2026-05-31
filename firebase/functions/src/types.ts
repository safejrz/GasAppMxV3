/** Normalized gas station, as cached in stations/latest.json and consumed by the app. */
export interface Station {
  id: string; // CNE place_id
  name: string;
  creId: string;
  lat: number;
  lon: number;
  regular: number | null;
  premium: number | null;
  diesel: number | null;
}

/** The full cached dataset blob written to Cloud Storage. */
export interface StationDataset {
  updatedAt: string; // ISO timestamp of this ingest
  source: string; // which feed it came from
  count: number;
  stations: Station[];
}

export type FuelType = "regular" | "premium" | "diesel";
