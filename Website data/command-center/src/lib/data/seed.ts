import type {
  Category,
  EmergencyReport,
  EmergencyStatus,
  FieldReport,
  GeoPoint,
  Hazard,
  Hospital,
  MeshLink,
  MeshNode,
  NamedPlace,
  OperationalDataset,
  RescueMission,
  RescueTeam,
  SyncEvent,
  TimelineEvent,
  Vulnerability,
} from '../types';
import { destinationPoint, distanceMeters } from '../geo';

/**
 * DEMONSTRATION SCENARIO — "OP RIVERSIDE"
 *
 * Every record below is fabricated for demonstration and is labelled as such
 * throughout the interface. It is hand-authored rather than randomly
 * generated so that a presentation runs identically every time: the same
 * incidents, the same cluster in Sector 4, the same blocked road, the same
 * nearest team. Only the clock moves.
 *
 * The geography is anchored on the same coordinates the Android app opens at
 * (`OfflineMapRegion.DEFAULT_CENTER_*`), so the phone and the console
 * describe one shared operating picture.
 */

export const DEMO_CENTER: GeoPoint = { latitude: 12.9716, longitude: 77.5946 };

/** Places a point a given number of metres north and east of the centre. */
function at(northMeters: number, eastMeters: number): GeoPoint {
  const northed = destinationPoint(DEMO_CENTER, 0, northMeters);
  return destinationPoint(northed, 90, eastMeters);
}

// --- sectors ---------------------------------------------------------------

interface Sector {
  label: string;
  center: GeoPoint;
}

export const DEMO_SECTORS: Sector[] = [
  { label: 'Sector 1 — Northgate', center: at(1150, -250) },
  { label: 'Sector 2 — Millrace', center: at(450, 900) },
  { label: 'Sector 3 — Old Town', center: at(-150, -100) },
  { label: 'Sector 4 — Riverside', center: at(-900, 620) },
  { label: 'Sector 5 — Eastfield', center: at(250, -1150) },
  { label: 'Sector 6 — Harbour Row', center: at(-1250, -700) },
];

/** Nearest named sector, or null when a point falls outside all of them. */
export function sectorFor(point: GeoPoint | null): string | null {
  if (!point) return null;
  let best: string | null = null;
  let bestDistance = Infinity;
  for (const sector of DEMO_SECTORS) {
    const d = distanceMeters(point, sector.center);
    if (d < bestDistance) {
      bestDistance = d;
      best = sector.label;
    }
  }
  return bestDistance <= 1400 ? best : null;
}

// --- emergency specifications ---------------------------------------------

interface EmergencySpec {
  n: number;
  e: number;
  category: Category;
  description: string;
  people: number | null;
  vulnerabilities?: Vulnerability[];
  minutesAgo: number;
  status?: EmergencyStatus;
  node: string;
  hops?: number;
  audio?: boolean;
  evidence?: number;
  /** Deliberately omitted position — an SOS whose GPS never fixed. */
  noPosition?: boolean;
}

/**
 * The Sector 4 concentration is the centrepiece: a chain of reports inside a
 * few hundred metres, straddling a collapsed frontage, with a blocked road on
 * the direct approach. It exists to exercise clustering, routing and
 * decision support at once.
 */
const SECTOR_4_CLUSTER: EmergencySpec[] = [
  { n: -880, e: 600, category: 'CRITICAL', description: 'Three people trapped under collapsed shopfront awning. One unresponsive.', people: 3, vulnerabilities: ['TRAPPED', 'INJURED'], minutesAgo: 47, node: 'NODE-4821', hops: 3, audio: true, evidence: 2 },
  { n: -940, e: 655, category: 'CRITICAL', description: 'Family on second floor, stairwell collapsed. Cannot descend.', people: 4, vulnerabilities: ['TRAPPED', 'CHILDREN'], minutesAgo: 39, node: 'NODE-9137', hops: 2, audio: true },
  { n: -820, e: 700, category: 'CRITICAL', description: 'Elderly resident trapped in flooded ground-floor flat. Water rising.', people: 1, vulnerabilities: ['TRAPPED', 'ELDERLY'], minutesAgo: 34, node: 'NODE-2846', hops: 4 },
  { n: -1010, e: 560, category: 'CRITICAL', description: 'Vehicle overturned in floodwater, occupant still inside.', people: 2, vulnerabilities: ['TRAPPED', 'INJURED'], minutesAgo: 22, node: 'NODE-7712', hops: 1, evidence: 1 },
  { n: -900, e: 520, category: 'MEDICAL', description: 'Open leg fracture, heavy bleeding controlled with improvised tourniquet.', people: 1, vulnerabilities: ['INJURED'], minutesAgo: 41, node: 'NODE-3390', hops: 2, audio: true },
  { n: -960, e: 720, category: 'MEDICAL', description: 'Insulin-dependent diabetic, supply lost in flooding. 14 hours without.', people: 1, minutesAgo: 58, node: 'NODE-5518', hops: 3 },
  { n: -790, e: 640, category: 'MEDICAL', description: 'Suspected smoke inhalation, two adults, breathing laboured.', people: 2, vulnerabilities: ['INJURED'], minutesAgo: 29, node: 'NODE-6604', hops: 2 },
  { n: -1050, e: 680, category: 'MEDICAL', description: 'Pregnant woman, contractions started. No transport available.', people: 1, vulnerabilities: ['PREGNANT'], minutesAgo: 18, node: 'NODE-8823', hops: 1, audio: true },
  { n: -870, e: 760, category: 'MEDICAL', description: 'Head injury from falling masonry, conscious but disoriented.', people: 1, vulnerabilities: ['INJURED'], minutesAgo: 12, node: 'NODE-1177', hops: 2 },
  { n: -1080, e: 620, category: 'SUPPLY', description: 'Shelter group of 22 without drinking water since morning.', people: 22, vulnerabilities: ['CHILDREN', 'ELDERLY'], minutesAgo: 66, node: 'NODE-4402', hops: 3 },
  { n: -760, e: 560, category: 'SUPPLY', description: 'Community kitchen requesting bandages and antiseptic.', people: 8, minutesAgo: 51, node: 'NODE-9950', hops: 2 },
  { n: -930, e: 830, category: 'SAFE', description: 'Nine residents accounted for on upper floors. No assistance required.', people: 9, minutesAgo: 25, status: 'RESOLVED', node: 'NODE-2201', hops: 2 },
  { n: -1000, e: 470, category: 'SAFE', description: 'Block evacuated to school shelter. All present and well.', people: 16, minutesAgo: 44, status: 'RESOLVED', node: 'NODE-7080', hops: 4 },
];

const WIDER_AREA: EmergencySpec[] = [
  { n: 1180, e: -220, category: 'CRITICAL', description: 'Worker fallen into open drainage culvert, conscious, cannot climb out.', people: 1, vulnerabilities: ['TRAPPED', 'INJURED'], minutesAgo: 31, status: 'ASSIGNED', node: 'NODE-3312', hops: 2 },
  { n: 1090, e: -310, category: 'MEDICAL', description: 'Chest pain, 60s, no cardiac history known.', people: 1, vulnerabilities: ['ELDERLY'], minutesAgo: 16, status: 'ACKNOWLEDGED', node: 'NODE-5567', hops: 1, audio: true },
  { n: 1250, e: -140, category: 'WARNING', description: 'Gas smell reported along the north parade. Area not evacuated.', people: null, minutesAgo: 23, node: 'NODE-9014', hops: 3 },
  { n: 1020, e: -400, category: 'SAFE', description: 'Twelve residents checked in from the north shelter.', people: 12, minutesAgo: 55, status: 'RESOLVED', node: 'NODE-2290', hops: 2 },

  { n: 470, e: 880, category: 'MEDICAL', description: 'Deep laceration to forearm, bleeding slowed but not stopped.', people: 1, vulnerabilities: ['INJURED'], minutesAgo: 37, status: 'ACKNOWLEDGED', node: 'NODE-6621', hops: 2 },
  { n: 400, e: 960, category: 'WARNING', description: 'Partial roof collapse at the mill. Building unsafe to enter.', people: null, minutesAgo: 72, node: 'NODE-4478', hops: 3, evidence: 1 },
  { n: 530, e: 820, category: 'SUPPLY', description: 'Generator fuel exhausted at the mill shelter.', people: 30, minutesAgo: 61, node: 'NODE-1903', hops: 2 },
  { n: 380, e: 1010, category: 'SAFE', description: 'Mill shelter reports all thirty occupants safe.', people: 30, minutesAgo: 20, status: 'RESOLVED', node: 'NODE-7745', hops: 2 },

  { n: -120, e: -60, category: 'CRITICAL', description: 'SOS raised, no further detail received. Audio only.', people: null, vulnerabilities: ['TRAPPED'], minutesAgo: 9, node: 'NODE-8890', hops: 5, audio: true, noPosition: true },
  { n: -180, e: -140, category: 'MEDICAL', description: 'Two casualties from a fall on wet stairs, suspected fractures.', people: 2, vulnerabilities: ['INJURED'], minutesAgo: 43, status: 'ACKNOWLEDGED', node: 'NODE-3047', hops: 2 },
  { n: -90, e: -190, category: 'WARNING', description: 'Live cable down across the market approach.', people: null, minutesAgo: 28, node: 'NODE-5583', hops: 1, evidence: 1 },
  { n: -210, e: -30, category: 'SUPPLY', description: 'Pharmacy requesting insulin and paediatric antibiotics.', people: 6, vulnerabilities: ['CHILDREN'], minutesAgo: 49, node: 'NODE-9928', hops: 3 },
  { n: -60, e: -110, category: 'SAFE', description: 'Old Town residents association confirms 41 accounted for.', people: 41, minutesAgo: 33, status: 'RESOLVED', node: 'NODE-2115', hops: 2 },

  { n: 280, e: -1120, category: 'MEDICAL', description: 'Dialysis patient missed treatment, showing symptoms.', people: 1, minutesAgo: 88, status: 'ASSIGNED', node: 'NODE-6672', hops: 4 },
  { n: 210, e: -1190, category: 'WARNING', description: 'Riverbank eroding beside the eastfield footbridge.', people: null, minutesAgo: 95, node: 'NODE-4409', hops: 3 },
  { n: 320, e: -1050, category: 'SUPPLY', description: 'Blankets and dry clothing requested for 18 displaced.', people: 18, vulnerabilities: ['CHILDREN'], minutesAgo: 70, node: 'NODE-1140', hops: 2 },
  { n: 190, e: -1080, category: 'SAFE', description: 'Eastfield ward reports no casualties.', people: 25, minutesAgo: 40, status: 'RESOLVED', node: 'NODE-8802', hops: 3 },

  { n: -1230, e: -680, category: 'CRITICAL', description: 'Two fishers cut off by tide on the harbour wall.', people: 2, vulnerabilities: ['TRAPPED'], minutesAgo: 26, node: 'NODE-3358', hops: 2, evidence: 1 },
  { n: -1300, e: -740, category: 'MEDICAL', description: 'Hypothermia suspected, one adult recovered from water.', people: 1, vulnerabilities: ['INJURED'], minutesAgo: 14, status: 'IN_PROGRESS', node: 'NODE-7791', hops: 1 },
  { n: -1180, e: -620, category: 'WARNING', description: 'Harbour Row flooding to knee depth and still rising.', people: null, minutesAgo: 52, node: 'NODE-5502', hops: 2 },
  { n: -1340, e: -650, category: 'SUPPLY', description: 'Harbour shelter short of drinking water for 40.', people: 40, minutesAgo: 36, node: 'NODE-9963', hops: 3 },
  { n: -1150, e: -790, category: 'SAFE', description: 'Harbour Row: eleven households confirmed safe.', people: 11, minutesAgo: 30, status: 'RESOLVED', node: 'NODE-2274', hops: 2 },
];

const ALL_SPECS = [...SECTOR_4_CLUSTER, ...WIDER_AREA];

// ---------------------------------------------------------------------------

function buildEmergencies(now: number): EmergencyReport[] {
  return ALL_SPECS.map((spec, index) => {
    const reportedAt = now - spec.minutesAgo * 60_000;
    const location = spec.noPosition ? null : at(spec.n, spec.e);
    const status = spec.status ?? 'UNASSIGNED';

    return {
      // Sequential and stable: a walkthrough can name an incident out loud
      // and it will be there, in that position, every run.
      id: `DM-${1001 + index}`,
      category: spec.category,
      // Filled in by the scoring pass; the seed never asserts a priority.
      priority: 'P3',
      status,
      description: spec.description,
      location,
      sector: sectorFor(location),
      reportedAt,
      peopleAffected: spec.people,
      vulnerabilities: spec.vulnerabilities ?? [],
      assignedTeamId: null,
      mesh: {
        senderNodeId: spec.node,
        hopCount: spec.hops,
        originTimestamp: reportedAt - (spec.hops ?? 0) * 24_000,
        collectedByRescuerId: index % 3 === 0 ? 'RSC-02' : 'RSC-01',
        lastSyncedAt: now - ((index * 37) % 9) * 60_000,
        // The Android relay copies the envelope without appending a route, so
        // the node-by-node path is genuinely unknown. Never claim otherwise.
        pathRecorded: false,
      },
      notes: [],
      audioClipId: spec.audio ? `AUD-${1000 + index}` : null,
      evidenceIds: Array.from({ length: spec.evidence ?? 0 }, (_, k) => `EVD-${1000 + index}-${k}`),
    } satisfies EmergencyReport;
  });
}

// --- teams -----------------------------------------------------------------

function buildTeams(now: number): RescueTeam[] {
  return [
    {
      id: 'TEAM-A',
      name: 'Team Alpha',
      callsign: 'ALPHA',
      members: [
        { id: 'M-A1', name: 'A. Rao', role: 'Team lead' },
        { id: 'M-A2', name: 'K. Iyer', role: 'Paramedic' },
        { id: 'M-A3', name: 'S. Fernandes', role: 'Extraction' },
        { id: 'M-A4', name: 'D. Kulkarni', role: 'Extraction' },
      ],
      capabilities: ['MEDICAL', 'EXTRACTION'],
      // Staged clear of the Riverside debris field (HZ-1), so the approach to
      // Sector 4 is a detour rather than an impossibility.
      location: at(-450, 250),
      status: 'AVAILABLE',
      currentMissionId: null,
      batteryPercent: 74,
      connectivity: 'GATEWAY',
      lastSyncedAt: now - 2 * 60_000,
      completedMissions: 6,
    },
    {
      id: 'TEAM-B',
      name: 'Team Bravo',
      callsign: 'BRAVO',
      members: [
        { id: 'M-B1', name: 'P. Nair', role: 'Team lead' },
        { id: 'M-B2', name: 'R. Menon', role: 'Paramedic' },
        { id: 'M-B3', name: 'T. Joshi', role: 'Water rescue' },
      ],
      capabilities: ['MEDICAL', 'WATER_RESCUE'],
      location: at(960, -260),
      status: 'EN_ROUTE',
      currentMissionId: 'MSN-001',
      batteryPercent: 41,
      connectivity: 'MESH_ONLY',
      lastSyncedAt: now - 6 * 60_000,
      completedMissions: 9,
    },
    {
      id: 'TEAM-C',
      name: 'Team Charlie',
      callsign: 'CHARLIE',
      members: [
        { id: 'M-C1', name: 'V. Shetty', role: 'Team lead' },
        { id: 'M-C2', name: 'N. Bose', role: 'Fire' },
        { id: 'M-C3', name: 'H. Patel', role: 'Fire' },
        { id: 'M-C4', name: 'L. DSouza', role: 'Extraction' },
        { id: 'M-C5', name: 'G. Verma', role: 'Medic' },
      ],
      capabilities: ['FIRE', 'EXTRACTION'],
      location: at(-1140, -560),
      status: 'ON_SCENE',
      currentMissionId: 'MSN-002',
      batteryPercent: 88,
      connectivity: 'GATEWAY',
      lastSyncedAt: now - 60_000,
      completedMissions: 4,
    },
    {
      id: 'TEAM-D',
      name: 'Team Delta',
      callsign: 'DELTA',
      members: [
        { id: 'M-D1', name: 'J. Thomas', role: 'Team lead' },
        { id: 'M-D2', name: 'B. Chandra', role: 'Logistics' },
        { id: 'M-D3', name: 'M. Qureshi', role: 'Logistics' },
      ],
      capabilities: ['LOGISTICS', 'RECON'],
      location: at(340, 700),
      status: 'AVAILABLE',
      currentMissionId: null,
      batteryPercent: 63,
      connectivity: 'MESH_ONLY',
      lastSyncedAt: now - 11 * 60_000,
      completedMissions: 11,
    },
    {
      id: 'TEAM-E',
      name: 'Team Echo',
      callsign: 'ECHO',
      members: [
        { id: 'M-E1', name: 'F. Dsa', role: 'Team lead' },
        { id: 'M-E2', name: 'W. Pinto', role: 'Water rescue' },
        { id: 'M-E3', name: 'C. Reddy', role: 'Water rescue' },
      ],
      capabilities: ['WATER_RESCUE', 'RECON'],
      // Out of contact: no position, no telemetry. The console shows the gaps
      // rather than the last known values dressed up as current ones.
      location: null,
      status: 'OFFLINE',
      currentMissionId: null,
      lastSyncedAt: now - 74 * 60_000,
      completedMissions: 7,
    },
  ];
}

// --- hospitals -------------------------------------------------------------

function buildHospitals(now: number): Hospital[] {
  return [
    {
      id: 'HOSP-1',
      name: 'Riverside General Hospital',
      location: at(-420, 1180),
      status: 'OPEN',
      totalBeds: 320,
      availableBeds: 47,
      emergencyBeds: 12,
      icuBeds: 4,
      traumaCapable: true,
      ambulancesAvailable: 3,
      lastUpdated: now - 8 * 60_000,
      source: 'rescuer',
    },
    {
      id: 'HOSP-2',
      name: 'Northgate Medical Centre',
      location: at(1420, 240),
      status: 'NEAR_CAPACITY',
      totalBeds: 180,
      availableBeds: 9,
      emergencyBeds: 3,
      icuBeds: 0,
      traumaCapable: true,
      ambulancesAvailable: 1,
      lastUpdated: now - 21 * 60_000,
      source: 'rescuer',
    },
    {
      id: 'HOSP-3',
      name: 'St. Cuthbert Community Clinic',
      location: at(-980, -1240),
      status: 'LIMITED',
      totalBeds: 60,
      availableBeds: 14,
      emergencyBeds: 2,
      traumaCapable: false,
      ambulancesAvailable: 0,
      lastUpdated: now - 34 * 60_000,
      source: 'mesh',
    },
    {
      id: 'HOSP-4',
      name: 'Eastfield District Hospital',
      // Unreachable since the event began. Capacity fields stay undefined
      // rather than being guessed — "unknown" is the truthful answer.
      location: at(760, -1560),
      status: 'UNKNOWN',
      lastUpdated: null,
      source: 'command',
    },
  ];
}

// --- hazards ---------------------------------------------------------------

function buildHazards(now: number): Hazard[] {
  return [
    {
      id: 'HZ-1',
      kind: 'BLOCKED_ROAD',
      label: 'Riverside approach blocked — debris across carriageway',
      center: at(-620, 480),
      radiusMeters: 190,
      reportedAt: now - 26 * 60_000,
      reportedBy: 'RSC-01',
      source: 'rescuer',
      active: true,
    },
    {
      id: 'HZ-2',
      kind: 'FLOOD',
      label: 'Harbour Row flood zone — knee depth, rising',
      center: at(-1210, -700),
      radiusMeters: 260,
      reportedAt: now - 52 * 60_000,
      reportedBy: 'NODE-5502',
      source: 'mesh',
      active: true,
    },
    {
      id: 'HZ-3',
      kind: 'COLLAPSE',
      label: 'Mill roof collapse — structure unsafe',
      center: at(400, 960),
      radiusMeters: 140,
      reportedAt: now - 72 * 60_000,
      reportedBy: 'NODE-4478',
      source: 'mesh',
      active: true,
    },
    {
      id: 'HZ-4',
      kind: 'UNSAFE_AREA',
      label: 'Live cable down across market approach',
      center: at(-90, -190),
      radiusMeters: 90,
      reportedAt: now - 28 * 60_000,
      reportedBy: 'NODE-5583',
      source: 'mesh',
      active: true,
    },
  ];
}

// --- missions --------------------------------------------------------------

/**
 * Missions are bound to the incident each team is actually standing next to.
 *
 * Matching on status alone once paired Team Charlie — staged at Harbour Row —
 * with a Northgate case, so the roster showed a team "on scene" 2.2 km from
 * its own assignment. Nothing in the interface can repair a scenario that
 * contradicts itself, so the seed has to be coherent.
 */
function buildMissions(now: number, emergencies: EmergencyReport[]): RescueMission[] {
  const assigned = emergencies.find((e) => e.status === 'ASSIGNED');
  const inProgress = emergencies.find(
    (e) => e.status === 'IN_PROGRESS' && e.sector?.includes('Harbour Row'),
  );
  const missions: RescueMission[] = [];

  if (assigned) {
    missions.push({
      id: 'MSN-001',
      emergencyId: assigned.id,
      teamId: 'TEAM-B',
      status: 'EN_ROUTE',
      assignedAt: now - 14 * 60_000,
      history: [
        { status: 'ASSIGNED', at: now - 14 * 60_000, byLabel: 'Cdr. Operations' },
        { status: 'EN_ROUTE', at: now - 11 * 60_000, byLabel: 'Team Bravo' },
      ],
      completedAt: null,
      routeId: null,
    });
    assigned.assignedTeamId = 'TEAM-B';
  }

  if (inProgress) {
    missions.push({
      id: 'MSN-002',
      emergencyId: inProgress.id,
      teamId: 'TEAM-C',
      status: 'ON_SCENE',
      assignedAt: now - 27 * 60_000,
      history: [
        { status: 'ASSIGNED', at: now - 27 * 60_000, byLabel: 'Cdr. Operations' },
        { status: 'EN_ROUTE', at: now - 24 * 60_000, byLabel: 'Team Charlie' },
        { status: 'ON_SCENE', at: now - 9 * 60_000, byLabel: 'Team Charlie' },
      ],
      completedAt: null,
      routeId: null,
    });
    inProgress.assignedTeamId = 'TEAM-C';
  }

  return missions;
}

// --- mesh ------------------------------------------------------------------

/** Deterministic PRNG. Same seed, same mesh, every run. */
function mulberry32(seed: number): () => number {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function buildMesh(now: number, emergencies: EmergencyReport[]): {
  nodes: MeshNode[];
  links: MeshLink[];
} {
  const rand = mulberry32(20260907);
  const nodes: MeshNode[] = [];

  // Every reporting node is real: it is the origin of a report we hold.
  const reporters = new Map<string, EmergencyReport[]>();
  for (const e of emergencies) {
    const list = reporters.get(e.mesh.senderNodeId) ?? [];
    list.push(e);
    reporters.set(e.mesh.senderNodeId, list);
  }

  for (const [nodeId, reports] of reporters) {
    const lastHeard = Math.max(...reports.map((r) => r.reportedAt));
    const age = now - lastHeard;
    nodes.push({
      id: nodeId,
      kind: 'CIVILIAN',
      state: age < 20 * 60_000 ? 'CONNECTED' : age < 50 * 60_000 ? 'RECENTLY_SYNCED' : 'DISCONNECTED',
      // A relay does not report its own position. The console knows where a
      // report came FROM only when that report carried coordinates, and it
      // will not borrow one report's position for the node itself unless the
      // node is the origin of exactly that report.
      location: reports[0].location,
      lastHeardAt: lastHeard,
      reportsContributed: reports.length,
    });
  }

  // Relay-only nodes: seen forwarding traffic, never originated a report, so
  // their position is genuinely unknown and stays null.
  for (let i = 0; i < 14; i += 1) {
    const id = `NODE-${Math.floor(1000 + rand() * 8999)}`;
    if (nodes.some((n) => n.id === id)) continue;
    nodes.push({
      id,
      kind: 'CIVILIAN',
      state: rand() > 0.35 ? 'CONNECTED' : 'DISCONNECTED',
      location: null,
      lastHeardAt: now - Math.floor(rand() * 55) * 60_000,
      reportsContributed: 0,
    });
  }

  const rescuers: MeshNode[] = [
    { id: 'RSC-01', kind: 'RESCUER', state: 'CONNECTED', location: at(-450, 250), lastHeardAt: now - 2 * 60_000, reportsContributed: 19, deviceModel: 'Pixel 8' },
    { id: 'RSC-02', kind: 'RESCUER', state: 'CONNECTED', location: at(-1140, -560), lastHeardAt: now - 60_000, reportsContributed: 12, deviceModel: 'Galaxy S23' },
    { id: 'RSC-03', kind: 'RESCUER', state: 'RECENTLY_SYNCED', location: at(960, -260), lastHeardAt: now - 6 * 60_000, reportsContributed: 8, deviceModel: 'Pixel 7a' },
    { id: 'RSC-04', kind: 'RESCUER', state: 'DISCONNECTED', location: null, lastHeardAt: now - 74 * 60_000, reportsContributed: 3, deviceModel: 'Moto G84' },
  ];

  const gateways: MeshNode[] = [
    { id: 'GW-01', kind: 'GATEWAY', state: 'CONNECTED', location: at(-430, 270), lastHeardAt: now - 2 * 60_000, reportsContributed: 0, deviceModel: 'Pixel 8 · Cellular' },
    { id: 'GW-02', kind: 'GATEWAY', state: 'CONNECTED', location: at(-1120, -540), lastHeardAt: now - 60_000, reportsContributed: 0, deviceModel: 'Galaxy S23 · Wi-Fi' },
    { id: 'GW-03', kind: 'GATEWAY', state: 'RECENTLY_SYNCED', location: at(950, -240), lastHeardAt: now - 6 * 60_000, reportsContributed: 0, deviceModel: 'Pixel 7a · Cellular' },
  ];

  const all = [...nodes, ...rescuers, ...gateways];

  // Links are recorded adjacencies only: a report that reached RSC-01 proves
  // a path existed to RSC-01, so that edge is drawn. Nothing else is inferred.
  const links: MeshLink[] = [];
  for (const e of emergencies) {
    const collector = e.mesh.collectedByRescuerId;
    if (!collector) continue;
    links.push({ fromId: e.mesh.senderNodeId, toId: collector, observedAt: e.reportedAt });
  }
  links.push({ fromId: 'RSC-01', toId: 'GW-01', observedAt: now - 2 * 60_000 });
  links.push({ fromId: 'RSC-02', toId: 'GW-02', observedAt: now - 60_000 });
  links.push({ fromId: 'RSC-03', toId: 'GW-03', observedAt: now - 6 * 60_000 });

  return { nodes: all, links };
}

// --- field reports ---------------------------------------------------------

function buildFieldReports(now: number, emergencies: EmergencyReport[]): FieldReport[] {
  const pick = (i: number): EmergencyReport | undefined => emergencies[i];
  const specs: Array<Omit<FieldReport, 'id' | 'mediaAvailable'>> = [
    { kind: 'PHOTO', title: 'Collapsed shopfront, east elevation', body: 'Awning down across the pavement. Two voids visible at ground level. Lifting gear required.', reporterId: 'RSC-01', reporterLabel: 'Rescuer RSC-01', teamId: 'TEAM-A', emergencyId: pick(0)?.id ?? null, location: at(-880, 600), capturedAt: now - 44 * 60_000, syncedAt: now - 7 * 60_000 },
    { kind: 'AUDIO', title: 'Voice report — trapped family, second floor', body: 'Caller states four people including two children. Stairwell impassable.', reporterId: 'NODE-9137', reporterLabel: 'Civilian node NODE-9137', teamId: null, emergencyId: pick(1)?.id ?? null, location: at(-940, 655), capturedAt: now - 39 * 60_000, syncedAt: now - 7 * 60_000, durationSeconds: 34 },
    { kind: 'PHOTO', title: 'Water level at Riverside junction', body: 'Approximately 40 cm and rising over the last half hour.', reporterId: 'RSC-01', reporterLabel: 'Rescuer RSC-01', teamId: 'TEAM-A', emergencyId: null, location: at(-820, 540), capturedAt: now - 31 * 60_000, syncedAt: now - 7 * 60_000 },
    { kind: 'TEXT', title: 'Access assessment — Riverside approach', body: 'Debris field roughly 190 m across. Not passable by vehicle. Foot access possible from the north side only.', reporterId: 'RSC-01', reporterLabel: 'Rescuer RSC-01', teamId: 'TEAM-A', emergencyId: null, location: at(-620, 480), capturedAt: now - 26 * 60_000, syncedAt: now - 7 * 60_000 },
    { kind: 'VIDEO', title: 'Overturned vehicle in floodwater', body: 'Occupant visible through rear window. Vehicle unstable in current.', reporterId: 'NODE-7712', reporterLabel: 'Civilian node NODE-7712', teamId: null, emergencyId: pick(3)?.id ?? null, location: at(-1010, 560), capturedAt: now - 22 * 60_000, syncedAt: now - 7 * 60_000, durationSeconds: 18 },
    { kind: 'AUDIO', title: 'Voice report — medical, open fracture', body: 'Bystander describes tourniquet applied at 40 minutes. Patient conscious.', reporterId: 'NODE-3390', reporterLabel: 'Civilian node NODE-3390', teamId: null, emergencyId: pick(4)?.id ?? null, location: at(-900, 520), capturedAt: now - 41 * 60_000, syncedAt: now - 7 * 60_000, durationSeconds: 51 },
    { kind: 'NOTE', title: 'Shelter headcount — Riverside school', body: '22 occupants. No potable water since 09:00. Two insulin-dependent.', reporterId: 'RSC-02', reporterLabel: 'Rescuer RSC-02', teamId: 'TEAM-C', emergencyId: pick(9)?.id ?? null, location: at(-1080, 620), capturedAt: now - 66 * 60_000, syncedAt: now - 12 * 60_000 },
    { kind: 'PHOTO', title: 'Harbour wall — two persons cut off', body: 'Tide covering the causeway. Both standing, waving. Boat access recommended.', reporterId: 'NODE-3358', reporterLabel: 'Civilian node NODE-3358', teamId: null, emergencyId: null, location: at(-1230, -680), capturedAt: now - 26 * 60_000, syncedAt: now - 12 * 60_000 },
    { kind: 'TEXT', title: 'Mill structure assessment', body: 'Roof truss failure over the north bay. Do not enter. Cordon set at 140 m.', reporterId: 'RSC-03', reporterLabel: 'Rescuer RSC-03', teamId: 'TEAM-D', emergencyId: null, location: at(400, 960), capturedAt: now - 70 * 60_000, syncedAt: now - 18 * 60_000 },
    { kind: 'PHOTO', title: 'Live cable, market approach', body: 'Cable in standing water. Approach cordoned from both ends.', reporterId: 'NODE-5583', reporterLabel: 'Civilian node NODE-5583', teamId: null, emergencyId: null, location: at(-90, -190), capturedAt: now - 28 * 60_000, syncedAt: now - 18 * 60_000 },
  ];

  return specs.map((spec, i) => ({
    ...spec,
    id: `FR-${2000 + i}`,
    // No real media ships with the demo. The viewer says so rather than
    // rendering a broken frame.
    mediaAvailable: false,
  }));
}

// --- timeline & sync -------------------------------------------------------

function buildTimeline(now: number, emergencies: EmergencyReport[]): TimelineEvent[] {
  const events: TimelineEvent[] = [];
  let seq = 0;
  const add = (e: Omit<TimelineEvent, 'id'>): void => {
    events.push({ ...e, id: `TL-${3000 + seq++}` });
  };

  for (const e of emergencies) {
    add({
      at: e.reportedAt,
      channel: 'EMERGENCY',
      severity: e.category === 'CRITICAL' ? 'CRITICAL' : e.category === 'MEDICAL' ? 'WARNING' : 'INFO',
      title: `${e.category === 'CRITICAL' ? 'SOS' : e.category} report received — ${e.id}`,
      detail: e.description,
      emergencyId: e.id,
      source: 'mesh',
    });
  }

  add({ at: now - 74 * 60_000, channel: 'TEAM', severity: 'WARNING', title: 'Team Echo lost contact', detail: 'No synchronisation received since. Position and telemetry unknown.', teamId: 'TEAM-E', source: 'derived' });
  add({ at: now - 72 * 60_000, channel: 'SYSTEM', severity: 'WARNING', title: 'Hazard recorded — mill roof collapse', detail: 'Cordon radius 140 m. Structure unsafe to enter.', source: 'mesh' });
  add({ at: now - 52 * 60_000, channel: 'SYSTEM', severity: 'WARNING', title: 'Hazard recorded — Harbour Row flood zone', detail: 'Knee depth and rising. Avoidance radius 260 m.', source: 'mesh' });
  add({ at: now - 27 * 60_000, channel: 'RESCUE', severity: 'INFO', title: 'Team Charlie assigned', detail: 'Harbour Row medical response.', teamId: 'TEAM-C', source: 'command' });
  add({ at: now - 26 * 60_000, channel: 'ROUTE', severity: 'WARNING', title: 'Road blockage reported on Riverside approach', detail: 'Debris across carriageway. Direct approach to Sector 4 is not passable.', source: 'rescuer' });
  add({ at: now - 24 * 60_000, channel: 'TEAM', severity: 'INFO', title: 'Team Charlie en route', teamId: 'TEAM-C', source: 'rescuer' });
  add({ at: now - 14 * 60_000, channel: 'RESCUE', severity: 'INFO', title: 'Team Bravo assigned', detail: 'Northgate culvert extraction.', teamId: 'TEAM-B', source: 'command' });
  add({ at: now - 11 * 60_000, channel: 'TEAM', severity: 'INFO', title: 'Team Bravo en route', teamId: 'TEAM-B', source: 'rescuer' });
  add({ at: now - 9 * 60_000, channel: 'TEAM', severity: 'SUCCESS', title: 'Team Charlie on scene', detail: 'Harbour Row.', teamId: 'TEAM-C', source: 'rescuer' });
  add({ at: now - 18 * 60_000, channel: 'SYNC', severity: 'INFO', title: 'Gateway GW-03 connected', detail: 'Rescuer RSC-03 regained cellular service.', source: 'rescuer' });
  add({ at: now - 12 * 60_000, channel: 'SYNC', severity: 'SUCCESS', title: '11 reports synchronised', detail: 'From gateway GW-02.', source: 'rescuer' });
  add({ at: now - 7 * 60_000, channel: 'SYNC', severity: 'SUCCESS', title: '19 reports synchronised', detail: 'From gateway GW-01.', source: 'rescuer' });

  return events.sort((a, b) => b.at - a.at);
}

function buildSyncEvents(now: number): SyncEvent[] {
  const events: SyncEvent[] = [
    { id: 'SY-1', at: now - 18 * 60_000, gatewayId: 'GW-03', text: 'Rescuer RSC-03 regained cellular service', kind: 'GATEWAY' },
    { id: 'SY-2', at: now - 18 * 60_000, gatewayId: 'GW-03', text: '8 emergency reports synchronised', kind: 'SUCCESS', recordCount: 8 },
    { id: 'SY-3', at: now - 12 * 60_000, gatewayId: 'GW-02', text: 'Rescuer RSC-02 connected via Wi-Fi', kind: 'GATEWAY' },
    { id: 'SY-4', at: now - 12 * 60_000, gatewayId: 'GW-02', text: '11 emergency reports synchronised', kind: 'SUCCESS', recordCount: 11 },
    { id: 'SY-5', at: now - 12 * 60_000, gatewayId: 'GW-02', text: '3 audio reports synchronised', kind: 'SUCCESS', recordCount: 3 },
    { id: 'SY-6', at: now - 9 * 60_000, gatewayId: 'GW-02', text: '2 rescue status updates synchronised', kind: 'SUCCESS', recordCount: 2 },
    { id: 'SY-7', at: now - 7 * 60_000, gatewayId: 'GW-01', text: 'Rescuer RSC-01 connected via cellular', kind: 'GATEWAY' },
    { id: 'SY-8', at: now - 7 * 60_000, gatewayId: 'GW-01', text: '19 emergency reports synchronised', kind: 'SUCCESS', recordCount: 19 },
    { id: 'SY-9', at: now - 6 * 60_000, gatewayId: 'GW-01', text: '4 field reports synchronised', kind: 'SUCCESS', recordCount: 4 },
    { id: 'SY-10', at: now - 5 * 60_000, gatewayId: 'GW-04', text: 'Gateway GW-04 unreachable — 4 records held on device', kind: 'FAILURE' },
    { id: 'SY-11', at: now - 2 * 60_000, gatewayId: 'GW-01', text: 'Heartbeat received', kind: 'INFO' },
  ];
  return events.sort((a, b) => b.at - a.at);
}

// ---------------------------------------------------------------------------

export const COMMAND_POST: NamedPlace = {
  ...at(-320, 180),
  label: 'Forward Command Post — Old Town Depot',
};

/** Builds the entire demonstration dataset for a given clock. */
export function buildDemoDataset(now: number): OperationalDataset {
  const emergencies = buildEmergencies(now);
  const missions = buildMissions(now, emergencies);

  // Invariant: a report cannot be ASSIGNED or IN_PROGRESS without a team
  // actually bound to it. Enforced here rather than trusted to the authored
  // rows, so a future edit to the scenario cannot reintroduce an incident
  // whose status claims a responder it does not have.
  for (const report of emergencies) {
    const active = report.status === 'ASSIGNED' || report.status === 'IN_PROGRESS';
    if (active && !report.assignedTeamId) report.status = 'ACKNOWLEDGED';
  }

  const { nodes, links } = buildMesh(now, emergencies);

  return {
    emergencies,
    teams: buildTeams(now),
    missions,
    hospitals: buildHospitals(now),
    hazards: buildHazards(now),
    fieldReports: buildFieldReports(now, emergencies),
    meshNodes: nodes,
    meshLinks: links,
    timeline: buildTimeline(now, emergencies),
    syncEvents: buildSyncEvents(now),
    commandPost: COMMAND_POST,
    operationName: 'OP RIVERSIDE',
    connection: {
      mode: 'DEMO',
      online: true,
      endpointConfigured: true,
      lastSyncedAt: now - 2 * 60_000,
      activeGatewayId: 'GW-01',
      gatewaysOnline: 3,
      pendingUploads: 4,
    },
  };
}
