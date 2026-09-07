'use client';

import { useRouter } from 'next/navigation';
import { ArrowRight, CircleCheck, Route as RouteIcon, Siren, Users } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { MissionStatus, RescueMission } from '@/lib/types';
import { MISSION_FLOW, MISSION_STATUS_META } from '@/lib/constants';
import { etaSeconds, formatDistance, formatEta, safeDistanceMeters } from '@/lib/geo';
import { formatClock, formatDuration } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import {
  Button,
  CategoryBadge,
  EmptyState,
  Panel,
  PriorityBadge,
  StatusBadge,
  cx,
} from '@/components/ui/primitives';

/** The forward path of a mission, drawn as a stepper the operator can drive. */
function MissionStepper({
  mission,
  onAdvance,
}: {
  mission: RescueMission;
  onAdvance: (status: MissionStatus) => void;
}): React.JSX.Element {
  const currentIndex = MISSION_FLOW.indexOf(mission.status);
  const aborted = mission.status === 'ABORTED';

  return (
    <ol className="flex flex-wrap items-center gap-1">
      {MISSION_FLOW.map((status, index) => {
        const meta = MISSION_STATUS_META[status];
        const done = !aborted && index < currentIndex;
        const current = !aborted && index === currentIndex;
        const next = !aborted && index === currentIndex + 1;

        return (
          <li key={status} className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => onAdvance(status)}
              disabled={!next}
              title={
                next
                  ? `Advance to ${meta.label}`
                  : current
                    ? 'Current status'
                    : done
                      ? 'Already passed'
                      : 'Not reachable yet'
              }
              className={cx(
                'rounded-xs border px-1.5 py-1 text-[10px] font-medium whitespace-nowrap transition-colors',
                current && 'border-rescuer/60 bg-rescuer/15 text-rescuer-text',
                done && 'border-line bg-surface-3 text-text-3',
                next && 'cursor-pointer border-line-2 bg-surface-3 text-text-2 hover:bg-surface-4 hover:text-text',
                !current && !done && !next && 'cursor-not-allowed border-line text-text-3 opacity-45',
              )}
            >
              {meta.label}
            </button>
            {index < MISSION_FLOW.length - 1 && (
              <ArrowRight size={9} className="shrink-0 text-line-2" aria-hidden />
            )}
          </li>
        );
      })}
    </ol>
  );
}

export default function OperationsPage(): React.JSX.Element {
  const router = useRouter();
  const { dataset, derived, now, advanceMission, selectEmergency, calculateRoute } = useOps();

  if (!dataset) return <></>;

  const active = dataset.missions.filter(
    (m) => m.status !== 'COMPLETED' && m.status !== 'ABORTED',
  );
  const closed = dataset.missions.filter(
    (m) => m.status === 'COMPLETED' || m.status === 'ABORTED',
  );

  const unassignedUrgent = derived.emergencies
    .filter((e) => !e.assignedTeamId && e.status === 'UNASSIGNED')
    .sort((a, b) => b.breakdown.score - a.breakdown.score)
    .slice(0, 6);

  const renderMission = (mission: RescueMission) => {
    const emergency = derived.byId.get(mission.emergencyId);
    const team = dataset.teams.find((t) => t.id === mission.teamId);
    const meters = safeDistanceMeters(team?.location ?? null, emergency?.location ?? null);

    return (
      <article key={mission.id} className="rounded-md border border-line bg-surface-2 p-3">
        {/* incident → team → status */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => {
              selectEmergency(mission.emergencyId);
              router.push('/emergencies');
            }}
            className="num text-[13px] font-semibold text-text hover:underline"
          >
            {mission.emergencyId}
          </button>
          {emergency && (
            <>
              <CategoryBadge category={emergency.category} />
              <PriorityBadge priority={emergency.priority} score={emergency.breakdown.score} />
            </>
          )}
          <ArrowRight size={12} className="text-line-2" aria-hidden />
          <span className="flex items-center gap-1.5 text-[12.5px] font-medium text-rescuer-text">
            <Users size={11} aria-hidden />
            {team?.name ?? mission.teamId}
          </span>
          <span className="ml-auto">
            <StatusBadge status={mission.status} kind="mission" />
          </span>
        </div>

        {emergency && (
          <p className="mt-1.5 line-clamp-1 text-[11.5px] text-text-3">{emergency.description}</p>
        )}

        <dl className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px]">
          <div className="flex gap-1.5">
            <dt className="text-text-3">Distance</dt>
            <dd className="num text-text-2">
              {meters !== null ? formatDistance(meters) : <span className="italic">unavailable</span>}
            </dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-text-3">ETA</dt>
            <dd className="num text-text-2">{meters !== null ? formatEta(etaSeconds(meters)) : '—'}</dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-text-3">Assigned</dt>
            <dd className="num text-text-2">{formatClock(mission.assignedAt)}</dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-text-3">Elapsed</dt>
            <dd className="num text-text-2">{formatDuration(now - mission.assignedAt)}</dd>
          </div>
        </dl>

        <div className="mt-2.5 border-t border-line pt-2.5">
          <p className="eyebrow mb-1.5">Mission status</p>
          <MissionStepper
            mission={mission}
            onAdvance={(status) => advanceMission(mission.id, status)}
          />
        </div>

        {mission.history.length > 0 && (
          <ul className="mt-2 flex flex-wrap gap-x-3 gap-y-0.5 text-[10px] text-text-3">
            {mission.history.map((entry, i) => (
              <li key={i} className="num">
                {formatClock(entry.at)} {MISSION_STATUS_META[entry.status].label.toLowerCase()}
              </li>
            ))}
          </ul>
        )}

        <div className="mt-2.5 flex gap-1.5">
          <Button
            size="sm"
            icon={RouteIcon}
            onClick={() => {
              selectEmergency(mission.emergencyId);
              calculateRoute(mission.emergencyId, mission.teamId);
              router.push('/map');
            }}
            disabled={!emergency?.location}
          >
            Route on map
          </Button>
          <Button
            size="sm"
            onClick={() => {
              selectEmergency(mission.emergencyId);
              router.push('/emergencies');
            }}
          >
            Open incident
          </Button>
        </div>
      </article>
    );
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Rescue Operations"
        subtitle="Missions from assignment to completion. Every status change is recorded on the timeline."
      />

      <div className="scroll-y min-h-0 flex-1 p-4">
        <div className="grid gap-4 xl:grid-cols-[1.5fr_1fr]">
          <div className="space-y-4">
            <section>
              <h2 className="eyebrow mb-2">Active missions · {active.length}</h2>
              {active.length === 0 ? (
                <Panel>
                  <EmptyState
                    icon={RouteIcon}
                    title="No active missions"
                    detail="Assign a team to an incident to open a mission. It will appear here with a status you can advance."
                  />
                </Panel>
              ) : (
                <div className="space-y-2">{active.map(renderMission)}</div>
              )}
            </section>

            {closed.length > 0 && (
              <section>
                <h2 className="eyebrow mb-2">Closed missions · {closed.length}</h2>
                <div className="space-y-2 opacity-80">{closed.map(renderMission)}</div>
              </section>
            )}
          </div>

          {/* awaiting dispatch */}
          <Panel
            title="Awaiting dispatch"
            subtitle="Highest-scoring unassigned reports"
            icon={Siren}
            bodyClassName="p-3 space-y-2"
          >
            {unassignedUrgent.length === 0 ? (
              <EmptyState
                icon={CircleCheck}
                title="Nothing awaiting dispatch"
                detail="Every open report has a team assigned."
              />
            ) : (
              unassignedUrgent.map((emergency) => (
                <div key={emergency.id} className="rounded-sm border border-line bg-surface-3 p-2.5">
                  <div className="flex items-center gap-2">
                    <CategoryBadge category={emergency.category} />
                    <span className="num text-[12px] font-semibold text-text">{emergency.id}</span>
                    <span className="ml-auto">
                      <PriorityBadge
                        priority={emergency.priority}
                        score={emergency.breakdown.score}
                      />
                    </span>
                  </div>
                  <p className="mt-1 line-clamp-2 text-[11.5px] text-text-2">
                    {emergency.description}
                  </p>
                  <p className="mt-1 text-[10.5px] text-text-3">
                    {emergency.sector ?? 'No sector'} ·{' '}
                    <span className="num">{formatDuration(now - emergency.reportedAt)}</span> waiting
                    · <span className="num">{formatDistance(emergency.distanceFromCommand)}</span>
                  </p>
                  <Button
                    size="sm"
                    variant="primary"
                    className="mt-2"
                    icon={Users}
                    onClick={() => {
                      selectEmergency(emergency.id);
                      router.push('/emergencies');
                    }}
                  >
                    Assign a team
                  </Button>
                </div>
              ))
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}
