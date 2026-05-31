import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/**
 * Conservative per-user daily caps (PLAN.md). Tunable here without an app release.
 */
export const DAILY_CAPS = {
  directions: 20,
  places: 30,
} as const;

export type QuotaKind = keyof typeof DAILY_CAPS;

function todayKey(): string {
  // UTC day bucket, e.g. "2026-05-30".
  return new Date().toISOString().slice(0, 10);
}

/**
 * Atomically check + increment a user's daily counter for `kind`. Throws a
 * resource-exhausted HttpsError (surfaced to the app as a 429-style "limit reached")
 * when the cap is met. Counter doc: usage/{uid}/days/{yyyy-mm-dd}.
 */
export async function enforceQuota(uid: string, kind: QuotaKind): Promise<void> {
  const cap = DAILY_CAPS[kind];
  const ref = getFirestore().doc(`usage/${uid}/days/${todayKey()}`);

  await getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const used = (snap.exists ? (snap.get(kind) as number | undefined) : 0) ?? 0;

    if (used >= cap) {
      throw new HttpsError(
        "resource-exhausted",
        `Daily ${kind} limit reached (${cap}). Try again tomorrow.`
      );
    }

    tx.set(
      ref,
      { [kind]: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() },
      { merge: true }
    );
  });
}
