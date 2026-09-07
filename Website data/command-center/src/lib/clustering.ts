import type { Category, EmergencyReport, GeoPoint, IncidentCluster } from './types';
import { centroid, distanceMeters, isUsablePoint } from './geo';

/**
 * Geographic clustering of incident reports.
 *
 * Single-link agglomerative clustering over great-circle distance: two
 * reports join the same cluster when they sit within `linkageMeters` of each
 * other, and clusters merge transitively. This is the behaviour a commander
 * expects from "people in the same collapsed block" — a chain of nearby
 * reports is one incident area, even when its two ends are further apart than
 * the threshold.
 *
 * Reports with no coordinates are excluded outright rather than dropped into
 * an arbitrary cluster. They remain in the queue; they just cannot be placed.
 */

export const DEFAULT_LINKAGE_METERS = 400;
export const MIN_CLUSTER_SIZE = 3;

interface Placed {
  report: EmergencyReport;
  point: GeoPoint;
}

export function buildClusters(
  reports: EmergencyReport[],
  options: {
    linkageMeters?: number;
    minSize?: number;
    sectorOf?: (point: GeoPoint) => string | null;
  } = {},
): IncidentCluster[] {
  const linkage = options.linkageMeters ?? DEFAULT_LINKAGE_METERS;
  const minSize = options.minSize ?? MIN_CLUSTER_SIZE;

  const placed: Placed[] = reports
    .filter((r) => r.status !== 'INVALID' && isUsablePoint(r.location))
    .map((r) => ({ report: r, point: r.location as GeoPoint }));

  if (placed.length === 0) return [];

  // Union-find over the linkage graph.
  const parent = placed.map((_, i) => i);
  const find = (i: number): number => {
    let root = i;
    while (parent[root] !== root) root = parent[root];
    while (parent[i] !== root) {
      const next = parent[i];
      parent[i] = root;
      i = next;
    }
    return root;
  };
  const union = (a: number, b: number): void => {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent[rb] = ra;
  };

  for (let i = 0; i < placed.length; i += 1) {
    for (let j = i + 1; j < placed.length; j += 1) {
      if (distanceMeters(placed[i].point, placed[j].point) <= linkage) union(i, j);
    }
  }

  const groups = new Map<number, Placed[]>();
  placed.forEach((entry, i) => {
    const root = find(i);
    const bucket = groups.get(root);
    if (bucket) bucket.push(entry);
    else groups.set(root, [entry]);
  });

  const clusters: IncidentCluster[] = [];

  for (const [root, members] of groups) {
    if (members.length < minSize) continue;

    const center = centroid(members.map((m) => m.point));
    if (!center) continue;

    const radiusMeters = members.reduce(
      (max, m) => Math.max(max, distanceMeters(center, m.point)),
      0,
    );

    const countsByCategory: Record<Category, number> = {
      CRITICAL: 0,
      MEDICAL: 0,
      WARNING: 0,
      SUPPLY: 0,
      SAFE: 0,
    };
    let peopleAffected = 0;

    for (const m of members) {
      countsByCategory[m.report.category] += 1;
      // A report with no headcount still represents at least one person.
      peopleAffected += m.report.peopleAffected ?? 1;
    }

    clusters.push({
      id: `CLU-${String(root).padStart(3, '0')}`,
      center,
      radiusMeters: Math.round(radiusMeters),
      emergencyIds: members.map((m) => m.report.id),
      peopleAffected,
      countsByCategory,
      topScore: 0, // filled in by the caller, which owns the scoring clock
      sector: options.sectorOf ? options.sectorOf(center) : null,
    });
  }

  // Densest and most severe first.
  return clusters.sort(
    (a, b) =>
      b.countsByCategory.CRITICAL - a.countsByCategory.CRITICAL ||
      b.peopleAffected - a.peopleAffected,
  );
}

/** Plain-English headline, e.g. "13 people potentially affected within 400 m". */
export function describeCluster(cluster: IncidentCluster): string {
  const radius = Math.max(50, Math.round(cluster.radiusMeters / 50) * 50);
  return `${cluster.peopleAffected} ${
    cluster.peopleAffected === 1 ? 'person' : 'people'
  } potentially affected within ${radius} m`;
}
