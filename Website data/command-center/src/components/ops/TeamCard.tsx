'use client';

import { Battery, MapPin, Radio, Users, WifiOff } from 'lucide-react';
import type { RescueTeam } from '@/lib/types';
import { TEAM_STATUS_META } from '@/lib/constants';
import { etaSeconds, formatDistance, formatEta } from '@/lib/geo';
import { formatAgo } from '@/lib/format';
import { StatusBadge, cx } from '@/components/ui/primitives';

interface TeamCardProps {
  team: RescueTeam;
  selected: boolean;
  onSelect: () => void;
  now: number;
  /** Distance to the team's current assignment, when both positions exist. */
  assignmentMeters?: number | null;
  assignmentLabel?: string | null;
}

/**
 * Operational team card.
 *
 * Telemetry is shown only when it was actually reported. A team out of
 * contact shows the gaps as gaps — a battery bar drawn from a two-hour-old
 * reading would be a lie told with a progress bar.
 */
export function TeamCard({
  team,
  selected,
  onSelect,
  now,
  assignmentMeters,
  assignmentLabel,
}: TeamCardProps): React.JSX.Element {
  const meta = TEAM_STATUS_META[team.status];
  const offline = team.status === 'OFFLINE';

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? 'true' : undefined}
      className={cx(
        'relative w-full rounded-md border p-3 text-left transition-colors duration-150',
        selected
          ? 'border-rescuer/50 bg-rescuer/8'
          : 'border-line bg-surface-2 hover:border-line-2 hover:bg-surface-3',
        offline && 'opacity-75',
      )}
    >
      <span
        className="absolute top-2.5 bottom-2.5 left-0 w-[3px] rounded-r"
        style={{ background: meta.dot }}
        aria-hidden
      />

      <div className="flex items-start justify-between gap-3 pl-1.5">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-[13px] font-semibold text-text">{team.name}</span>
            <span className="num rounded-xs border border-line px-1 py-px text-[9.5px] tracking-wider text-text-3">
              {team.callsign}
            </span>
          </div>
          <div className="mt-1">
            <StatusBadge status={team.status} kind="team" />
          </div>
        </div>

        <div className="shrink-0 text-right">
          <div className="flex items-center justify-end gap-1 text-[11px] text-text-3">
            <Users size={10} aria-hidden />
            <span className="num">{team.members.length}</span>
          </div>
          <div className="num mt-1 text-[10px] text-text-3">
            {team.completedMissions} completed
          </div>
        </div>
      </div>

      {assignmentLabel && (
        <div className="mt-2 rounded-sm border border-line bg-surface-3 px-2 py-1.5 pl-2">
          <p className="eyebrow">Current assignment</p>
          <p className="mt-0.5 truncate text-[11.5px] text-text-2">{assignmentLabel}</p>
          {assignmentMeters !== null && assignmentMeters !== undefined ? (
            <p className="num mt-0.5 text-[11px] text-rescuer-text">
              {formatDistance(assignmentMeters)} · ETA {formatEta(etaSeconds(assignmentMeters))}
            </p>
          ) : (
            <p className="mt-0.5 text-[10.5px] text-text-3 italic">
              Distance unavailable — a position is missing
            </p>
          )}
        </div>
      )}

      <dl className="mt-2 flex flex-wrap gap-x-3 gap-y-1 pl-1.5 text-[10.5px] text-text-3">
        <div className="flex items-center gap-1">
          <MapPin size={10} aria-hidden />
          <dd>{team.location ? 'Position reported' : 'No position reported'}</dd>
        </div>

        <div className="flex items-center gap-1">
          {team.connectivity === 'OFFLINE' || !team.connectivity ? (
            <WifiOff size={10} aria-hidden />
          ) : (
            <Radio size={10} aria-hidden />
          )}
          <dd>
            {team.connectivity
              ? team.connectivity.replace('_', ' ').toLowerCase()
              : 'connectivity not reported'}
          </dd>
        </div>

        <div className="flex items-center gap-1">
          <Battery size={10} aria-hidden />
          <dd className={team.batteryPercent === undefined ? 'italic' : 'num'}>
            {team.batteryPercent === undefined ? 'battery not reported' : `${team.batteryPercent}%`}
          </dd>
        </div>

        <dd className="num">sync {formatAgo(team.lastSyncedAt, now)}</dd>
      </dl>
    </button>
  );
}
