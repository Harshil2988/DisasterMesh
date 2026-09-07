'use client';

import { useMemo, useState } from 'react';
import { CircleAlert, Users } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { ScoredEmergency } from '@/lib/recommendations';
import type { RescueTeam } from '@/lib/types';
import { etaSeconds, formatDistance, formatEta, safeDistanceMeters } from '@/lib/geo';
import { formatAgo } from '@/lib/format';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { StatusBadge, cx } from '@/components/ui/primitives';

interface Candidate {
  team: RescueTeam;
  meters: number | null;
  /** Empty when the team can be assigned. Otherwise why it cannot. */
  blocker: string | null;
}

/**
 * Team assignment.
 *
 * Candidates are ranked by real distance to the incident, and a team that
 * cannot be assigned is still listed with the reason — hiding it would leave
 * a commander wondering where Echo went at exactly the wrong moment.
 */
export function AssignTeamDialog({
  emergency,
  open,
  onClose,
}: {
  emergency: ScoredEmergency;
  open: boolean;
  onClose: () => void;
}): React.JSX.Element {
  const { dataset, assignTeam, now } = useOps();
  const [chosen, setChosen] = useState<string | null>(null);

  const candidates = useMemo<Candidate[]>(() => {
    if (!dataset) return [];
    return dataset.teams
      .map<Candidate>((team) => {
        const meters = safeDistanceMeters(team.location, emergency.location);
        const blocker =
          team.status === 'OFFLINE'
            ? 'Out of contact — no synchronisation received'
            : team.currentMissionId
              ? 'Already committed to an active mission'
              : !team.location
                ? 'No position reported — distance cannot be computed'
                : null;
        return { team, meters, blocker };
      })
      .sort((a, b) => {
        if (!a.blocker && b.blocker) return -1;
        if (a.blocker && !b.blocker) return 1;
        return (a.meters ?? Infinity) - (b.meters ?? Infinity);
      });
  }, [dataset, emergency.location]);

  const selectedCandidate = candidates.find((c) => c.team.id === chosen) ?? null;

  return (
    <ConfirmDialog
      open={open}
      wide
      title={`Assign a rescue team to ${emergency.id}`}
      description={`${emergency.sector ?? 'No sector resolved'} · ${
        emergency.peopleAffected ?? 'unknown number of'
      } people · ${emergency.breakdown.summary}`}
      confirmLabel={selectedCandidate ? `Assign ${selectedCandidate.team.name}` : 'Select a team'}
      confirmVariant="primary"
      onConfirm={
        selectedCandidate && !selectedCandidate.blocker
          ? () => {
              assignTeam(emergency.id, selectedCandidate.team.id);
              setChosen(null);
              onClose();
            }
          : undefined
      }
      onClose={() => {
        setChosen(null);
        onClose();
      }}
    >
      {!emergency.location && (
        <div className="mb-3 flex items-start gap-2 rounded-sm border border-warning/30 bg-warning/8 px-2.5 py-2">
          <CircleAlert size={13} className="mt-px shrink-0 text-warning-text" aria-hidden />
          <p className="text-[11.5px] text-warning-text">
            This report carries no coordinates, so distances and ETAs cannot be calculated for any
            team. Assignment is still possible — the team will need a location from another source.
          </p>
        </div>
      )}

      <ul className="space-y-1.5">
        {candidates.map(({ team, meters, blocker }) => {
          const isChosen = chosen === team.id;
          const disabled = Boolean(blocker);

          return (
            <li key={team.id}>
              <button
                type="button"
                disabled={disabled}
                onClick={() => setChosen(team.id)}
                aria-pressed={isChosen}
                className={cx(
                  'flex w-full items-start gap-3 rounded-sm border p-2.5 text-left transition-colors',
                  disabled
                    ? 'cursor-not-allowed border-line bg-surface-1 opacity-55'
                    : isChosen
                      ? 'border-rescuer/60 bg-rescuer/10'
                      : 'border-line bg-surface-3 hover:border-line-2 hover:bg-surface-4',
                )}
              >
                <span
                  className={cx(
                    'mt-1 grid size-4 shrink-0 place-items-center rounded-full border',
                    isChosen ? 'border-rescuer bg-rescuer' : 'border-line-2',
                  )}
                  aria-hidden
                >
                  {isChosen && <span className="size-1.5 rounded-full bg-void" />}
                </span>

                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-[12.5px] font-semibold text-text">{team.name}</span>
                    <StatusBadge status={team.status} kind="team" />
                  </div>

                  <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-[11px] text-text-3">
                    <span className="flex items-center gap-1">
                      <Users size={10} aria-hidden />
                      <span className="num">{team.members.length}</span> members
                    </span>
                    <span>{team.capabilities.map((c) => c.replace('_', ' ').toLowerCase()).join(', ')}</span>
                    <span>Last sync {formatAgo(team.lastSyncedAt, now)}</span>
                  </div>

                  {blocker && <p className="mt-1 text-[11px] text-warning-text">{blocker}</p>}
                </div>

                <div className="shrink-0 text-right">
                  {meters !== null ? (
                    <>
                      <div className="num text-[15px] leading-none font-semibold text-text">
                        {formatDistance(meters)}
                      </div>
                      <div className="num mt-1 text-[10.5px] text-text-3">
                        ETA {formatEta(etaSeconds(meters))}
                      </div>
                    </>
                  ) : (
                    <div className="text-[10.5px] text-text-3 italic">Distance unavailable</div>
                  )}
                </div>
              </button>
            </li>
          );
        })}
      </ul>

      <p className="mt-3 border-t border-line pt-2 text-[10px] leading-relaxed text-text-3">
        Distances are straight-line from each team’s last reported position. Travel times assume a
        26 km/h ground speed and are estimates, not road-network routing. A team that cannot be
        assigned is listed with the reason rather than hidden.
      </p>
    </ConfirmDialog>
  );
}
