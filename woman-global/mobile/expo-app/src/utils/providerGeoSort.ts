import type { Provider } from '@/types/models';

const EARTH_RADIUS_M = 6_371_000;

export function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rLat1 = (lat1 * Math.PI) / 180;
  const rLat2 = (lat2 * Math.PI) / 180;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_RADIUS_M * c;
}

/** Stable sort: closest first when seeker coords exist; providers without coords follow (Kotlin `ProviderGeoSort`). */
export function sortProvidersByDistance(
  providers: Provider[],
  seekerLat: number | null | undefined,
  seekerLng: number | null | undefined,
): Provider[] {
  if (providers.length === 0) return providers;
  if (seekerLat == null || seekerLng == null || !Number.isFinite(seekerLat) || !Number.isFinite(seekerLng)) {
    return [...providers];
  }

  const withCoords: { p: Provider; m: number }[] = [];
  const without: Provider[] = [];
  for (const p of providers) {
    const plat = p.latitude;
    const plng = p.longitude;
    if (plat != null && plng != null && Number.isFinite(plat) && Number.isFinite(plng)) {
      withCoords.push({ p, m: haversineMeters(seekerLat, seekerLng, plat, plng) });
    } else {
      without.push(p);
    }
  }
  withCoords.sort((a, b) => a.m - b.m);
  return [...withCoords.map((x) => x.p), ...without];
}

export function providerRefForSort(p: Provider): string {
  return (p.user_name ?? '').trim() || String(p.id);
}
