import type {
  Category,
  EmergencyReport,
  PriorityBreakdown,
  PriorityFactor,
  PriorityLevel,
  Vulnerability,
} from './types';
import { formatDuration } from './format';

/**
 * RESCUE PRIORITY SCORE — a transparent, deterministic ranking function.
 *
 * There is no model here and the console never implies there is one. The
 * score is a weighted sum of six stated factors, every one of which is
 * returned alongside the total so the commander sees the same arithmetic the
 * queue used. Two reports with identical inputs always score identically.
 *
 * If a learned model ever replaces this, it must satisfy the same interface:
 * return the number AND the factors that justify it. A score a commander
 * cannot audit is a score a commander cannot overrule.
 */

// --- weights -------------------------------------------------------------
// Chosen so severity dominates, and no single secondary factor can lift a
// supply request above an untouched life-threatening call.

const SEVERITY_POINTS: Record<Category, number> = {
  CRITICAL: 45,
  MEDICAL: 32,
  WARNING: 18,
  SUPPLY: 10,
  SAFE: 0,
};

const VULNERABILITY_POINTS: Record<Vulnerability, number> = {
  TRAPPED: 12,
  INJURED: 8,
  CHILDREN: 7,
  ELDERLY: 6,
  DISABLED: 6,
  PREGNANT: 6,
};

const VULNERABILITY_LABELS: Record<Vulnerability, string> = {
  TRAPPED: 'trapped',
  INJURED: 'injured',
  CHILDREN: 'children present',
  ELDERLY: 'elderly',
  DISABLED: 'mobility needs',
  PREGNANT: 'pregnancy',
};

const MAX_VULNERABILITY = 20;
const MAX_PEOPLE_POINTS = 12;
const MAX_WAITING_POINTS = 18;

/** Waiting time saturates here: beyond it, more delay adds no further points. */
const WAITING_SATURATION_MS = 90 * 60_000;

/** Reports already being worked lose their queue-jumping urgency. */
const STATUS_POINTS: Record<EmergencyReport['status'], number> = {
  UNASSIGNED: 8,
  ACKNOWLEDGED: 4,
  ASSIGNED: 0,
  IN_PROGRESS: 0,
  RESCUED: -40,
  RESOLVED: -60,
  INVALID: -100,
};

export function scoreEmergency(report: EmergencyReport, now: number): PriorityBreakdown {
  const factors: PriorityFactor[] = [];

  // 1 — severity of the reported category
  const severity = SEVERITY_POINTS[report.category];
  factors.push({
    label: 'Emergency severity',
    detail: `Reported as ${report.category.toLowerCase()}`,
    points: severity,
  });

  // 2 — vulnerability of the people involved
  const vulnPoints = Math.min(
    MAX_VULNERABILITY,
    report.vulnerabilities.reduce((sum, v) => sum + VULNERABILITY_POINTS[v], 0),
  );
  if (vulnPoints > 0) {
    factors.push({
      label: 'Vulnerability',
      detail: report.vulnerabilities.map((v) => VULNERABILITY_LABELS[v]).join(', '),
      points: vulnPoints,
    });
  }

  // 3 — number of people affected, on a log curve: the step from 1 to 4
  //     people matters far more than the step from 20 to 23.
  const people = report.peopleAffected ?? 0;
  const peoplePoints =
    people > 1 ? Math.min(MAX_PEOPLE_POINTS, Math.round(Math.log2(people) * 4)) : 0;
  if (people > 0) {
    factors.push({
      label: 'People affected',
      detail: people === 1 ? '1 person reported' : `${people} people reported`,
      points: peoplePoints,
    });
  } else {
    factors.push({
      label: 'People affected',
      detail: 'Not reported — assumed at least one',
      points: 0,
    });
  }

  // 4 — how long this has gone unanswered
  const waitedMs = Math.max(0, now - report.reportedAt);
  const waitingPoints = Math.round(
    Math.min(1, waitedMs / WAITING_SATURATION_MS) * MAX_WAITING_POINTS,
  );
  factors.push({
    label: 'Waiting time',
    detail: `${formatDuration(waitedMs)} since first report`,
    points: waitingPoints,
  });

  // 5 — current handling state
  const statusPoints = STATUS_POINTS[report.status];
  if (statusPoints !== 0) {
    factors.push({
      label: 'Rescue status',
      detail:
        statusPoints > 0
          ? 'No team is working this yet'
          : `Already ${report.status.toLowerCase().replace('_', ' ')}`,
      points: statusPoints,
    });
  }

  // 6 — a report with no position cannot be routed to; flagged, not penalised
  if (!report.location) {
    factors.push({
      label: 'Position',
      detail: 'No coordinates received — cannot be routed to',
      points: 0,
    });
  }

  const raw = factors.reduce((sum, f) => sum + f.points, 0);
  const score = Math.max(0, Math.min(100, raw));

  return {
    score,
    level: levelFor(score, report.category),
    factors,
    summary: buildSummary(report, waitedMs),
  };
}

/**
 * Maps a score to a priority band, with two overrides that keep the bands
 * honest rather than purely numeric:
 *   - a SAFE report is never anything but P3, however long it has waited;
 *   - a CRITICAL report never falls below P1 while it is still open.
 */
function levelFor(score: number, category: Category): PriorityLevel {
  if (category === 'SAFE') return 'P3';
  const banded: PriorityLevel = score >= 75 ? 'P0' : score >= 55 ? 'P1' : score >= 30 ? 'P2' : 'P3';
  if (category === 'CRITICAL' && (banded === 'P2' || banded === 'P3')) return 'P1';
  return banded;
}

/** The one-line justification shown under every score. */
function buildSummary(report: EmergencyReport, waitedMs: number): string {
  const parts: string[] = [];

  if (report.vulnerabilities.length > 0) {
    parts.push(report.vulnerabilities.map((v) => VULNERABILITY_LABELS[v]).join(' · '));
  } else {
    parts.push(report.category === 'SAFE' ? 'no assistance needed' : 'no vulnerability reported');
  }

  if (report.peopleAffected && report.peopleAffected > 0) {
    parts.push(report.peopleAffected === 1 ? '1 person' : `${report.peopleAffected} people`);
  }

  parts.push(`waiting ${formatDuration(waitedMs)}`);
  const s = parts.join(' · ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/**
 * The thresholds, published so the UI can render the scale it is using
 * instead of asking the operator to infer it.
 */
export const PRIORITY_BANDS: { level: PriorityLevel; min: number; label: string }[] = [
  { level: 'P0', min: 75, label: 'Life threatening' },
  { level: 'P1', min: 55, label: 'Urgent' },
  { level: 'P2', min: 30, label: 'Important' },
  { level: 'P3', min: 0, label: 'Safe / informational' },
];

export const SCORING_MODEL_NOTE =
  'Deterministic weighted sum of six factors. No machine learning is used. ' +
  'Identical inputs always produce an identical score.';
