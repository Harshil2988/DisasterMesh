/**
 * DISASTERMESH — RESCUE COMMAND CENTER
 * Domain model.
 *
 * The categories, priority letters and status vocabulary here mirror the
 * Android app's `command/EmergencyReport.kt` and `uplink/ExternalUplink.kt`
 * so a report keeps its meaning across the whole pipeline:
 *
 *   civilian node -> mesh relay -> rescuer device -> uplink -> this console
 */

// ---------------------------------------------------------------------------
// Provenance — the honesty layer
// ---------------------------------------------------------------------------

/**
 * Where a value actually came from. Rendered in the UI so a commander is
 * never left guessing whether a number was reported, entered, or computed.
 */
export type FieldSource =
  | 'mesh' // travelled the civilian mesh; originated on a member of the public
  | 'rescuer' // captured or amended on a rescuer device in the field
  | 'command' // entered here, at the command centre, by an operator
  | 'derived'; // computed by this console from other values

export type DataMode = 'DEMO' | 'LIVE';

/**
 * The link to the outside world. Mirrors `UplinkStatus` in the Android app:
 * a gateway needs BOTH a validated path out AND a configured endpoint.
 */
export interface ConnectionState {
  mode: DataMode;
  online: boolean;
  endpointConfigured: boolean;
  lastSyncedAt: number | null;
  activeGatewayId: string | null;
  gatewaysOnline: number;
  pendingUploads: number;
  /** Populated only when the console failed to reach its data source. */
  error?: string;
}

// ---------------------------------------------------------------------------
// Geography
// ---------------------------------------------------------------------------

export interface GeoPoint {
  latitude: number;
  longitude: number;
  /** Metres of horizontal uncertainty, when the reporting device supplied it. */
  accuracy?: number;
  /** When the fix was taken — not when the report was received. */
  timestamp?: number;
}

export interface NamedPlace extends GeoPoint {
  /** Operator-assigned sector label, e.g. "Sector 4 — Riverside". */
  label: string;
}

// ---------------------------------------------------------------------------
// Emergencies
// ---------------------------------------------------------------------------

export const CATEGORIES = ['CRITICAL', 'MEDICAL', 'WARNING', 'SUPPLY', 'SAFE'] as const;
export type Category = (typeof CATEGORIES)[number];

/** P0 is the most urgent. Fixed per category on the device; refined here. */
export type PriorityLevel = 'P0' | 'P1' | 'P2' | 'P3';

export const EMERGENCY_STATUSES = [
  'UNASSIGNED',
  'ACKNOWLEDGED',
  'ASSIGNED',
  'IN_PROGRESS',
  'RESCUED',
  'RESOLVED',
  'INVALID',
] as const;
export type EmergencyStatus = (typeof EMERGENCY_STATUSES)[number];

/** Reported vulnerability of the people involved. Raises the priority score. */
export type Vulnerability = 'CHILDREN' | 'ELDERLY' | 'INJURED' | 'TRAPPED' | 'DISABLED' | 'PREGNANT';

export interface MeshProvenance {
  /** Mesh node id of the phone that first raised this, e.g. NODE-4821. */
  senderNodeId: string;
  /** Relays crossed before a rescuer heard it. Undefined when not recorded. */
  hopCount?: number;
  /** When the originating handset created the report. */
  originTimestamp: number;
  /** Rescuer device that carried it out of the mesh, when known. */
  collectedByRescuerId?: string;
  /** When this console last received an update for it. */
  lastSyncedAt?: number;
  /**
   * True only when the mesh actually recorded the node-by-node path. The
   * Android relay copies the envelope without appending a route, so this is
   * normally false and the UI must say "relayed through N hops" instead of
   * drawing a path it does not know.
   */
  pathRecorded: boolean;
  relayPath?: string[];
}

export interface EmergencyNote {
  id: string;
  authorId: string;
  authorLabel: string;
  text: string;
  at: number;
  source: FieldSource;
}

export interface EmergencyReport {
  id: string;
  category: Category;
  priority: PriorityLevel;
  status: EmergencyStatus;
  description: string;
  location: GeoPoint | null;
  /** Operator-facing sector name resolved from the location grid. */
  sector: string | null;
  reportedAt: number;
  peopleAffected: number | null;
  vulnerabilities: Vulnerability[];
  assignedTeamId: string | null;
  mesh: MeshProvenance;
  notes: EmergencyNote[];
  audioClipId: string | null;
  evidenceIds: string[];
  /** Set when an operator marks the report invalid, with the stated reason. */
  invalidReason?: string;
  acknowledgedAt?: number;
  resolvedAt?: number;
}

/**
 * A priority score with its own arithmetic attached.
 *
 * The console never shows a bare number: every score ships the factors that
 * produced it so a commander can audit, and disagree with, the ranking.
 */
export interface PriorityFactor {
  label: string;
  detail: string;
  points: number;
}

export interface PriorityBreakdown {
  score: number;
  level: PriorityLevel;
  factors: PriorityFactor[];
  /** One-line plain-English summary, e.g. "Trapped · 3 people · 41 min". */
  summary: string;
}

// ---------------------------------------------------------------------------
// Rescue teams
// ---------------------------------------------------------------------------

export const TEAM_STATUSES = [
  'AVAILABLE',
  'ASSIGNED',
  'EN_ROUTE',
  'ON_SCENE',
  'RESCUE_IN_PROGRESS',
  'RETURNING',
  'OFFLINE',
] as const;
export type TeamStatus = (typeof TEAM_STATUSES)[number];

export type TeamCapability = 'MEDICAL' | 'EXTRACTION' | 'WATER_RESCUE' | 'FIRE' | 'LOGISTICS' | 'RECON';

export interface TeamMember {
  id: string;
  name: string;
  role: string;
}

export interface RescueTeam {
  id: string;
  name: string;
  callsign: string;
  members: TeamMember[];
  capabilities: TeamCapability[];
  /** Null when the team has not reported a position since the last sync. */
  location: GeoPoint | null;
  status: TeamStatus;
  currentMissionId: string | null;
  /** Optional telemetry. Absent means "not reported" — never assume 100%. */
  batteryPercent?: number;
  connectivity?: 'MESH_ONLY' | 'GATEWAY' | 'OFFLINE';
  lastSyncedAt: number | null;
  completedMissions: number;
}

// ---------------------------------------------------------------------------
// Missions
// ---------------------------------------------------------------------------

export const MISSION_STATUSES = [
  'ASSIGNED',
  'EN_ROUTE',
  'ON_SCENE',
  'RESCUE_IN_PROGRESS',
  'RESCUED',
  'COMPLETED',
  'ABORTED',
] as const;
export type MissionStatus = (typeof MISSION_STATUSES)[number];

export interface MissionStatusChange {
  status: MissionStatus;
  at: number;
  byLabel: string;
}

export interface RescueMission {
  id: string;
  emergencyId: string;
  teamId: string;
  status: MissionStatus;
  assignedAt: number;
  history: MissionStatusChange[];
  completedAt: number | null;
  /** Snapshot of the routing solution taken at assignment time. */
  routeId: string | null;
}

// ---------------------------------------------------------------------------
// Hospitals
// ---------------------------------------------------------------------------

export const HOSPITAL_STATUSES = ['OPEN', 'LIMITED', 'NEAR_CAPACITY', 'FULL', 'UNKNOWN'] as const;
export type HospitalStatus = (typeof HOSPITAL_STATUSES)[number];

export interface Hospital {
  id: string;
  name: string;
  location: GeoPoint;
  status: HospitalStatus;
  /** Every capacity field is optional: an unreachable hospital reports none. */
  totalBeds?: number;
  availableBeds?: number;
  emergencyBeds?: number;
  icuBeds?: number;
  traumaCapable?: boolean;
  ambulancesAvailable?: number;
  lastUpdated: number | null;
  source: FieldSource;
}

// ---------------------------------------------------------------------------
// Hazards
// ---------------------------------------------------------------------------

export const HAZARD_KINDS = ['BLOCKED_ROAD', 'FLOOD', 'FIRE', 'COLLAPSE', 'UNSAFE_AREA'] as const;
export type HazardKind = (typeof HAZARD_KINDS)[number];

export interface Hazard {
  id: string;
  kind: HazardKind;
  label: string;
  center: GeoPoint;
  /** Avoidance radius in metres. The router treats this as impassable. */
  radiusMeters: number;
  reportedAt: number;
  reportedBy: string;
  source: FieldSource;
  active: boolean;
}

// ---------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------

export interface RouteLeg {
  from: GeoPoint;
  to: GeoPoint;
  distanceMeters: number;
  /** Hazards this leg passes through. Non-empty means the leg is NOT clear. */
  hazardIds: string[];
}

/**
 * A computed path.
 *
 * `method` is surfaced verbatim in the UI. This console has no road network,
 * so it never claims to have produced a driving route.
 */
export interface RouteSolution {
  id: string;
  originLabel: string;
  destinationLabel: string;
  legs: RouteLeg[];
  distanceMeters: number;
  /** Null when no speed profile applies. */
  etaSeconds: number | null;
  method: 'DIRECT' | 'HAZARD_AVOIDING';
  /** True when every leg is clear of all active hazards. */
  clear: boolean;
  blockedByHazardIds: string[];
  /** Straight-line distance, for showing the detour cost of avoidance. */
  directDistanceMeters: number;
  /**
   * Hazards the origin or destination sits INSIDE.
   *
   * A distinct failure from "the way is blocked": when an endpoint is already
   * within a hazard zone no corridor can exist, and the commander needs to be
   * told that specifically rather than left reading a generic refusal.
   */
  originInHazardIds: string[];
  destinationInHazardIds: string[];
  computedAt: number;
}

// ---------------------------------------------------------------------------
// Clusters
// ---------------------------------------------------------------------------

export interface IncidentCluster {
  id: string;
  center: GeoPoint;
  /** Distance from the centre to the furthest member, in metres. */
  radiusMeters: number;
  emergencyIds: string[];
  peopleAffected: number;
  countsByCategory: Record<Category, number>;
  /** Highest priority score among members. */
  topScore: number;
  sector: string | null;
}

// ---------------------------------------------------------------------------
// Field reports
// ---------------------------------------------------------------------------

export type FieldReportKind = 'TEXT' | 'PHOTO' | 'AUDIO' | 'VIDEO' | 'NOTE';

export interface FieldReport {
  id: string;
  kind: FieldReportKind;
  title: string;
  body: string;
  reporterId: string;
  reporterLabel: string;
  teamId: string | null;
  emergencyId: string | null;
  location: GeoPoint | null;
  capturedAt: number;
  syncedAt: number | null;
  /** Seconds. Audio and video only. */
  durationSeconds?: number;
  /**
   * Demo builds carry no real media. The viewer renders a labelled
   * placeholder rather than a broken image, and says so.
   */
  mediaAvailable: boolean;
}

// ---------------------------------------------------------------------------
// Mesh network
// ---------------------------------------------------------------------------

export type MeshNodeKind = 'CIVILIAN' | 'RESCUER' | 'GATEWAY';
export type MeshNodeState = 'CONNECTED' | 'DISCONNECTED' | 'RECENTLY_SYNCED';

export interface MeshNode {
  id: string;
  kind: MeshNodeKind;
  state: MeshNodeState;
  /**
   * Null for the overwhelming majority of nodes: a relay does not report its
   * own position, and the console must not invent one.
   */
  location: GeoPoint | null;
  lastHeardAt: number | null;
  reportsContributed: number;
  deviceModel?: string;
}

/** An observed adjacency. Only recorded links appear — none are inferred. */
export interface MeshLink {
  fromId: string;
  toId: string;
  observedAt: number;
}

// ---------------------------------------------------------------------------
// Timeline & synchronisation
// ---------------------------------------------------------------------------

export type TimelineChannel = 'EMERGENCY' | 'TEAM' | 'ROUTE' | 'SYSTEM' | 'SYNC' | 'RESCUE';
export type TimelineSeverity = 'CRITICAL' | 'WARNING' | 'INFO' | 'SUCCESS';

export interface TimelineEvent {
  id: string;
  at: number;
  channel: TimelineChannel;
  severity: TimelineSeverity;
  title: string;
  detail?: string;
  emergencyId?: string;
  teamId?: string;
  source: FieldSource;
}

export interface SyncEvent {
  id: string;
  at: number;
  gatewayId: string;
  text: string;
  kind: 'GATEWAY' | 'UPLOAD' | 'SUCCESS' | 'FAILURE' | 'INFO';
  recordCount?: number;
}

// ---------------------------------------------------------------------------
// Decision support
// ---------------------------------------------------------------------------

export type RecommendationKind =
  | 'STALE_CRITICAL'
  | 'NEAREST_TEAM'
  | 'CLUSTER'
  | 'HOSPITAL_CAPACITY'
  | 'ROUTE_HAZARD'
  | 'TEAM_OVERLOADED'
  | 'UNASSIGNED_BACKLOG'
  | 'IDLE_TEAM';

export interface RecommendationAction {
  label: string;
  kind: 'VIEW_EMERGENCY' | 'VIEW_AREA' | 'ASSIGN_TEAM' | 'VIEW_TEAM' | 'VIEW_HOSPITAL' | 'VIEW_MAP';
  emergencyId?: string;
  teamId?: string;
  hospitalId?: string;
  clusterId?: string;
}

/**
 * One recommendation from the rules engine.
 *
 * `because` is mandatory. A recommendation that cannot explain itself does
 * not get shown — that constraint is what keeps this a decision-support tool
 * rather than an oracle.
 */
export interface OperationalRecommendation {
  id: string;
  kind: RecommendationKind;
  severity: TimelineSeverity;
  title: string;
  because: string[];
  suggestion: string;
  /** Sort weight. Higher surfaces first. */
  weight: number;
  actions: RecommendationAction[];
}

// ---------------------------------------------------------------------------
// The dataset
// ---------------------------------------------------------------------------

export interface OperationalDataset {
  emergencies: EmergencyReport[];
  teams: RescueTeam[];
  missions: RescueMission[];
  hospitals: Hospital[];
  hazards: Hazard[];
  fieldReports: FieldReport[];
  meshNodes: MeshNode[];
  meshLinks: MeshLink[];
  timeline: TimelineEvent[];
  syncEvents: SyncEvent[];
  /** Where the commander is sitting. Null when no position is available. */
  commandPost: NamedPlace | null;
  connection: ConnectionState;
  /** Human-readable name of the operation, e.g. "OP RIVERSIDE". */
  operationName: string;
}
