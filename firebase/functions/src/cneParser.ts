import { XMLParser } from "fast-xml-parser";
import { Station } from "./types";

/**
 * CNE/CRE feed parsing. Ported from the proven V1 logic
 * (~/git/GasAppMx/mobile/App.js parsePlaces/parsePrices). Both the `places` and `prices`
 * feeds share a <places><place>...</place></places> shape:
 *   - places feed: place_id, name, cre_id, location.x (lon) / location.y (lat)
 *   - prices feed: place_id + one or more <gas_price type="regular|premium|diesel">value</gas_price>
 */

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "",
  parseTagValue: true,
  trimValues: true,
});

function toArray<T>(value: T | T[] | undefined | null): T[] {
  if (value === undefined || value === null) return [];
  return Array.isArray(value) ? value : [value];
}

interface PlaceMeta {
  id: string;
  name: string;
  creId: string;
  lat: number;
  lon: number;
}

interface PriceBucket {
  regular: number | null;
  premium: number | null;
  diesel: number | null;
}

export function parsePlaces(xml: string): Map<string, PlaceMeta> {
  const doc = parser.parse(xml);
  const places = toArray<any>(doc?.places?.place);
  const map = new Map<string, PlaceMeta>();

  for (const place of places) {
    const id = String(place?.place_id ?? "").trim();
    if (!id || map.has(id)) continue;

    const lat = Number(place?.location?.y ?? place?.y ?? NaN);
    const lon = Number(place?.location?.x ?? place?.x ?? NaN);

    map.set(id, {
      id,
      name: String(place?.name ?? "Estación sin nombre").trim(),
      creId: String(place?.cre_id ?? "").trim(),
      lat,
      lon,
    });
  }

  return map;
}

export function parsePrices(xml: string): Map<string, PriceBucket> {
  const doc = parser.parse(xml);
  const places = toArray<any>(doc?.places?.place);
  const map = new Map<string, PriceBucket>();

  for (const place of places) {
    const id = String(place?.place_id ?? "").trim();
    if (!id) continue;

    if (!map.has(id)) {
      map.set(id, { regular: null, premium: null, diesel: null });
    }
    const bucket = map.get(id)!;

    for (const gasPrice of toArray<any>(place?.gas_price)) {
      const type = String(gasPrice?.type ?? "").toLowerCase();
      const rawValue =
        typeof gasPrice === "object" && gasPrice !== null
          ? gasPrice["#text"] ?? gasPrice._ ?? null
          : gasPrice;
      const value = Number(rawValue);

      if (
        (type === "regular" || type === "premium" || type === "diesel") &&
        Number.isFinite(value)
      ) {
        bucket[type] = value;
      }
    }
  }

  return map;
}

/** Join place metadata with prices into normalized stations. Drops entries lacking
 *  valid coordinates or with no price for any fuel type. */
export function buildStations(placesXml: string, pricesXml: string): Station[] {
  const places = parsePlaces(placesXml);
  const prices = parsePrices(pricesXml);
  const stations: Station[] = [];

  for (const [id, meta] of places) {
    if (!Number.isFinite(meta.lat) || !Number.isFinite(meta.lon)) continue;
    const p = prices.get(id) ?? { regular: null, premium: null, diesel: null };
    if (p.regular === null && p.premium === null && p.diesel === null) continue;

    stations.push({
      id,
      name: meta.name,
      creId: meta.creId,
      lat: meta.lat,
      lon: meta.lon,
      regular: p.regular,
      premium: p.premium,
      diesel: p.diesel,
    });
  }

  return stations;
}
