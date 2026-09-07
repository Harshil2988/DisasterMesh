'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  type ReactNode,
} from 'react';
import type {
  Category,
  DataMode,
  EmergencyReport,
  EmergencyStatus,
  GeoPoint,
  Hazard,
  HazardKind,
  IncidentCluster,
  MissionStatus,
  OperationalDataset,
  OperationalRecommendation,
  RescueMission,
  RouteSolution,
  TimelineChannel,
  TimelineEvent,
  TimelineSeverity,
} from '@/lib/types';
import { ApiDataProvider } from '@/lib/data/api-provider';
import { DemoDataProvider } from '@/lib/data/demo-provider';
import { sectorFor } from '@/lib/data/seed';
import { buildClusters } from '@/lib/clustering';
import { computeRoute } from '@/lib/routing';
import { scoreEmergency } from '@/lib/priority';
import { generateRecommendations, type ScoredEmergency } from '@/lib/recommendations';
import { etaSeconds, isUsablePoint, safeDistanceMeters } from '@/lib/geo';
import { MISSION_FLOW } from '@/lib/constants';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type SortKey = 'PRIORITY' | 'OLDEST' | 'NEWEST' | 'NEAREST' | 'PEOPLE';

export interface Filters {
  categories: Set<Category>;
  statuses: Set<EmergencyStatus>;
  search: string;
  sort: SortKey;
}

export interface MapLayers {
  emergencies: boolean;
  safe: boolean;
  teams: boolean;
  hospitals: boolean;
  hazards: boolean;
  clusters: boolean;
  route: boolean;
  meshNodes: boolean;
}

export interface Alert {
  id: string;
  severity: TimelineSeverity;
  title: string;
  detail?: string;
  at: number;
  emergencyId?: string;
}

/** Where distances are measured from, and whether that origin is real. */
export type OriginKind = 'DEVICE' | 'COMMAND_POST' | 'NONE';

interface OpsState {
  mode: DataMode;
  status: 'loading' | 'ready' | 'error';
  error: string | null;
  dataset: OperationalDataset | null;
  now: number;
  selectedEmergencyId: string | null;
  selectedTeamId: string | null;
  selectedHospitalId: string | null;
  selectedClusterId: string | null;
  route: RouteSolution | null;
  routeSubjectId: string | null;
  routeError: string | null;
  filters: Filters;
  layers: MapLayers;
  deviceLocation: GeoPoint | null;
  locationError: string | null;
  alerts: Alert[];
  /** Rises whenever the map should refit its viewport. */
  fitToken: number;
  fitTarget: 'ALL' | 'SELECTED' | 'CLUSTER' | null;
  demoCounter: number;
}

type Action =
  | { type: 'LOADING' }
  | { type: 'LOADED'; dataset: OperationalDataset; mode: DataMode }
  | { type: 'LOAD_FAILED'; error: string; mode: DataMode }
  | { type: 'TICK'; now: number }
  | { type: 'SELECT_EMERGENCY'; id: string | null }
  | { type: 'SELECT_TEAM'; id: string | null }
  | { type: 'SELECT_HOSPITAL'; id: string | null }
  | { type: 'SELECT_CLUSTER'; id: string | null }
  | { type: 'SET_ROUTE'; route: RouteSolution | null; subjectId: string | null; error: string | null }
  | { type: 'SET_FILTERS'; filters: Partial<Filters> }
  | { type: 'TOGGLE_CATEGORY'; category: Category }
  | { type: 'TOGGLE_STATUS'; status: EmergencyStatus }
  | { type: 'TOGGLE_LAYER'; layer: keyof MapLayers }
  | { type: 'SET_LOCATION'; location: GeoPoint | null; error: string | null }
  | { type: 'PUSH_ALERT'; alert: Alert }
  | { type: 'DISMISS_ALERT'; id: string }
  | { type: 'FIT'; target: 'ALL' | 'SELECTED' | 'CLUSTER' }
  | { type: 'MUTATE'; dataset: OperationalDataset; alert?: Alert };

// ---------------------------------------------------------------------------

const ALL_CATEGORIES: Category[] = ['CRITICAL', 'MEDICAL', 'WARNING', 'SUPPLY', 'SAFE'];
const ALL_STATUSES: EmergencyStatus[] = [
  'UNASSIGNED',
  'ACKNOWLEDGED',
  'ASSIGNED',
  'IN_PROGRESS',
  'RESCUED',
  'RESOLVED',
  'INVALID',
];

const initialState: OpsState = {
  mode: 'DEMO',
  status: 'loading',
  error: null,
  dataset: null,
  // Fixed until the client mounts, so server and client render the same tree.
  now: 0,
  selectedEmergencyId: null,
  selectedTeamId: null,
  selectedHospitalId: null,
  selectedClusterId: null,
  route: null,
  routeSubjectId: null,
  routeError: null,
  filters: {
    categories: new Set(ALL_CATEGORIES),
    statuses: new Set(ALL_STATUSES),
    search: '',
    sort: 'PRIORITY',
  },
  layers: {
    emergencies: true,
    safe: true,
    teams: true,
    hospitals: true,
    hazards: true,
    clusters: true,
    route: true,
    meshNodes: false,
  },
  deviceLocation: null,
  locationError: null,
  alerts: [],
  fitToken: 0,
  fitTarget: null,
  demoCounter: 0,
};

function reducer(state: OpsState, action: Action): OpsState {
  switch (action.type) {
    case 'LOADING':
      return { ...state, status: 'loading', error: null };

    case 'LOADED':
      return {
        ...state,
        status: 'ready',
        error: null,
        dataset: action.dataset,
        mode: action.mode,
        now: Date.now(),
        // A mode switch must not leave a selection pointing at the other
        // dataset's records.
        selectedEmergencyId: null,
        selectedTeamId: null,
        route: null,
        routeSubjectId: null,
      };

    case 'LOAD_FAILED':
      return { ...state, status: 'error', error: action.error, dataset: null, mode: action.mode };

    case 'TICK':
      return { ...state, now: action.now };

    case 'SELECT_EMERGENCY':
      return {
        ...state,
        selectedEmergencyId: action.id,
        selectedClusterId: null,
        // A route belongs to the incident it was computed for.
        route: state.routeSubjectId === action.id ? state.route : null,
        routeSubjectId: state.routeSubjectId === action.id ? state.routeSubjectId : null,
        routeError: null,
      };

    case 'SELECT_TEAM':
      return { ...state, selectedTeamId: action.id };

    case 'SELECT_HOSPITAL':
      return { ...state, selectedHospitalId: action.id };

    case 'SELECT_CLUSTER':
      return { ...state, selectedClusterId: action.id };

    case 'SET_ROUTE':
      return {
        ...state,
        route: action.route,
        routeSubjectId: action.subjectId,
        routeError: action.error,
      };

    case 'SET_FILTERS':
      return { ...state, filters: { ...state.filters, ...action.filters } };

    case 'TOGGLE_CATEGORY': {
      const next = new Set(state.filters.categories);
      if (next.has(action.category)) next.delete(action.category);
      else next.add(action.category);
      return { ...state, filters: { ...state.filters, categories: next } };
    }

    case 'TOGGLE_STATUS': {
      const next = new Set(state.filters.statuses);
      if (next.has(action.status)) next.delete(action.status);
      else next.add(action.status);
      return { ...state, filters: { ...state.filters, statuses: next } };
    }

    case 'TOGGLE_LAYER':
      return { ...state, layers: { ...state.layers, [action.layer]: !state.layers[action.layer] } };

    case 'SET_LOCATION':
      return { ...state, deviceLocation: action.location, locationError: action.error };

    case 'PUSH_ALERT':
      return { ...state, alerts: [action.alert, ...state.alerts].slice(0, 4) };

    case 'DISMISS_ALERT':
      return { ...state, alerts: state.alerts.filter((a) => a.id !== action.id) };

    case 'FIT':
      return { ...state, fitToken: state.fitToken + 1, fitTarget: action.target };

    case 'MUTATE':
      return {
        ...state,
        dataset: action.dataset,
        demoCounter: state.demoCounter + 1,
        alerts: action.alert ? [action.alert, ...state.alerts].slice(0, 4) : state.alerts,
      };

    default:
      return state;
  }
}

// ---------------------------------------------------------------------------
// Derived operating picture
// ---------------------------------------------------------------------------

export interface OperationalStats {
  critical: number;
  medical: number;
  warning: number;
  supply: number;
  safe: number;
  peopleTracked: number;
  activeTeams: number;
  availableTeams: number;
  meshNodes: number;
  rescued: number;
  pending: number;
  unassigned: number;
  reportsSynchronised: number;
  openIncidents: number;
  resolvedIncidents: number;
}

export interface DerivedOps {
  emergencies: ScoredEmergency[];
  byId: Map<string, ScoredEmergency>;
  filtered: ScoredEmergency[];
  clusters: IncidentCluster[];
  recommendations: OperationalRecommendation[];
  stats: OperationalStats;
  /** Distance origin actually in use, with its honesty label. */
  origin: { point: GeoPoint | null; kind: OriginKind; label: string };
}

// ---------------------------------------------------------------------------

interface OpsContextValue extends OpsState {
  derived: DerivedOps;
  selected: ScoredEmergency | null;
  setMode: (mode: DataMode) => void;
  reload: () => void;
  selectEmergency: (id: string | null) => void;
  selectTeam: (id: string | null) => void;
  selectHospital: (id: string | null) => void;
  selectCluster: (id: string | null) => void;
  setFilters: (filters: Partial<Filters>) => void;
  toggleCategory: (category: Category) => void;
  toggleStatus: (status: EmergencyStatus) => void;
  toggleLayer: (layer: keyof MapLayers) => void;
  resetFilters: () => void;
  fit: (target: 'ALL' | 'SELECTED' | 'CLUSTER') => void;
  dismissAlert: (id: string) => void;
  requestDeviceLocation: () => void;
  // operations
  assignTeam: (emergencyId: string, teamId: string) => void;
  unassign: (emergencyId: string) => void;
  setEmergencyStatus: (emergencyId: string, status: EmergencyStatus, reason?: string) => void;
  advanceMission: (missionId: string, status: MissionStatus) => void;
  addNote: (emergencyId: string, text: string) => void;
  calculateRoute: (emergencyId: string, teamId?: string | null) => void;
  clearRoute: () => void;
  addHazard: (kind: HazardKind, label: string, center: GeoPoint, radiusMeters: number) => void;
  toggleHazard: (hazardId: string) => void;
  // demo controls
  demoGenerateReport: (category: Category) => void;
  demoSimulateSync: () => void;
  demoCompleteRescue: () => void;
}

const OpsContext = createContext<OpsContextValue | null>(null);

export function useOps(): OpsContextValue {
  const ctx = useContext(OpsContext);
  if (!ctx) throw new Error('useOps must be used inside <OpsProvider>');
  return ctx;
}

// ---------------------------------------------------------------------------

let idSeq = 0;
const uid = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${idSeq++}`;

export function OpsProvider({ children }: { children: ReactNode }): React.JSX.Element {
  const [state, dispatch] = useReducer(reducer, initialState);
  const modeRef = useRef<DataMode>('DEMO');

  const load = useCallback((mode: DataMode) => {
    modeRef.current = mode;
    dispatch({ type: 'LOADING' });
    const provider = mode === 'DEMO' ? new DemoDataProvider() : new ApiDataProvider();
    void provider.load().then((result) => {
      // A mode switch that lands after a slower one must not overwrite it.
      if (modeRef.current !== mode) return;
      if (result.dataset) dispatch({ type: 'LOADED', dataset: result.dataset, mode });
      else dispatch({ type: 'LOAD_FAILED', error: result.error ?? 'Unknown error', mode });
    });
  }, []);

  useEffect(() => {
    load('DEMO');
  }, [load]);

  // Operational clock. 20 s is frequent enough for waiting times to feel live
  // and slow enough that the whole tree is not re-derived constantly.
  useEffect(() => {
    const timer = window.setInterval(() => dispatch({ type: 'TICK', now: Date.now() }), 20_000);
    return () => window.clearInterval(timer);
  }, []);

  // -------------------------------------------------------------------------
  // Derivation
  // -------------------------------------------------------------------------

  const derived = useMemo<DerivedOps>(() => {
    const dataset = state.dataset;
    // `now` is stamped by LOADED and refreshed by TICK, so it is always set
    // by the time a dataset exists. Reading the clock during render instead
    // would make this memo non-idempotent.
    const now = state.now;

    const empty: DerivedOps = {
      emergencies: [],
      byId: new Map(),
      filtered: [],
      clusters: [],
      recommendations: [],
      stats: {
        critical: 0, medical: 0, warning: 0, supply: 0, safe: 0,
        peopleTracked: 0, activeTeams: 0, availableTeams: 0, meshNodes: 0,
        rescued: 0, pending: 0, unassigned: 0, reportsSynchronised: 0,
        openIncidents: 0, resolvedIncidents: 0,
      },
      origin: { point: null, kind: 'NONE', label: 'No position available' },
    };

    if (!dataset) return empty;

    // --- origin for every distance in the console --------------------------
    const origin: DerivedOps['origin'] = state.deviceLocation
      ? { point: state.deviceLocation, kind: 'DEVICE', label: 'This device' }
      : dataset.commandPost
        ? { point: dataset.commandPost, kind: 'COMMAND_POST', label: dataset.commandPost.label }
        : { point: null, kind: 'NONE', label: 'No position available' };

    // --- scoring ------------------------------------------------------------
    const emergencies: ScoredEmergency[] = dataset.emergencies.map((e) => {
      const breakdown = scoreEmergency(e, now);
      return {
        ...e,
        priority: breakdown.level,
        breakdown,
        distanceFromCommand: safeDistanceMeters(origin.point, e.location),
      };
    });

    const byId = new Map(emergencies.map((e) => [e.id, e]));

    // --- clustering ---------------------------------------------------------
    const clusters = buildClusters(emergencies, { sectorOf: sectorFor }).map((cluster) => ({
      ...cluster,
      topScore: Math.max(
        0,
        ...cluster.emergencyIds.map((id) => byId.get(id)?.breakdown.score ?? 0),
      ),
    }));

    // --- filtering & sorting ------------------------------------------------
    const query = state.filters.search.trim().toLowerCase();
    const teamNameById = new Map(dataset.teams.map((t) => [t.id, t.name.toLowerCase()]));

    const filtered = emergencies
      .filter((e) => state.filters.categories.has(e.category))
      .filter((e) => state.filters.statuses.has(e.status))
      .filter((e) => {
        if (!query) return true;
        const haystack = [
          e.id,
          e.category,
          e.status,
          e.priority,
          e.description,
          e.sector ?? '',
          e.mesh.senderNodeId,
          e.assignedTeamId ? (teamNameById.get(e.assignedTeamId) ?? '') : '',
        ]
          .join(' ')
          .toLowerCase();
        return haystack.includes(query);
      })
      .sort((a, b) => {
        switch (state.filters.sort) {
          case 'OLDEST':
            return a.reportedAt - b.reportedAt;
          case 'NEWEST':
            return b.reportedAt - a.reportedAt;
          case 'NEAREST': {
            const da = a.distanceFromCommand ?? Infinity;
            const db = b.distanceFromCommand ?? Infinity;
            return da - db;
          }
          case 'PEOPLE':
            return (b.peopleAffected ?? 0) - (a.peopleAffected ?? 0);
          case 'PRIORITY':
          default:
            return b.breakdown.score - a.breakdown.score || a.reportedAt - b.reportedAt;
        }
      });

    // --- statistics ---------------------------------------------------------
    const openStatuses: EmergencyStatus[] = ['UNASSIGNED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS'];
    const isOpen = (e: EmergencyReport): boolean => openStatuses.includes(e.status);

    const stats: OperationalStats = {
      critical: emergencies.filter((e) => e.category === 'CRITICAL' && isOpen(e)).length,
      medical: emergencies.filter((e) => e.category === 'MEDICAL' && isOpen(e)).length,
      warning: emergencies.filter((e) => e.category === 'WARNING' && isOpen(e)).length,
      supply: emergencies.filter((e) => e.category === 'SUPPLY' && isOpen(e)).length,
      safe: emergencies.filter((e) => e.category === 'SAFE').length,
      peopleTracked: emergencies.reduce((sum, e) => sum + (e.peopleAffected ?? 0), 0),
      activeTeams: dataset.teams.filter((t) => t.status !== 'OFFLINE' && t.status !== 'AVAILABLE').length,
      availableTeams: dataset.teams.filter((t) => t.status === 'AVAILABLE').length,
      meshNodes: dataset.meshNodes.length,
      rescued: emergencies.filter((e) => e.status === 'RESCUED').length,
      pending: emergencies.filter(isOpen).length,
      unassigned: emergencies.filter((e) => e.status === 'UNASSIGNED').length,
      reportsSynchronised: dataset.syncEvents.reduce((sum, s) => sum + (s.recordCount ?? 0), 0),
      openIncidents: emergencies.filter(isOpen).length,
      resolvedIncidents: emergencies.filter((e) => e.status === 'RESOLVED' || e.status === 'RESCUED').length,
    };

    // --- decision support ---------------------------------------------------
    const recommendations = generateRecommendations({
      now,
      emergencies,
      teams: dataset.teams,
      missions: dataset.missions,
      hospitals: dataset.hospitals,
      hazards: dataset.hazards,
      clusters,
    });

    return { emergencies, byId, filtered, clusters, recommendations, stats, origin };
  }, [state.dataset, state.now, state.filters, state.deviceLocation]);

  const selected = state.selectedEmergencyId
    ? (derived.byId.get(state.selectedEmergencyId) ?? null)
    : null;

  // -------------------------------------------------------------------------
  // Mutations. Every one records a timeline entry.
  // -------------------------------------------------------------------------

  const mutate = useCallback(
    (
      fn: (dataset: OperationalDataset) => {
        dataset: OperationalDataset;
        event?: Omit<TimelineEvent, 'id' | 'at'> & { at?: number };
        alert?: Omit<Alert, 'id' | 'at'>;
      },
    ) => {
      // Applied against the latest dataset, then dispatched as one atomic
      // replacement — never via a loading state, which would blank the
      // operating picture every time a commander pressed a button.
      const current = state.dataset;
      if (!current) return;

      const result = fn(current);
      const at = Date.now();
      const next: OperationalDataset = result.event
        ? {
            ...result.dataset,
            timeline: [{ ...result.event, id: uid('TL'), at: result.event.at ?? at }, ...result.dataset.timeline],
          }
        : result.dataset;

      dispatch({
        type: 'MUTATE',
        dataset: next,
        alert: result.alert ? { ...result.alert, id: uid('AL'), at } : undefined,
      });
    },
    [state.dataset],
  );

  const teamName = useCallback(
    (teamId: string | null | undefined): string =>
      state.dataset?.teams.find((t) => t.id === teamId)?.name ?? 'Unknown team',
    [state.dataset],
  );

  const assignTeam = useCallback(
    (emergencyId: string, teamId: string) => {
      mutate((dataset) => {
        const emergency = dataset.emergencies.find((e) => e.id === emergencyId);
        const team = dataset.teams.find((t) => t.id === teamId);
        if (!emergency || !team) return { dataset };

        const missionId = uid('MSN');
        const at = Date.now();

        const mission: RescueMission = {
          id: missionId,
          emergencyId,
          teamId,
          status: 'ASSIGNED',
          assignedAt: at,
          history: [{ status: 'ASSIGNED', at, byLabel: 'Cdr. Operations' }],
          completedAt: null,
          routeId: null,
        };

        return {
          dataset: {
            ...dataset,
            emergencies: dataset.emergencies.map((e) =>
              e.id === emergencyId ? { ...e, assignedTeamId: teamId, status: 'ASSIGNED' as const } : e,
            ),
            teams: dataset.teams.map((t) =>
              t.id === teamId ? { ...t, status: 'ASSIGNED' as const, currentMissionId: missionId } : t,
            ),
            missions: [...dataset.missions, mission],
          },
          event: {
            channel: 'RESCUE',
            severity: 'INFO',
            title: `${team.name} assigned to ${emergencyId}`,
            detail: emergency.description,
            emergencyId,
            teamId,
            source: 'command',
          },
          alert: {
            severity: 'SUCCESS',
            title: `${team.name} assigned`,
            detail: `${emergencyId} — ${emergency.sector ?? 'position unknown'}`,
            emergencyId,
          },
        };
      });
    },
    [mutate],
  );

  const unassign = useCallback(
    (emergencyId: string) => {
      mutate((dataset) => {
        const emergency = dataset.emergencies.find((e) => e.id === emergencyId);
        if (!emergency?.assignedTeamId) return { dataset };
        const teamId = emergency.assignedTeamId;

        return {
          dataset: {
            ...dataset,
            emergencies: dataset.emergencies.map((e) =>
              e.id === emergencyId ? { ...e, assignedTeamId: null, status: 'UNASSIGNED' as const } : e,
            ),
            teams: dataset.teams.map((t) =>
              t.id === teamId ? { ...t, status: 'AVAILABLE' as const, currentMissionId: null } : t,
            ),
            missions: dataset.missions.map((m) =>
              m.emergencyId === emergencyId && m.status !== 'COMPLETED'
                ? { ...m, status: 'ABORTED' as const, completedAt: Date.now() }
                : m,
            ),
          },
          event: {
            channel: 'RESCUE',
            severity: 'WARNING',
            title: `Assignment withdrawn — ${emergencyId}`,
            detail: `${teamName(teamId)} released and returned to available.`,
            emergencyId,
            teamId,
            source: 'command',
          },
        };
      });
    },
    [mutate, teamName],
  );

  const setEmergencyStatus = useCallback(
    (emergencyId: string, status: EmergencyStatus, reason?: string) => {
      mutate((dataset) => {
        const emergency = dataset.emergencies.find((e) => e.id === emergencyId);
        if (!emergency) return { dataset };
        const at = Date.now();
        const closing = status === 'RESCUED' || status === 'RESOLVED' || status === 'INVALID';

        return {
          dataset: {
            ...dataset,
            emergencies: dataset.emergencies.map((e) =>
              e.id === emergencyId
                ? {
                    ...e,
                    status,
                    invalidReason: status === 'INVALID' ? reason : e.invalidReason,
                    acknowledgedAt: status === 'ACKNOWLEDGED' ? at : e.acknowledgedAt,
                    resolvedAt: closing ? at : e.resolvedAt,
                  }
                : e,
            ),
            teams: closing
              ? dataset.teams.map((t) =>
                  t.id === emergency.assignedTeamId
                    ? { ...t, status: 'AVAILABLE' as const, currentMissionId: null, completedMissions: t.completedMissions + 1 }
                    : t,
                )
              : dataset.teams,
            missions: closing
              ? dataset.missions.map((m) =>
                  m.emergencyId === emergencyId && m.status !== 'COMPLETED'
                    ? { ...m, status: 'COMPLETED' as const, completedAt: at }
                    : m,
                )
              : dataset.missions,
          },
          event: {
            channel: status === 'RESCUED' ? 'RESCUE' : 'EMERGENCY',
            severity: status === 'RESCUED' ? 'SUCCESS' : status === 'INVALID' ? 'INFO' : 'INFO',
            title: `${emergencyId} marked ${status.replace('_', ' ').toLowerCase()}`,
            detail: reason ?? emergency.description,
            emergencyId,
            teamId: emergency.assignedTeamId ?? undefined,
            source: 'command',
          },
          alert:
            status === 'RESCUED'
              ? { severity: 'SUCCESS', title: `${emergencyId} — people rescued`, detail: emergency.sector ?? undefined, emergencyId }
              : undefined,
        };
      });
    },
    [mutate],
  );

  const advanceMission = useCallback(
    (missionId: string, status: MissionStatus) => {
      mutate((dataset) => {
        const mission = dataset.missions.find((m) => m.id === missionId);
        if (!mission) return { dataset };
        const at = Date.now();

        const teamStatus =
          status === 'EN_ROUTE'
            ? ('EN_ROUTE' as const)
            : status === 'ON_SCENE'
              ? ('ON_SCENE' as const)
              : status === 'RESCUE_IN_PROGRESS'
                ? ('RESCUE_IN_PROGRESS' as const)
                : status === 'RESCUED' || status === 'COMPLETED'
                  ? ('AVAILABLE' as const)
                  : ('ASSIGNED' as const);

        const finished = status === 'COMPLETED' || status === 'RESCUED';

        return {
          dataset: {
            ...dataset,
            missions: dataset.missions.map((m) =>
              m.id === missionId
                ? {
                    ...m,
                    status,
                    completedAt: finished ? at : null,
                    history: [...m.history, { status, at, byLabel: teamName(m.teamId) }],
                  }
                : m,
            ),
            teams: dataset.teams.map((t) =>
              t.id === mission.teamId
                ? {
                    ...t,
                    status: teamStatus,
                    currentMissionId: finished ? null : missionId,
                    completedMissions: finished ? t.completedMissions + 1 : t.completedMissions,
                  }
                : t,
            ),
            emergencies: dataset.emergencies.map((e) =>
              e.id === mission.emergencyId
                ? {
                    ...e,
                    status:
                      status === 'RESCUED' || status === 'COMPLETED'
                        ? ('RESCUED' as const)
                        : status === 'ASSIGNED'
                          ? ('ASSIGNED' as const)
                          : ('IN_PROGRESS' as const),
                    resolvedAt: finished ? at : e.resolvedAt,
                  }
                : e,
            ),
          },
          event: {
            channel: 'TEAM',
            severity: finished ? 'SUCCESS' : 'INFO',
            title: `${teamName(mission.teamId)} — ${status.replace(/_/g, ' ').toLowerCase()}`,
            detail: `Mission ${mission.id} on ${mission.emergencyId}.`,
            emergencyId: mission.emergencyId,
            teamId: mission.teamId,
            source: 'rescuer',
          },
          alert: finished
            ? { severity: 'SUCCESS', title: `${teamName(mission.teamId)} completed ${mission.emergencyId}` }
            : { severity: 'INFO', title: `${teamName(mission.teamId)} — ${status.replace(/_/g, ' ').toLowerCase()}` },
        };
      });
    },
    [mutate, teamName],
  );

  const addNote = useCallback(
    (emergencyId: string, text: string) => {
      const trimmed = text.trim();
      if (!trimmed) return;
      mutate((dataset) => ({
        dataset: {
          ...dataset,
          emergencies: dataset.emergencies.map((e) =>
            e.id === emergencyId
              ? {
                  ...e,
                  notes: [
                    ...e.notes,
                    {
                      id: uid('NOTE'),
                      authorId: 'CMD-1',
                      authorLabel: 'Cdr. Operations',
                      text: trimmed,
                      at: Date.now(),
                      source: 'command' as const,
                    },
                  ],
                }
              : e,
          ),
        },
        event: {
          channel: 'EMERGENCY',
          severity: 'INFO',
          title: `Note added to ${emergencyId}`,
          detail: trimmed,
          emergencyId,
          source: 'command',
        },
      }));
    },
    [mutate],
  );

  const calculateRoute = useCallback(
    (emergencyId: string, teamId?: string | null) => {
      const dataset = state.dataset;
      if (!dataset) return;

      const emergency = dataset.emergencies.find((e) => e.id === emergencyId);
      if (!emergency?.location) {
        dispatch({
          type: 'SET_ROUTE',
          route: null,
          subjectId: emergencyId,
          error: 'Route could not be calculated — this report carries no coordinates.',
        });
        return;
      }

      const team = teamId ? dataset.teams.find((t) => t.id === teamId) : null;
      const originPoint = team?.location ?? derived.origin.point;
      const originLabel = team ? team.name : derived.origin.label;

      if (!isUsablePoint(originPoint)) {
        dispatch({
          type: 'SET_ROUTE',
          route: null,
          subjectId: emergencyId,
          error: team
            ? `Route could not be calculated — ${team.name} has not reported a position.`
            : 'Route could not be calculated — no origin position is available.',
        });
        return;
      }

      const route = computeRoute({
        origin: originPoint,
        destination: emergency.location,
        originLabel,
        destinationLabel: `${emergencyId} — ${emergency.sector ?? 'position'}`,
        hazards: dataset.hazards,
      });

      dispatch({
        type: 'SET_ROUTE',
        route,
        subjectId: emergencyId,
        error: route ? null : 'Route could not be calculated.',
      });

      if (route) {
        mutate((d) => ({
          dataset: d,
          event: {
            channel: 'ROUTE',
            severity: route.clear ? 'INFO' : 'WARNING',
            title: route.clear
              ? `Route computed — ${originLabel} to ${emergencyId}`
              : `No clear corridor — ${originLabel} to ${emergencyId}`,
            detail: route.clear
              ? `${(route.distanceMeters / 1000).toFixed(1)} km via ${
                  route.method === 'HAZARD_AVOIDING' ? 'hazard-avoiding corridor' : 'direct corridor'
                }.`
              : `Direct line crosses ${route.blockedByHazardIds.length} active hazard zone(s).`,
            emergencyId,
            teamId: team?.id,
            source: 'derived',
          },
        }));
      }
    },
    [state.dataset, derived.origin, mutate],
  );

  const clearRoute = useCallback(() => {
    dispatch({ type: 'SET_ROUTE', route: null, subjectId: null, error: null });
  }, []);

  const addHazard = useCallback(
    (kind: HazardKind, label: string, center: GeoPoint, radiusMeters: number) => {
      mutate((dataset) => {
        const hazard: Hazard = {
          id: uid('HZ'),
          kind,
          label,
          center,
          radiusMeters,
          reportedAt: Date.now(),
          reportedBy: 'Cdr. Operations',
          source: 'command',
          active: true,
        };
        return {
          dataset: { ...dataset, hazards: [...dataset.hazards, hazard] },
          event: {
            channel: 'SYSTEM',
            severity: 'WARNING',
            title: `Hazard recorded — ${label}`,
            detail: `Avoidance radius ${radiusMeters} m. Routing will detour around this zone.`,
            source: 'command',
          },
          alert: { severity: 'WARNING', title: 'Hazard recorded', detail: label },
        };
      });
    },
    [mutate],
  );

  const toggleHazard = useCallback(
    (hazardId: string) => {
      mutate((dataset) => {
        const hazard = dataset.hazards.find((h) => h.id === hazardId);
        if (!hazard) return { dataset };
        return {
          dataset: {
            ...dataset,
            hazards: dataset.hazards.map((h) => (h.id === hazardId ? { ...h, active: !h.active } : h)),
          },
          event: {
            channel: 'SYSTEM',
            severity: 'INFO',
            title: `${hazard.label} marked ${hazard.active ? 'cleared' : 'active'}`,
            source: 'command',
          },
        };
      });
    },
    [mutate],
  );

  // --- demonstration controls ---------------------------------------------

  const demoGenerateReport = useCallback(
    (category: Category) => {
      mutate((dataset) => {
        const anchor = dataset.emergencies.find((e) => e.location)?.location;
        if (!anchor) return { dataset };

        const jitter = (): number => (Math.random() - 0.5) * 0.006;
        const location: GeoPoint = {
          latitude: anchor.latitude + jitter(),
          longitude: anchor.longitude + jitter(),
        };
        const at = Date.now();
        const id = `DM-${9000 + dataset.emergencies.length}`;

        const descriptions: Record<Category, string> = {
          CRITICAL: 'SOS raised — person trapped, unable to move.',
          MEDICAL: 'Medical assistance requested — injury reported.',
          WARNING: 'Hazard reported in the area.',
          SUPPLY: 'Supplies requested — drinking water.',
          SAFE: 'Check-in received — no assistance needed.',
        };

        const report: EmergencyReport = {
          id,
          category,
          priority: 'P3',
          status: 'UNASSIGNED',
          description: descriptions[category],
          location,
          sector: sectorFor(location),
          reportedAt: at,
          peopleAffected: category === 'SAFE' ? 4 : category === 'CRITICAL' ? 2 : 1,
          vulnerabilities: category === 'CRITICAL' ? ['TRAPPED'] : [],
          assignedTeamId: null,
          mesh: {
            senderNodeId: `NODE-${1000 + Math.floor(Math.random() * 8999)}`,
            hopCount: 1 + Math.floor(Math.random() * 4),
            originTimestamp: at,
            collectedByRescuerId: 'RSC-01',
            lastSyncedAt: at,
            pathRecorded: false,
          },
          notes: [],
          audioClipId: category === 'CRITICAL' ? uid('AUD') : null,
          evidenceIds: [],
        };

        return {
          dataset: { ...dataset, emergencies: [report, ...dataset.emergencies] },
          event: {
            channel: 'EMERGENCY',
            severity: category === 'CRITICAL' ? 'CRITICAL' : 'INFO',
            title: `${category === 'CRITICAL' ? 'SOS' : category} report received — ${id}`,
            detail: report.description,
            emergencyId: id,
            source: 'mesh',
          },
          alert: {
            severity: category === 'CRITICAL' ? 'CRITICAL' : 'INFO',
            title: category === 'CRITICAL' ? 'New critical SOS received' : `New ${category.toLowerCase()} report`,
            detail: `${id} — ${report.sector ?? 'position unknown'}`,
            emergencyId: id,
          },
        };
      });
    },
    [mutate],
  );

  const demoSimulateSync = useCallback(() => {
    mutate((dataset) => {
      const at = Date.now();
      const count = 3 + Math.floor(Math.random() * 9);
      const gatewayId = dataset.connection.activeGatewayId ?? 'GW-01';

      return {
        dataset: {
          ...dataset,
          syncEvents: [
            { id: uid('SY'), at, gatewayId, text: `${count} reports synchronised`, kind: 'SUCCESS' as const, recordCount: count },
            { id: uid('SY'), at: at - 1000, gatewayId, text: `Gateway ${gatewayId} connected to internet`, kind: 'GATEWAY' as const },
            ...dataset.syncEvents,
          ],
          connection: { ...dataset.connection, lastSyncedAt: at, pendingUploads: Math.max(0, dataset.connection.pendingUploads - 1) },
        },
        event: {
          channel: 'SYNC',
          severity: 'SUCCESS',
          title: `${count} reports synchronised`,
          detail: `Uploaded from gateway ${gatewayId}.`,
          source: 'rescuer',
        },
        alert: { severity: 'INFO', title: `${count} reports synchronised`, detail: `Gateway ${gatewayId}` },
      };
    });
  }, [mutate]);

  const demoCompleteRescue = useCallback(() => {
    const dataset = state.dataset;
    if (!dataset) return;
    const active = dataset.missions.find((m) => m.status !== 'COMPLETED' && m.status !== 'ABORTED');
    if (!active) return;
    const currentIndex = MISSION_FLOW.indexOf(active.status);
    const next = MISSION_FLOW[Math.min(MISSION_FLOW.length - 1, currentIndex + 1)];
    advanceMission(active.id, next);
  }, [state.dataset, advanceMission]);

  // --- device location -----------------------------------------------------

  const requestDeviceLocation = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      dispatch({
        type: 'SET_LOCATION',
        location: null,
        error: 'This browser does not provide a geolocation API.',
      });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        dispatch({
          type: 'SET_LOCATION',
          location: {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            timestamp: position.timestamp,
          },
          error: null,
        });
      },
      (error) => {
        dispatch({
          type: 'SET_LOCATION',
          location: null,
          error:
            error.code === error.PERMISSION_DENIED
              ? 'Location permission denied — distances are measured from the command post instead.'
              : 'Current location unavailable.',
        });
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  }, []);

  // -------------------------------------------------------------------------

  const value = useMemo<OpsContextValue>(
    () => ({
      ...state,
      derived,
      selected,
      setMode: (mode) => load(mode),
      reload: () => load(state.mode),
      selectEmergency: (id) => dispatch({ type: 'SELECT_EMERGENCY', id }),
      selectTeam: (id) => dispatch({ type: 'SELECT_TEAM', id }),
      selectHospital: (id) => dispatch({ type: 'SELECT_HOSPITAL', id }),
      selectCluster: (id) => dispatch({ type: 'SELECT_CLUSTER', id }),
      setFilters: (filters) => dispatch({ type: 'SET_FILTERS', filters }),
      toggleCategory: (category) => dispatch({ type: 'TOGGLE_CATEGORY', category }),
      toggleStatus: (status) => dispatch({ type: 'TOGGLE_STATUS', status }),
      toggleLayer: (layer) => dispatch({ type: 'TOGGLE_LAYER', layer }),
      resetFilters: () =>
        dispatch({
          type: 'SET_FILTERS',
          filters: {
            categories: new Set(ALL_CATEGORIES),
            statuses: new Set(ALL_STATUSES),
            search: '',
            sort: 'PRIORITY',
          },
        }),
      fit: (target) => dispatch({ type: 'FIT', target }),
      dismissAlert: (id) => dispatch({ type: 'DISMISS_ALERT', id }),
      requestDeviceLocation,
      assignTeam,
      unassign,
      setEmergencyStatus,
      advanceMission,
      addNote,
      calculateRoute,
      clearRoute,
      addHazard,
      toggleHazard,
      demoGenerateReport,
      demoSimulateSync,
      demoCompleteRescue,
    }),
    [
      state, derived, selected, load, requestDeviceLocation, assignTeam, unassign,
      setEmergencyStatus, advanceMission, addNote, calculateRoute, clearRoute,
      addHazard, toggleHazard, demoGenerateReport, demoSimulateSync, demoCompleteRescue,
    ],
  );

  return <OpsContext.Provider value={value}>{children}</OpsContext.Provider>;
}

// ---------------------------------------------------------------------------
// Shared helpers for screens
// ---------------------------------------------------------------------------

/** Distance and ETA from a team to an incident. Null when either lacks a fix. */
export function teamToIncident(
  teamLocation: GeoPoint | null,
  incidentLocation: GeoPoint | null,
): { meters: number | null; seconds: number | null } {
  const meters = safeDistanceMeters(teamLocation, incidentLocation);
  return { meters, seconds: etaSeconds(meters) };
}

export const TIMELINE_CHANNELS: TimelineChannel[] = [
  'EMERGENCY',
  'TEAM',
  'ROUTE',
  'RESCUE',
  'SYNC',
  'SYSTEM',
];
