import type {
  EmergencyReport,
  Hazard,
  Hospital,
  IncidentCluster,
  OperationalRecommendation,
  PriorityBreakdown,
  RescueMission,
  RescueTeam,
} from './types';
import { formatDistance, formatEta, etaSeconds, safeDistanceMeters } from './geo';
import { formatDuration } from './format';
import { describeCluster } from './clustering';

/**
 * DECISION SUPPORT ENGINE
 *
 * Eight deterministic rules over the current operating picture. No model, no
 * training data, no inference — and the interface says exactly that, because
 * a commander who believes a rule is an oracle will stop checking it.
 *
 * Two constraints hold for every rule:
 *   1. `because[]` is mandatory. A rule that cannot state its evidence in
 *      plain language does not get to make a recommendation.
 *   2. Rules only ever read data. Nothing here changes an assignment; the
 *      operator does that, from the action the card offers.
 *
 * Replacing these with a learned model later means implementing this same
 * function signature. The UI needs no change — it renders whatever
 * justification it is handed.
 */

export interface ScoredEmergency extends EmergencyReport {
  breakdown: PriorityBreakdown;
  /** Metres from the command post. Null when either position is missing. */
  distanceFromCommand: number | null;
}

export interface RecommendationInput {
  now: number;
  emergencies: ScoredEmergency[];
  teams: RescueTeam[];
  missions: RescueMission[];
  hospitals: Hospital[];
  hazards: Hazard[];
  clusters: IncidentCluster[];
}

/** A critical case unanswered for longer than this is escalated. */
export const STALE_CRITICAL_MS = 30 * 60_000;
/** More active missions than this counts as overloaded. */
export const TEAM_LOAD_LIMIT = 2;
/** Below this share of free emergency beds, a hospital is flagged. */
export const HOSPITAL_PRESSURE_RATIO = 0.15;

export function generateRecommendations(input: RecommendationInput): OperationalRecommendation[] {
  const { now, emergencies, teams, missions, hospitals, hazards, clusters } = input;

  const out: OperationalRecommendation[] = [];
  const open = emergencies.filter(
    (e) => e.status !== 'RESOLVED' && e.status !== 'RESCUED' && e.status !== 'INVALID',
  );
  const unassigned = open.filter((e) => !e.assignedTeamId);
  const availableTeams = teams.filter((t) => t.status === 'AVAILABLE' && t.location);

  // --- RULE 1 — critical cases left waiting -------------------------------
  const stale = unassigned
    .filter((e) => e.category === 'CRITICAL' && now - e.reportedAt > STALE_CRITICAL_MS)
    .sort((a, b) => a.reportedAt - b.reportedAt);

  if (stale.length > 0) {
    const oldest = stale[0];
    out.push({
      id: 'REC-STALE-CRITICAL',
      kind: 'STALE_CRITICAL',
      severity: 'CRITICAL',
      title:
        stale.length === 1
          ? '1 critical case has been waiting over 30 minutes'
          : `${stale.length} critical cases have been waiting over 30 minutes`,
      because: [
        `Oldest is ${oldest.id}, first reported ${formatDuration(now - oldest.reportedAt)} ago.`,
        `None of these ${stale.length === 1 ? 'has' : 'have'} a team assigned.`,
        `Rule: category is CRITICAL, status is unassigned, and waiting time exceeds ${
          STALE_CRITICAL_MS / 60_000
        } minutes.`,
      ],
      suggestion: `Assign a team to ${oldest.id} first — it has the longest unanswered wait.`,
      weight: 1000 + stale.length * 10,
      actions: [
        { label: 'Open incident', kind: 'VIEW_EMERGENCY', emergencyId: oldest.id },
        { label: 'Assign team', kind: 'ASSIGN_TEAM', emergencyId: oldest.id },
      ],
    });
  }

  // --- RULE 2 — concentrations of people ----------------------------------
  for (const cluster of clusters.slice(0, 2)) {
    const criticals = cluster.countsByCategory.CRITICAL;
    if (cluster.emergencyIds.length < 3) continue;

    const nearest = nearestTeamTo(cluster.center, availableTeams);
    const because = [
      describeCluster(cluster) + '.',
      `${cluster.emergencyIds.length} separate reports: ` +
        `${criticals} critical, ${cluster.countsByCategory.MEDICAL} medical, ` +
        `${cluster.countsByCategory.SUPPLY} supply, ${cluster.countsByCategory.SAFE} safe.`,
      `Rule: three or more reports within ${Math.round(cluster.radiusMeters)} m of each other.`,
    ];
    if (nearest) {
      because.push(
        `Nearest available team is ${nearest.team.name}, ${formatDistance(nearest.meters)} away ` +
          `(${formatEta(etaSeconds(nearest.meters))} at the standard ground-speed estimate).`,
      );
    }

    out.push({
      id: `REC-CLUSTER-${cluster.id}`,
      kind: 'CLUSTER',
      severity: criticals >= 3 ? 'CRITICAL' : 'WARNING',
      title: `High-impact area — ${cluster.sector ?? 'unnamed sector'}`,
      because,
      suggestion: nearest
        ? `Deploy ${nearest.team.name} to the cluster rather than to a single incident — one approach covers ${cluster.emergencyIds.length} reports.`
        : 'No team is currently available for this cluster. Free one, or request reinforcement.',
      weight: 900 + criticals * 20 + cluster.peopleAffected,
      actions: [
        { label: 'View area', kind: 'VIEW_AREA', clusterId: cluster.id },
        ...(nearest
          ? [
              {
                label: `Assign ${nearest.team.name}`,
                kind: 'ASSIGN_TEAM' as const,
                teamId: nearest.team.id,
                emergencyId: topOfCluster(cluster, emergencies),
              },
            ]
          : []),
      ],
    });
  }

  // --- RULE 3 — closest available team to the highest-priority open case --
  const topUnassigned = [...unassigned].sort((a, b) => b.breakdown.score - a.breakdown.score)[0];
  if (topUnassigned?.location) {
    const nearest = nearestTeamTo(topUnassigned.location, availableTeams);
    if (nearest) {
      const alsoNear = unassigned.filter(
        (e) =>
          e.id !== topUnassigned.id &&
          e.location &&
          (safeDistanceMeters(nearest.team.location, e.location) ?? Infinity) < nearest.meters * 1.5,
      );

      out.push({
        id: 'REC-NEAREST-TEAM',
        kind: 'NEAREST_TEAM',
        severity: topUnassigned.breakdown.level === 'P0' ? 'CRITICAL' : 'WARNING',
        title: `${nearest.team.name} is closest to the highest-priority open case`,
        because: [
          `${topUnassigned.id} scores ${topUnassigned.breakdown.score}/100 — the highest of ${unassigned.length} unassigned reports.`,
          `${nearest.team.name} is ${formatDistance(nearest.meters)} away and currently available.`,
          alsoNear.length > 0
            ? `The same team is also nearest to ${alsoNear.length} other unresolved ${
                alsoNear.length === 1 ? 'report' : 'reports'
              }.`
            : 'No other unassigned report is closer to this team.',
          'Rule: straight-line distance from the team’s last reported position. Not a road distance.',
        ],
        suggestion: `Assign ${nearest.team.name} to ${topUnassigned.id}.`,
        weight: 800 + topUnassigned.breakdown.score,
        actions: [
          { label: 'Assign team', kind: 'ASSIGN_TEAM', emergencyId: topUnassigned.id, teamId: nearest.team.id },
          { label: 'Open incident', kind: 'VIEW_EMERGENCY', emergencyId: topUnassigned.id },
        ],
      });
    }
  }

  // --- RULE 4 — hazards sitting on an approach ----------------------------
  for (const hazard of hazards.filter((h) => h.active)) {
    const affected = open.filter(
      (e) => e.location && (safeDistanceMeters(e.location, hazard.center) ?? Infinity) < hazard.radiusMeters * 3.5,
    );
    if (affected.length < 2) continue;

    out.push({
      id: `REC-HAZARD-${hazard.id}`,
      kind: 'ROUTE_HAZARD',
      severity: 'WARNING',
      title: `Hazard on the approach to ${affected[0].sector ?? 'an active area'}`,
      because: [
        `${hazard.label}.`,
        `${affected.length} unresolved reports lie within ${formatDistance(
          hazard.radiusMeters * 3.5,
        )} of it.`,
        `Reported ${formatDuration(now - hazard.reportedAt)} ago by ${hazard.reportedBy}.`,
        'Rule: an active hazard within 3.5 avoidance radii of two or more open reports.',
      ],
      suggestion:
        'Calculate the route before dispatch — the router will detour around this zone, and will say so if no clear corridor exists.',
      weight: 600 + affected.length * 5,
      actions: [{ label: 'View on map', kind: 'VIEW_MAP' }],
    });
  }

  // --- RULE 5 — hospitals under pressure ----------------------------------
  for (const hospital of hospitals) {
    const beds = hospital.emergencyBeds;
    const total = hospital.totalBeds;
    const pressured =
      hospital.status === 'FULL' ||
      hospital.status === 'NEAR_CAPACITY' ||
      (beds !== undefined && total !== undefined && total > 0 && beds / total < HOSPITAL_PRESSURE_RATIO);

    if (!pressured) continue;

    out.push({
      id: `REC-HOSPITAL-${hospital.id}`,
      kind: 'HOSPITAL_CAPACITY',
      severity: hospital.status === 'FULL' ? 'CRITICAL' : 'WARNING',
      title: `${hospital.name} is ${hospital.status === 'FULL' ? 'full' : 'approaching capacity'}`,
      because: [
        beds !== undefined
          ? `${beds} emergency ${beds === 1 ? 'bed' : 'beds'} reported free.`
          : 'No free-bed count reported.',
        hospital.lastUpdated
          ? `Capacity last updated ${formatDuration(now - hospital.lastUpdated)} ago.`
          : 'Capacity has never been reported for this facility.',
        'Rule: status is FULL or NEAR_CAPACITY, or free emergency beds fall below 15% of total.',
      ],
      suggestion: 'Route further medical casualties to the next-nearest facility with reported capacity.',
      weight: 500,
      actions: [{ label: 'View hospital', kind: 'VIEW_HOSPITAL', hospitalId: hospital.id }],
    });
  }

  // --- RULE 6 — teams carrying too much -----------------------------------
  const activeByTeam = new Map<string, number>();
  for (const mission of missions) {
    if (mission.status === 'COMPLETED' || mission.status === 'ABORTED') continue;
    activeByTeam.set(mission.teamId, (activeByTeam.get(mission.teamId) ?? 0) + 1);
  }
  for (const [teamId, count] of activeByTeam) {
    if (count <= TEAM_LOAD_LIMIT) continue;
    const team = teams.find((t) => t.id === teamId);
    if (!team) continue;

    out.push({
      id: `REC-LOAD-${teamId}`,
      kind: 'TEAM_OVERLOADED',
      severity: 'WARNING',
      title: `${team.name} is carrying ${count} active missions`,
      because: [
        `${count} missions are open against this team.`,
        `${team.members.length} members are on the roster.`,
        `Rule: more than ${TEAM_LOAD_LIMIT} simultaneous active missions.`,
      ],
      suggestion: 'Redistribute one mission to an available team before assigning anything further.',
      weight: 400 + count,
      actions: [{ label: 'View team', kind: 'VIEW_TEAM', teamId }],
    });
  }

  // --- RULE 7 — idle capacity against an open backlog ---------------------
  const urgentUnassigned = unassigned.filter(
    (e) => e.breakdown.level === 'P0' || e.breakdown.level === 'P1',
  );
  if (availableTeams.length > 0 && urgentUnassigned.length > 0) {
    out.push({
      id: 'REC-IDLE-TEAM',
      kind: 'IDLE_TEAM',
      severity: 'WARNING',
      title: `${availableTeams.length} ${
        availableTeams.length === 1 ? 'team is' : 'teams are'
      } available with ${urgentUnassigned.length} urgent cases open`,
      because: [
        `Available: ${availableTeams.map((t) => t.name).join(', ')}.`,
        `${urgentUnassigned.length} unassigned reports are at P0 or P1.`,
        'Rule: at least one available team while any P0 or P1 report is unassigned.',
      ],
      suggestion: 'Assign the idle teams to the top of the priority queue.',
      weight: 300 + urgentUnassigned.length,
      actions: [{ label: 'Open queue', kind: 'VIEW_EMERGENCY', emergencyId: urgentUnassigned[0].id }],
    });
  }

  // --- RULE 8 — backlog with no capacity at all ---------------------------
  if (availableTeams.length === 0 && unassigned.length > 0) {
    out.push({
      id: 'REC-BACKLOG',
      kind: 'UNASSIGNED_BACKLOG',
      severity: 'CRITICAL',
      title: `${unassigned.length} unassigned reports and no available team`,
      because: [
        'Every team is offline or already committed to a mission.',
        `${unassigned.filter((e) => e.category === 'CRITICAL').length} of the backlog are critical.`,
        'Rule: zero teams in AVAILABLE state while unassigned reports exist.',
      ],
      suggestion: 'Request reinforcement, or release a team from a lower-priority mission.',
      weight: 950,
      actions: [{ label: 'View teams', kind: 'VIEW_TEAM' }],
    });
  }

  return out.sort((a, b) => b.weight - a.weight);
}

// ---------------------------------------------------------------------------

function nearestTeamTo(
  point: { latitude: number; longitude: number },
  teams: RescueTeam[],
): { team: RescueTeam; meters: number } | null {
  let best: { team: RescueTeam; meters: number } | null = null;
  for (const team of teams) {
    const meters = safeDistanceMeters(team.location, point);
    if (meters === null) continue;
    if (!best || meters < best.meters) best = { team, meters };
  }
  return best;
}

function topOfCluster(cluster: IncidentCluster, emergencies: ScoredEmergency[]): string {
  const members = emergencies.filter((e) => cluster.emergencyIds.includes(e.id));
  return [...members].sort((a, b) => b.breakdown.score - a.breakdown.score)[0]?.id ?? cluster.emergencyIds[0];
}
