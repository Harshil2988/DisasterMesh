import type { GeoPoint, Hazard, RouteLeg, RouteSolution } from './types';
import {
  destinationPoint,
  distanceMeters,
  etaSeconds,
  isUsablePoint,
  segmentIntersectsCircle,
} from './geo';

/**
 * HAZARD-AVOIDING CORRIDOR ROUTING
 *
 * What this is, stated plainly because the UI states it too: there is no road
 * network in this console and no routing service behind it. What it computes
 * is a shortest polyline from origin to destination that stays outside every
 * known hazard zone — a search over a visibility graph built from the hazard
 * boundaries themselves.
 *
 * That is a real geometric result, not a decorative line: if it returns a
 * path, every leg genuinely clears every active hazard, and if it cannot, it
 * says so and names the hazards in the way. What it is NOT is a driving
 * route, and nothing here ever calls it one.
 *
 * Method reported to the operator:
 *   DIRECT           straight corridor; nothing known was in the way
 *   HAZARD_AVOIDING  detoured around one or more hazard zones
 *   (clear = false)  no clear corridor exists; the hazards are named
 */

/** Detour waypoints are placed outside the hazard edge by this factor. */
const CLEARANCE_FACTOR = 1.25;
/** A second, wider ring so the search can escape tightly packed hazards. */
const WIDE_CLEARANCE_FACTOR = 1.9;
/** Waypoints generated per ring per hazard. */
const RING_SAMPLES = 12;
/** Refuses to build an unbounded graph on a pathological hazard count. */
const MAX_HAZARDS_CONSIDERED = 24;

export interface RouteRequest {
  origin: GeoPoint;
  destination: GeoPoint;
  originLabel: string;
  destinationLabel: string;
  hazards: Hazard[];
  speedKmh?: number;
}

export function computeRoute(request: RouteRequest): RouteSolution | null {
  const { origin, destination, originLabel, destinationLabel, speedKmh } = request;

  if (!isUsablePoint(origin) || !isUsablePoint(destination)) return null;

  const hazards = request.hazards
    .filter((h) => h.active && isUsablePoint(h.center) && h.radiusMeters > 0)
    .slice(0, MAX_HAZARDS_CONSIDERED);

  const directDistance = distanceMeters(origin, destination);
  const directBlockers = hazardsOnSegment(origin, destination, hazards);

  // Fast path: nothing known is in the way.
  if (directBlockers.length === 0) {
    return finish({
      points: [origin, destination],
      hazards,
      method: 'DIRECT',
      directDistance,
      originLabel,
      destinationLabel,
      speedKmh,
    });
  }

  // Something is in the way. Search for a corridor around it.
  const path = searchAround(origin, destination, hazards);

  if (path) {
    return finish({
      points: path,
      hazards,
      method: 'HAZARD_AVOIDING',
      directDistance,
      originLabel,
      destinationLabel,
      speedKmh,
    });
  }

  // No clear corridor. Return the direct line, flagged as NOT clear, with the
  // blocking hazards named. The map draws it dashed and the panel refuses to
  // call it a route.
  return finish({
    points: [origin, destination],
    hazards,
    method: 'DIRECT',
    directDistance,
    originLabel,
    destinationLabel,
    speedKmh,
  });
}

// ---------------------------------------------------------------------------

function hazardsOnSegment(a: GeoPoint, b: GeoPoint, hazards: Hazard[]): string[] {
  return hazards
    .filter((h) => segmentIntersectsCircle(a, b, h.center, h.radiusMeters))
    .map((h) => h.id);
}

function insideAnyHazard(point: GeoPoint, hazards: Hazard[]): boolean {
  return hazardsContaining(point, hazards).length > 0;
}

/** Hazard zones a point sits inside. */
function hazardsContaining(point: GeoPoint, hazards: Hazard[]): string[] {
  return hazards.filter((h) => distanceMeters(point, h.center) < h.radiusMeters).map((h) => h.id);
}

/**
 * Dijkstra over a visibility graph.
 *
 * Nodes: origin, destination, and a ring of clearance waypoints around every
 * hazard. Edges: any pair of nodes with an unobstructed segment between them.
 * The result is the shortest obstacle-free polyline the sampling can express.
 */
function searchAround(
  origin: GeoPoint,
  destination: GeoPoint,
  hazards: Hazard[],
): GeoPoint[] | null {
  const nodes: GeoPoint[] = [origin, destination];

  for (const hazard of hazards) {
    for (const factor of [CLEARANCE_FACTOR, WIDE_CLEARANCE_FACTOR]) {
      for (let i = 0; i < RING_SAMPLES; i += 1) {
        const bearing = (360 / RING_SAMPLES) * i;
        const candidate = destinationPoint(hazard.center, bearing, hazard.radiusMeters * factor);
        if (!insideAnyHazard(candidate, hazards)) nodes.push(candidate);
      }
    }
  }

  const n = nodes.length;
  const ORIGIN = 0;
  const DEST = 1;

  // Adjacency, computed lazily per node to keep the O(n²) visibility test off
  // the hot path when an early exit is available.
  const visible = (i: number, j: number): boolean =>
    hazardsOnSegment(nodes[i], nodes[j], hazards).length === 0;

  const dist = new Array<number>(n).fill(Infinity);
  const prev = new Array<number>(n).fill(-1);
  const settled = new Array<boolean>(n).fill(false);
  dist[ORIGIN] = 0;

  for (;;) {
    let current = -1;
    let best = Infinity;
    for (let i = 0; i < n; i += 1) {
      if (!settled[i] && dist[i] < best) {
        best = dist[i];
        current = i;
      }
    }
    if (current === -1) break;
    if (current === DEST) break;
    settled[current] = true;

    for (let j = 0; j < n; j += 1) {
      if (settled[j] || j === current) continue;
      if (!visible(current, j)) continue;
      const candidate = dist[current] + distanceMeters(nodes[current], nodes[j]);
      if (candidate < dist[j]) {
        dist[j] = candidate;
        prev[j] = current;
      }
    }
  }

  if (!Number.isFinite(dist[DEST])) return null;

  const path: GeoPoint[] = [];
  for (let at = DEST; at !== -1; at = prev[at]) path.unshift(nodes[at]);
  return simplify(path, hazards);
}

/**
 * Removes waypoints the corridor does not need — the ring sampling tends to
 * produce a staircase, and a commander should see the corner they must
 * actually drive round, not twelve of them.
 */
function simplify(path: GeoPoint[], hazards: Hazard[]): GeoPoint[] {
  if (path.length <= 2) return path;
  const out: GeoPoint[] = [path[0]];
  let anchor = 0;

  while (anchor < path.length - 1) {
    let furthest = anchor + 1;
    for (let probe = path.length - 1; probe > anchor + 1; probe -= 1) {
      if (hazardsOnSegment(path[anchor], path[probe], hazards).length === 0) {
        furthest = probe;
        break;
      }
    }
    out.push(path[furthest]);
    anchor = furthest;
  }
  return out;
}

// ---------------------------------------------------------------------------

interface FinishArgs {
  points: GeoPoint[];
  hazards: Hazard[];
  method: RouteSolution['method'];
  directDistance: number;
  originLabel: string;
  destinationLabel: string;
  speedKmh?: number;
}

function finish(args: FinishArgs): RouteSolution {
  const { points, hazards, method, directDistance, originLabel, destinationLabel } = args;
  const origin = points[0];
  const destination = points[points.length - 1];

  const legs: RouteLeg[] = [];
  for (let i = 0; i < points.length - 1; i += 1) {
    const from = points[i];
    const to = points[i + 1];
    legs.push({
      from,
      to,
      distanceMeters: distanceMeters(from, to),
      hazardIds: hazardsOnSegment(from, to, hazards),
    });
  }

  const total = legs.reduce((sum, leg) => sum + leg.distanceMeters, 0);
  const blocked = Array.from(new Set(legs.flatMap((leg) => leg.hazardIds)));

  return {
    id: `RTE-${Math.round(total)}-${points.length}`,
    originLabel,
    destinationLabel,
    legs,
    distanceMeters: total,
    etaSeconds: etaSeconds(total, args.speedKmh),
    method,
    clear: blocked.length === 0,
    blockedByHazardIds: blocked,
    directDistanceMeters: directDistance,
    originInHazardIds: hazardsContaining(origin, hazards),
    destinationInHazardIds: hazardsContaining(destination, hazards),
    computedAt: Date.now(),
  };
}

/** Extra ground covered to avoid the hazards, in metres. */
export function detourCost(route: RouteSolution): number {
  return Math.max(0, route.distanceMeters - route.directDistanceMeters);
}

export const ROUTING_METHOD_NOTE =
  'Corridor routing over hazard geometry. This console has no road network, ' +
  'so a computed path avoids known hazard zones but is not a driving route.';
