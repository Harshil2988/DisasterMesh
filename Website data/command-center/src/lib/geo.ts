import type { GeoPoint } from './types';

/**
 * Geospatial utilities.
 *
 * Every distance in this console comes from here. Nothing is estimated,
 * rounded up for effect, or filled in when a coordinate is missing — the
 * callers are all written to render "unavailable" instead.
 */

const EARTH_RADIUS_M = 6_371_008.8; // IUGG mean radius

const toRad = (deg: number): number => (deg * Math.PI) / 180;
const toDeg = (rad: number): number => (rad * 180) / Math.PI;

/**
 * Rejects anything that is not a genuinely usable fix.
 *
 * Mirrors `GeoPoint.of` in the Android app, including the 0,0 rule: an exact
 * null island is an uninitialised variable, not a position in the Gulf of
 * Guinea, and it must never reach the map.
 */
export function isUsablePoint(point: GeoPoint | null | undefined): point is GeoPoint {
  if (!point) return false;
  const { latitude: lat, longitude: lon } = point;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return false;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return false;
  if (lat === 0 && lon === 0) return false;
  return true;
}

/** Great-circle distance in metres. Haversine. */
export function distanceMeters(a: GeoPoint, b: GeoPoint): number {
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);

  const h =
    Math.sin(dLat / 2) ** 2 + Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Distance between two points, or null when either is unusable. */
export function safeDistanceMeters(
  a: GeoPoint | null | undefined,
  b: GeoPoint | null | undefined,
): number | null {
  if (!isUsablePoint(a) || !isUsablePoint(b)) return null;
  return distanceMeters(a, b);
}

/** Initial bearing from a to b, in degrees clockwise from true north. */
export function bearingDegrees(a: GeoPoint, b: GeoPoint): number {
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

const COMPASS = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];

export function compassPoint(bearing: number): string {
  return COMPASS[Math.round(bearing / 22.5) % 16];
}

/** Projects a point along a bearing. Used to build hazard-avoidance waypoints. */
export function destinationPoint(origin: GeoPoint, bearing: number, meters: number): GeoPoint {
  const angular = meters / EARTH_RADIUS_M;
  const brg = toRad(bearing);
  const lat1 = toRad(origin.latitude);
  const lon1 = toRad(origin.longitude);

  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(angular) + Math.cos(lat1) * Math.sin(angular) * Math.cos(brg),
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(brg) * Math.sin(angular) * Math.cos(lat1),
      Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2),
    );

  return {
    latitude: toDeg(lat2),
    longitude: ((toDeg(lon2) + 540) % 360) - 180,
  };
}

/** Arithmetic centre of a set of points. Adequate at city scale. */
export function centroid(points: GeoPoint[]): GeoPoint | null {
  const usable = points.filter(isUsablePoint);
  if (usable.length === 0) return null;
  const sum = usable.reduce(
    (acc, p) => ({ lat: acc.lat + p.latitude, lon: acc.lon + p.longitude }),
    { lat: 0, lon: 0 },
  );
  return { latitude: sum.lat / usable.length, longitude: sum.lon / usable.length };
}

export interface Bounds {
  south: number;
  west: number;
  north: number;
  east: number;
}

export function boundsOf(points: GeoPoint[]): Bounds | null {
  const usable = points.filter(isUsablePoint);
  if (usable.length === 0) return null;
  return usable.reduce<Bounds>(
    (b, p) => ({
      south: Math.min(b.south, p.latitude),
      west: Math.min(b.west, p.longitude),
      north: Math.max(b.north, p.latitude),
      east: Math.max(b.east, p.longitude),
    }),
    { south: 90, west: 180, north: -90, east: -180 },
  );
}

/**
 * Shortest distance from point p to segment ab, in metres.
 *
 * Works in a local equirectangular projection about the segment. Over the few
 * kilometres a rescue corridor spans, the error is far below the precision of
 * a phone GPS fix, and it keeps the hazard test cheap enough to run inside a
 * routing loop.
 */
export function pointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): number {
  const latRef = toRad((a.latitude + b.latitude) / 2);
  const mx = EARTH_RADIUS_M * Math.cos(latRef);

  const project = (pt: GeoPoint): [number, number] => [
    toRad(pt.longitude) * mx,
    toRad(pt.latitude) * EARTH_RADIUS_M,
  ];

  const [px, py] = project(p);
  const [ax, ay] = project(a);
  const [bx, by] = project(b);

  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;

  if (lenSq === 0) return Math.hypot(px - ax, py - ay);

  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

/** True when segment ab passes within `radius` metres of `center`. */
export function segmentIntersectsCircle(
  a: GeoPoint,
  b: GeoPoint,
  center: GeoPoint,
  radiusMeters: number,
): boolean {
  return pointToSegmentMeters(center, a, b) < radiusMeters;
}

// ---------------------------------------------------------------------------
// Presentation
// ---------------------------------------------------------------------------

/**
 * The single distance format used across the console.
 *   under 1 km -> "650 m" (nearest 10 m)
 *   1 km and up -> "2.4 km"
 */
export function formatDistance(meters: number | null | undefined): string {
  if (meters === null || meters === undefined || !Number.isFinite(meters)) return '—';
  if (meters < 1000) return `${Math.round(meters / 10) * 10} m`;
  if (meters < 10_000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters / 1000)} km`;
}

/**
 * Travel-time estimate.
 *
 * Deliberately a single, stated assumption rather than a traffic model: this
 * console has no road network and no live traffic, so it applies one ground
 * speed and labels the result an estimate everywhere it appears.
 */
export const GROUND_SPEED_KMH = 26;

export function etaSeconds(meters: number | null, speedKmh = GROUND_SPEED_KMH): number | null {
  if (meters === null || !Number.isFinite(meters) || speedKmh <= 0) return null;
  return (meters / 1000 / speedKmh) * 3600;
}

export function formatEta(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return '—';
  const mins = Math.round(seconds / 60);
  if (mins < 1) return '< 1 min';
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60);
  return `${h} h ${mins % 60} min`;
}

/** Coordinate pair at the precision a handset GPS actually justifies. */
export function formatCoords(point: GeoPoint | null | undefined): string {
  if (!isUsablePoint(point)) return 'No position';
  return `${point.latitude.toFixed(5)}°, ${point.longitude.toFixed(5)}°`;
}
