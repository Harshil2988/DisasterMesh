'use client';

import { useRouter } from 'next/navigation';
import { Map as MapIcon, Radio, Siren, Users } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { RescueTeam, TeamStatus } from '@/lib/types';
import { safeDistanceMeters } from '@/lib/geo';
import { formatAgo, formatClock } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { Button, EmptyState, Field, Panel, StatusBadge } from '@/components/ui/primitives';
import { TeamCard } from '@/components/ops/TeamCard';
import { MISSION_STATUS_META } from '@/lib/constants';

/** Available first — the roster is read to answer "who can I send?". */
const GROUPS: { title: string; match: (t: RescueTeam) => boolean }[] = [
  { title: 'Available', match: (t) => t.status === 'AVAILABLE' },
  {
    title: 'On task',
    match: (t) => ['ASSIGNED', 'EN_ROUTE', 'ON_SCENE', 'RESCUE_IN_PROGRESS', 'RETURNING'].includes(t.status as TeamStatus),
  },
  { title: 'Out of contact', match: (t) => t.status === 'OFFLINE' },
];

export default function TeamsPage(): React.JSX.Element {
  const router = useRouter();
  const { dataset, derived, now, selectedTeamId, selectTeam, selectEmergency, fit } = useOps();

  if (!dataset) return <></>;

  const team = dataset.teams.find((t) => t.id === selectedTeamId) ?? null;

  const assignmentFor = (t: RescueTeam) => {
    const mission = dataset.missions.find(
      (m) => m.teamId === t.id && m.status !== 'COMPLETED' && m.status !== 'ABORTED',
    );
    if (!mission) return { label: null, meters: null, emergencyId: null, mission: null };
    const emergency = derived.byId.get(mission.emergencyId);
    return {
      mission,
      emergencyId: mission.emergencyId,
      label: emergency
        ? `${emergency.id} — ${emergency.sector ?? 'no sector'} · ${emergency.category.toLowerCase()}`
        : mission.emergencyId,
      meters: safeDistanceMeters(t.location, emergency?.location ?? null),
    };
  };

  const nearbyFor = (t: RescueTeam) =>
    t.location
      ? derived.emergencies
          .filter((e) => e.location && !e.assignedTeamId && e.status !== 'RESOLVED' && e.status !== 'RESCUED')
          .map((e) => ({ emergency: e, meters: safeDistanceMeters(t.location, e.location) }))
          .filter((r) => r.meters !== null)
          .sort((a, b) => (a.meters ?? 0) - (b.meters ?? 0))
          .slice(0, 4)
      : [];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Rescue Teams"
        subtitle="Roster, position and current tasking for every team on this operation."
        actions={
          <Button size="sm" icon={MapIcon} onClick={() => router.push('/map')}>
            Show on map
          </Button>
        }
      />

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[1fr_minmax(320px,400px)]">
        <div className="scroll-y min-h-0 space-y-4 p-4">
          {dataset.teams.length === 0 ? (
            <EmptyState
              icon={Users}
              title="No rescue teams on record"
              detail="Teams are command-centre records rather than mesh traffic. None have been added for this operation."
            />
          ) : (
            GROUPS.map((group) => {
              const members = dataset.teams.filter(group.match);
              if (members.length === 0) return null;
              return (
                <section key={group.title}>
                  <h2 className="eyebrow mb-2">
                    {group.title} · {members.length}
                  </h2>
                  <div className="grid gap-2 md:grid-cols-2 2xl:grid-cols-3">
                    {members.map((t) => {
                      const assignment = assignmentFor(t);
                      return (
                        <TeamCard
                          key={t.id}
                          team={t}
                          now={now}
                          selected={selectedTeamId === t.id}
                          onSelect={() => selectTeam(t.id)}
                          assignmentLabel={assignment.label}
                          assignmentMeters={assignment.meters}
                        />
                      );
                    })}
                  </div>
                </section>
              );
            })
          )}
        </div>

        {/* ---- team detail ---- */}
        <aside className="scroll-y min-h-0 border-l border-line bg-surface-1 p-4">
          {!team ? (
            <EmptyState
              icon={Users}
              title="No team selected"
              detail="Choose a team to see its roster, tasking, nearby unassigned incidents and mission history."
            />
          ) : (
            <div className="space-y-3">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-[16px] font-semibold text-text">{team.name}</h2>
                  <StatusBadge status={team.status} kind="team" />
                </div>
                <p className="num mt-0.5 text-[11px] text-text-3">
                  {team.callsign} · {team.capabilities.map((c) => c.replace('_', ' ').toLowerCase()).join(', ')}
                </p>
              </div>

              <Panel title="Roster" icon={Users} bodyClassName="p-0">
                <ul>
                  {team.members.map((member) => (
                    <li
                      key={member.id}
                      className="flex items-center justify-between gap-2 border-b border-line px-3 py-1.5 last:border-b-0"
                    >
                      <span className="text-[12px] text-text-2">{member.name}</span>
                      <span className="text-[10.5px] text-text-3">{member.role}</span>
                    </li>
                  ))}
                </ul>
              </Panel>

              <Panel title="Status" icon={Radio} bodyClassName="grid grid-cols-2 gap-3 p-3">
                <Field label="Position">
                  {team.location ? 'Reported' : <span className="text-text-3 italic">Not reported</span>}
                </Field>
                <Field label="Last synchronised">{formatAgo(team.lastSyncedAt, now)}</Field>
                <Field label="Battery" mono>
                  {team.batteryPercent === undefined ? (
                    <span className="font-sans text-text-3 italic">Not reported</span>
                  ) : (
                    `${team.batteryPercent}%`
                  )}
                </Field>
                <Field label="Connectivity">
                  {team.connectivity ? (
                    team.connectivity.replace('_', ' ').toLowerCase()
                  ) : (
                    <span className="text-text-3 italic">Not reported</span>
                  )}
                </Field>
              </Panel>

              <Panel title="Nearby unassigned incidents" icon={Siren} bodyClassName="p-0">
                {!team.location ? (
                  <p className="px-3 py-3 text-[11.5px] text-text-3">
                    This team has not reported a position, so nearby incidents cannot be ranked.
                  </p>
                ) : nearbyFor(team).length === 0 ? (
                  <p className="px-3 py-3 text-[11.5px] text-text-3">
                    No unassigned incidents with a known position.
                  </p>
                ) : (
                  <ul>
                    {nearbyFor(team).map(({ emergency, meters }) => (
                      <li key={emergency.id}>
                        <button
                          type="button"
                          onClick={() => {
                            selectEmergency(emergency.id);
                            router.push('/emergencies');
                          }}
                          className="flex w-full items-center gap-2 border-b border-line px-3 py-2 text-left transition-colors last:border-b-0 hover:bg-surface-3"
                        >
                          <span className="num text-[11.5px] font-medium text-text">
                            {emergency.id}
                          </span>
                          <span className="truncate text-[11px] text-text-3">
                            {emergency.sector ?? 'no sector'}
                          </span>
                          <span className="num ml-auto text-[11.5px] text-rescuer-text">
                            {meters !== null ? `${(meters / 1000).toFixed(1)} km` : '—'}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>

              <Panel title="Mission history" bodyClassName="p-0">
                {dataset.missions.filter((m) => m.teamId === team.id).length === 0 ? (
                  <p className="px-3 py-3 text-[11.5px] text-text-3">
                    No missions recorded for this team during this operation.
                  </p>
                ) : (
                  <ul>
                    {dataset.missions
                      .filter((m) => m.teamId === team.id)
                      .sort((a, b) => b.assignedAt - a.assignedAt)
                      .map((mission) => (
                        <li
                          key={mission.id}
                          className="flex items-center gap-2 border-b border-line px-3 py-2 last:border-b-0"
                        >
                          <span className="num text-[11.5px] text-text-2">{mission.emergencyId}</span>
                          <span
                            className="text-[10.5px]"
                            style={{ color: MISSION_STATUS_META[mission.status].text }}
                          >
                            {MISSION_STATUS_META[mission.status].label}
                          </span>
                          <span className="num ml-auto text-[10.5px] text-text-3">
                            {formatClock(mission.assignedAt)}
                          </span>
                        </li>
                      ))}
                  </ul>
                )}
              </Panel>

              <Button
                block
                icon={MapIcon}
                onClick={() => {
                  fit('ALL');
                  router.push('/map');
                }}
              >
                Locate on map
              </Button>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
