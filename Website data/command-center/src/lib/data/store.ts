import type {
  Category,
  EmergencyReport,
  OperationalDataset,
  SyncEvent,
  TimelineEvent,
} from '../types';
import { sectorFor } from './seed';

/**
 * Server-side store for LIVE mode.
 *
 * Deliberately in-memory and deliberately empty at boot. This process holds
 * exactly what rescuer gateways have POSTed to it during its lifetime and
 * nothing else — no seeding, no fixtures, no "sample" rows. Restart the
 * server and the console correctly reports that it is waiting for a gateway.
 *
 * A production deployment replaces this module with a database. Every caller
 * already goes through `liveStore`, so nothing above it needs to change.
 */

interface LiveStore {
  emergencies: Map<string, EmergencyReport>;
  timeline: TimelineEvent[];
  syncEvents: SyncEvent[];
  gateways: Map<string, number>;
  startedAt: number;
  lastSyncedAt: number | null;
}

// Survives Next's dev-mode module reloads, which would otherwise silently
// discard everything a rescuer had already uploaded.
const globalRef = globalThis as unknown as { __dmLiveStore?: LiveStore };

export const liveStore: LiveStore =
  globalRef.__dmLiveStore ??
  (globalRef.__dmLiveStore = {
    emergencies: new Map(),
    timeline: [],
    syncEvents: [],
    gateways: new Map(),
    startedAt: Date.now(),
    lastSyncedAt: null,
  });

/**
 * The wire format the Android app's `InternetUplink` actually sends.
 * Field names are taken verbatim from `uplink/ExternalUplink.kt` so a handset
 * pointed at this endpoint works with no adapter in between.
 */
export interface UplinkPayload {
  reportId?: unknown;
  senderNodeId?: unknown;
  category?: unknown;
  priority?: unknown;
  description?: unknown;
  timestamp?: unknown;
  latitude?: unknown;
  longitude?: unknown;
  peopleAffected?: unknown;
  hops?: unknown;
  /** Not sent by the current Android build; accepted when present. */
  gatewayId?: unknown;
}

const VALID_CATEGORIES: Category[] = ['CRITICAL', 'MEDICAL', 'WARNING', 'SUPPLY', 'SAFE'];

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function asFiniteNumber(value: unknown): number | null {
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * Parses one uplink payload.
 *
 * Defensive in the same spirit as `EmergencyReport.fromLogEntry` on the
 * handset: anything malformed arriving from an unknown phone is sanitised or
 * rejected, never allowed to break the console.
 */
export function parseUplink(payload: UplinkPayload): { report: EmergencyReport } | { error: string } {
  const reportId = asString(payload.reportId);
  if (!reportId) return { error: 'reportId is required' };

  const rawCategory = asString(payload.category)?.toUpperCase() ?? '';
  const category = (VALID_CATEGORIES as string[]).includes(rawCategory)
    ? (rawCategory as Category)
    : 'WARNING';

  const lat = asFiniteNumber(payload.latitude);
  const lon = asFiniteNumber(payload.longitude);
  const hasPosition =
    lat !== null &&
    lon !== null &&
    lat >= -90 &&
    lat <= 90 &&
    lon >= -180 &&
    lon <= 180 &&
    !(lat === 0 && lon === 0);

  const location = hasPosition ? { latitude: lat, longitude: lon } : null;
  const timestamp = asFiniteNumber(payload.timestamp) ?? Date.now();
  const people = asFiniteNumber(payload.peopleAffected);
  const hops = asFiniteNumber(payload.hops);
  const now = Date.now();

  const report: EmergencyReport = {
    id: reportId,
    category,
    priority: 'P3', // recomputed by the console's scoring pass
    status: 'UNASSIGNED',
    description: asString(payload.description) ?? 'Report received without description',
    location,
    sector: sectorFor(location),
    reportedAt: timestamp > 0 && timestamp < now + 60_000 ? timestamp : now,
    peopleAffected: people !== null && people > 0 ? Math.round(people) : null,
    vulnerabilities: [],
    assignedTeamId: null,
    mesh: {
      senderNodeId: asString(payload.senderNodeId) ?? 'UNKNOWN',
      hopCount: hops !== null && hops >= 0 ? Math.round(hops) : undefined,
      originTimestamp: timestamp,
      collectedByRescuerId: asString(payload.gatewayId) ?? undefined,
      lastSyncedAt: now,
      pathRecorded: false,
    },
    notes: [],
    audioClipId: null,
    evidenceIds: [],
  };

  return { report };
}

let sequence = 0;
const nextId = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${sequence++}`;

/** Records an uploaded report and the sync/timeline entries it generates. */
export function ingest(report: EmergencyReport, gatewayId: string | null): { created: boolean } {
  const created = !liveStore.emergencies.has(report.id);
  const existing = liveStore.emergencies.get(report.id);

  // The mesh already guarantees id uniqueness, so a repeat is a re-delivery,
  // not a new incident. Preserve command-centre state and refresh the rest.
  liveStore.emergencies.set(
    report.id,
    existing
      ? {
          ...report,
          status: existing.status,
          assignedTeamId: existing.assignedTeamId,
          notes: existing.notes,
          vulnerabilities: existing.vulnerabilities,
        }
      : report,
  );

  const now = Date.now();
  liveStore.lastSyncedAt = now;
  if (gatewayId) liveStore.gateways.set(gatewayId, now);

  if (created) {
    liveStore.timeline.unshift({
      id: nextId('TL'),
      at: now,
      channel: 'EMERGENCY',
      severity: report.category === 'CRITICAL' ? 'CRITICAL' : report.category === 'MEDICAL' ? 'WARNING' : 'INFO',
      title: `${report.category} report received — ${report.id}`,
      detail: report.description,
      emergencyId: report.id,
      source: 'mesh',
    });
  }

  liveStore.syncEvents.unshift({
    id: nextId('SY'),
    at: now,
    gatewayId: gatewayId ?? 'unknown',
    text: created ? `Report ${report.id} received` : `Report ${report.id} re-delivered`,
    kind: created ? 'SUCCESS' : 'INFO',
    recordCount: 1,
  });

  // Bound the logs so a long-running process cannot grow without limit.
  if (liveStore.timeline.length > 2000) liveStore.timeline.length = 2000;
  if (liveStore.syncEvents.length > 1000) liveStore.syncEvents.length = 1000;

  return { created };
}

const GATEWAY_ONLINE_WINDOW_MS = 10 * 60_000;

/** Projects the live store into the dataset shape every screen consumes. */
export function buildLiveDataset(): OperationalDataset {
  const now = Date.now();
  const gatewaysOnline = Array.from(liveStore.gateways.values()).filter(
    (at) => now - at < GATEWAY_ONLINE_WINDOW_MS,
  ).length;

  return {
    emergencies: Array.from(liveStore.emergencies.values()).sort(
      (a, b) => b.reportedAt - a.reportedAt,
    ),
    // Teams, hospitals and hazards are command-centre records rather than
    // mesh traffic. Until an operator adds them, there are none — and the
    // console says so instead of inventing a roster.
    teams: [],
    missions: [],
    hospitals: [],
    hazards: [],
    fieldReports: [],
    meshNodes: [],
    meshLinks: [],
    timeline: liveStore.timeline.slice(0, 500),
    syncEvents: liveStore.syncEvents.slice(0, 200),
    commandPost: null,
    operationName: 'LIVE OPERATION',
    connection: {
      mode: 'LIVE',
      online: true,
      endpointConfigured: true,
      lastSyncedAt: liveStore.lastSyncedAt,
      activeGatewayId:
        Array.from(liveStore.gateways.entries()).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null,
      gatewaysOnline,
      pendingUploads: 0,
    },
  };
}
