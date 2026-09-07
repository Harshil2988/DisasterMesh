import type {
  Category,
  EmergencyStatus,
  HazardKind,
  HospitalStatus,
  MissionStatus,
  PriorityLevel,
  TeamStatus,
  TimelineChannel,
} from './types';

/**
 * Presentation metadata for every enumerated value in the domain.
 *
 * Centralised for one reason above all: a category must look identical on a
 * map pin, a list row, a chart series and a filter chip, or an operator has
 * to relearn the colour code on every screen.
 *
 * Each category carries BOTH a mark colour and a text colour. The mark is for
 * fills and strokes (WCAG 3:1 for non-text); the text tint is for labels
 * (4.5:1). `#8B5CF6` measured 4.39:1 on the panel surface and so is used only
 * as a mark, with `#A78BFA` for its text.
 */

export interface CategoryMeta {
  id: Category;
  label: string;
  /** Short form for dense rows and chart axes. */
  short: string;
  mark: string;
  text: string;
  /** Tailwind-safe rgb triplet for translucent fills. */
  rgb: string;
  description: string;
  examples: string[];
}

export const CATEGORY_META: Record<Category, CategoryMeta> = {
  CRITICAL: {
    id: 'CRITICAL',
    label: 'Critical / SOS',
    short: 'SOS',
    mark: '#ef4444',
    text: '#f87171',
    rgb: '239 68 68',
    description: 'Life-threatening. Immediate danger to a person right now.',
    examples: ['Trapped under debris', 'Life-threatening injury', 'Immediate danger'],
  },
  MEDICAL: {
    id: 'MEDICAL',
    label: 'Medical',
    short: 'MED',
    mark: '#f97316',
    text: '#fb923c',
    rgb: '249 115 22',
    description: 'Injury or illness requiring medical attention.',
    examples: ['Injury', 'Illness', 'Medical assistance needed'],
  },
  WARNING: {
    id: 'WARNING',
    label: 'Warning',
    short: 'WARN',
    mark: '#f59e0b',
    text: '#fbbf24',
    rgb: '245 158 11',
    description: 'Hazard affecting an area rather than a named individual.',
    examples: ['Fire', 'Flood', 'Building damage', 'Structural hazard'],
  },
  SUPPLY: {
    id: 'SUPPLY',
    label: 'Supply',
    short: 'SUP',
    mark: '#3b82f6',
    text: '#60a5fa',
    rgb: '59 130 246',
    description: 'Request for materials or equipment.',
    examples: ['Water', 'Food', 'Medicine', 'Equipment'],
  },
  SAFE: {
    id: 'SAFE',
    label: 'Safe',
    short: 'SAFE',
    mark: '#22c55e',
    text: '#4ade80',
    rgb: '34 197 94',
    description: 'No assistance needed. Accounts for a person or clears an area.',
    examples: ["I'm safe", 'Area cleared', 'No assistance needed'],
  },
};

export const CATEGORY_ORDER: Category[] = ['CRITICAL', 'MEDICAL', 'WARNING', 'SUPPLY', 'SAFE'];

export const ROLE_COLORS = {
  rescuer: { mark: '#06b6d4', text: '#22d3ee', rgb: '6 182 212' },
  hospital: { mark: '#e2e8f0', text: '#e2e8f0', rgb: '226 232 240' },
  hazard: { mark: '#eab308', text: '#facc15', rgb: '234 179 8' },
  system: { mark: '#8b5cf6', text: '#a78bfa', rgb: '139 92 246' },
} as const;

// ---------------------------------------------------------------------------

export interface PriorityMeta {
  id: PriorityLevel;
  label: string;
  mark: string;
  text: string;
}

export const PRIORITY_META: Record<PriorityLevel, PriorityMeta> = {
  P0: { id: 'P0', label: 'Life threatening', mark: '#ef4444', text: '#f87171' },
  P1: { id: 'P1', label: 'Urgent', mark: '#f97316', text: '#fb923c' },
  P2: { id: 'P2', label: 'Important', mark: '#f59e0b', text: '#fbbf24' },
  P3: { id: 'P3', label: 'Safe / informational', mark: '#22c55e', text: '#4ade80' },
};

export const PRIORITY_ORDER: PriorityLevel[] = ['P0', 'P1', 'P2', 'P3'];

// ---------------------------------------------------------------------------

export interface StatusMeta {
  label: string;
  /** 'open' rows still need a decision from the commander. */
  open: boolean;
  text: string;
  dot: string;
}

export const EMERGENCY_STATUS_META: Record<EmergencyStatus, StatusMeta> = {
  UNASSIGNED: { label: 'Unassigned', open: true, text: '#f87171', dot: '#ef4444' },
  ACKNOWLEDGED: { label: 'Acknowledged', open: true, text: '#fbbf24', dot: '#f59e0b' },
  ASSIGNED: { label: 'Assigned', open: true, text: '#22d3ee', dot: '#06b6d4' },
  IN_PROGRESS: { label: 'In progress', open: true, text: '#22d3ee', dot: '#06b6d4' },
  RESCUED: { label: 'Rescued', open: false, text: '#4ade80', dot: '#22c55e' },
  RESOLVED: { label: 'Resolved', open: false, text: '#4ade80', dot: '#22c55e' },
  INVALID: { label: 'Invalid', open: false, text: '#94a3b8', dot: '#64748b' },
};

export const TEAM_STATUS_META: Record<TeamStatus, StatusMeta> = {
  AVAILABLE: { label: 'Available', open: true, text: '#4ade80', dot: '#22c55e' },
  ASSIGNED: { label: 'Assigned', open: true, text: '#22d3ee', dot: '#06b6d4' },
  EN_ROUTE: { label: 'En route', open: true, text: '#22d3ee', dot: '#06b6d4' },
  ON_SCENE: { label: 'On scene', open: true, text: '#fbbf24', dot: '#f59e0b' },
  RESCUE_IN_PROGRESS: { label: 'Rescue in progress', open: true, text: '#fb923c', dot: '#f97316' },
  RETURNING: { label: 'Returning', open: true, text: '#94a3b8', dot: '#64748b' },
  OFFLINE: { label: 'Offline', open: false, text: '#94a3b8', dot: '#475569' },
};

export const MISSION_STATUS_META: Record<MissionStatus, StatusMeta> = {
  ASSIGNED: { label: 'Assigned', open: true, text: '#22d3ee', dot: '#06b6d4' },
  EN_ROUTE: { label: 'En route', open: true, text: '#22d3ee', dot: '#06b6d4' },
  ON_SCENE: { label: 'On scene', open: true, text: '#fbbf24', dot: '#f59e0b' },
  RESCUE_IN_PROGRESS: { label: 'Rescue in progress', open: true, text: '#fb923c', dot: '#f97316' },
  RESCUED: { label: 'Rescued', open: false, text: '#4ade80', dot: '#22c55e' },
  COMPLETED: { label: 'Completed', open: false, text: '#4ade80', dot: '#22c55e' },
  ABORTED: { label: 'Aborted', open: false, text: '#94a3b8', dot: '#64748b' },
};

/** The forward path a mission walks. Used to drive the status stepper. */
export const MISSION_FLOW: MissionStatus[] = [
  'ASSIGNED',
  'EN_ROUTE',
  'ON_SCENE',
  'RESCUE_IN_PROGRESS',
  'RESCUED',
  'COMPLETED',
];

export const HOSPITAL_STATUS_META: Record<HospitalStatus, StatusMeta> = {
  OPEN: { label: 'Open', open: true, text: '#4ade80', dot: '#22c55e' },
  LIMITED: { label: 'Limited', open: true, text: '#fbbf24', dot: '#f59e0b' },
  NEAR_CAPACITY: { label: 'Near capacity', open: true, text: '#fb923c', dot: '#f97316' },
  FULL: { label: 'Full', open: false, text: '#f87171', dot: '#ef4444' },
  UNKNOWN: { label: 'Unknown', open: false, text: '#94a3b8', dot: '#64748b' },
};

export const HAZARD_META: Record<HazardKind, { label: string; short: string }> = {
  BLOCKED_ROAD: { label: 'Blocked road', short: 'BLOCKED' },
  FLOOD: { label: 'Flood zone', short: 'FLOOD' },
  FIRE: { label: 'Fire zone', short: 'FIRE' },
  COLLAPSE: { label: 'Collapsed structure', short: 'COLLAPSE' },
  UNSAFE_AREA: { label: 'Unsafe area', short: 'UNSAFE' },
};

export const TIMELINE_CHANNEL_META: Record<TimelineChannel, { label: string; text: string }> = {
  EMERGENCY: { label: 'Emergencies', text: '#f87171' },
  TEAM: { label: 'Teams', text: '#22d3ee' },
  ROUTE: { label: 'Routes', text: '#fbbf24' },
  SYSTEM: { label: 'System', text: '#a78bfa' },
  SYNC: { label: 'Synchronisation', text: '#60a5fa' },
  RESCUE: { label: 'Rescue', text: '#4ade80' },
};

export const SOURCE_META: Record<
  'mesh' | 'rescuer' | 'command' | 'derived',
  { label: string; description: string }
> = {
  mesh: {
    label: 'Mesh',
    description: 'Reported by a civilian device and relayed over the mesh network.',
  },
  rescuer: {
    label: 'Rescuer',
    description: 'Captured or amended on a rescuer device in the field.',
  },
  command: {
    label: 'Command',
    description: 'Entered here at the command centre by an operator.',
  },
  derived: {
    label: 'Computed',
    description: 'Calculated by this console from other values. Not reported.',
  },
};
