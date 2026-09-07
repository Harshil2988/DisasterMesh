'use client';

import { useRouter } from 'next/navigation';
import {
  Crosshair,
  FlaskConical,
  Navigation,
  RefreshCw,
  Radio,
  Route as RouteIcon,
  TriangleAlert,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { etaSeconds, formatDistance, formatEta, safeDistanceMeters } from '@/lib/geo';
import { formatAgo, formatClock } from '@/lib/format';
import { Button, CategoryBadge, PriorityBadge, cx } from '@/components/ui/primitives';
import { GlobalSearch } from './GlobalSearch';

/** One live figure in the header status strip. */
function Metric({
  label,
  value,
  tone,
  onClick,
  className,
}: {
  label: string;
  value: string | number;
  tone?: string;
  onClick?: () => void;
  /** Breakpoint at which this figure earns its space. */
  className?: string;
}): React.JSX.Element {
  const content = (
    <>
      <span className="num text-[15px] leading-none font-semibold" style={{ color: tone }}>
        {value}
      </span>
      <span className="eyebrow leading-none">{label}</span>
    </>
  );

  if (!onClick) {
    return <div className={cx('flex flex-col items-start gap-1', className)}>{content}</div>;
  }
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        '-mx-1.5 flex flex-col items-start gap-1 rounded-sm px-1.5 py-1 transition-colors hover:bg-surface-3',
        className,
      )}
      title={`Show ${label.toLowerCase()}`}
    >
      {content}
    </button>
  );
}

export function CommandHeader(): React.JSX.Element {
  const router = useRouter();
  const ops = useOps();
  const {
    mode, dataset, derived, selected, now, route, setMode, reload,
    setFilters, resetFilters, requestDeviceLocation,
  } = ops;

  const stats = derived.stats;
  const connection = dataset?.connection;
  const isDemo = mode === 'DEMO';

  const focus = (categories: Parameters<typeof setFilters>[0]['categories']): void => {
    resetFilters();
    setFilters({ categories });
    router.push('/emergencies');
  };

  // Distance always measures from whichever origin is genuinely in use: an
  // assigned team's own position when there is one, otherwise the command
  // post or this device.
  const assignedTeam = selected?.assignedTeamId
    ? (dataset?.teams.find((t) => t.id === selected.assignedTeamId) ?? null)
    : null;
  const distanceOrigin = assignedTeam?.location ?? derived.origin.point;
  const distanceOriginLabel = assignedTeam ? assignedTeam.name : derived.origin.label;
  const distanceToIncident = safeDistanceMeters(distanceOrigin, selected?.location ?? null);

  return (
    <header className="shrink-0 border-b border-line bg-surface-1">
      {/* ---- command bar ---- */}
      <div className="flex h-14 items-center gap-4 px-4">
        {/* brand */}
        <div className="flex shrink-0 items-center gap-2.5">
          <MeshMark />
          <div className="leading-none">
            <div className="text-[13px] font-bold tracking-[0.16em] text-text">DISASTERMESH</div>
            <div className="mt-1 text-[10px] font-medium tracking-[0.14em] text-text-3 uppercase">
              Rescue Command Center
            </div>
          </div>
        </div>

        <div className="hidden h-7 w-px bg-line sm:block" aria-hidden />

        <GlobalSearch />

        {/* Live system status. Each figure appears only where there is room
            for it; overlapping numbers are worse than absent ones. */}
        <div className="hidden min-w-0 flex-1 items-center justify-end gap-5 px-2 xl:flex 2xl:justify-center">
          {dataset ? (
            <>
              <div className="hidden shrink-0 items-center gap-1.5 2xl:flex">
                <Radio size={12} className="text-rescuer-text" aria-hidden />
                <span className="num text-[11.5px] font-semibold tracking-wider text-text-2">
                  {dataset.operationName}
                </span>
              </div>
              <div className="hidden h-6 w-px bg-line 2xl:block" aria-hidden />
              <Metric
                label="Critical"
                value={stats.critical}
                tone="var(--color-critical-text)"
                onClick={() => focus(new Set(['CRITICAL']))}
              />
              <Metric
                label="Medical"
                value={stats.medical}
                tone="var(--color-medical-text)"
                onClick={() => focus(new Set(['MEDICAL']))}
              />
              <Metric
                label="Open"
                value={stats.openIncidents}
                onClick={() => {
                  resetFilters();
                  router.push('/emergencies');
                }}
              />
              <Metric
                label="Teams free"
                value={`${stats.availableTeams}/${dataset.teams.length}`}
                tone={stats.availableTeams === 0 ? 'var(--color-warning-text)' : undefined}
                onClick={() => router.push('/teams')}
                className="hidden 2xl:flex"
              />
              <Metric label="People" value={stats.peopleTracked} className="hidden 2xl:flex" />
            </>
          ) : (
            <span className="text-[11.5px] text-text-3">Awaiting operating picture…</span>
          )}
        </div>

        {/* right cluster */}
        <div className="ml-auto flex shrink-0 items-center gap-3 xl:ml-0">
          {/* mode */}
          <div
            className="flex items-center rounded-sm border p-0.5"
            style={{
              borderColor: isDemo ? 'rgb(245 158 11 / 0.45)' : 'rgb(6 182 212 / 0.45)',
              background: isDemo ? 'rgb(245 158 11 / 0.08)' : 'rgb(6 182 212 / 0.08)',
            }}
            role="group"
            aria-label="Data mode"
          >
            {(['DEMO', 'LIVE'] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMode(m)}
                aria-pressed={mode === m}
                className={cx(
                  'flex items-center gap-1 rounded-xs px-2 py-1 text-[10px] font-bold tracking-wider transition-colors',
                  mode === m
                    ? m === 'DEMO'
                      ? 'bg-warning/25 text-warning-text'
                      : 'bg-rescuer/25 text-rescuer-text'
                    : 'text-text-3 hover:text-text-2',
                )}
              >
                {m === 'DEMO' && <FlaskConical size={10} aria-hidden />}
                {m}
              </button>
            ))}
          </div>

          {/* connectivity */}
          <div className="hidden flex-col items-end gap-0.5 lg:flex">
            <div className="flex items-center gap-1.5">
              {connection?.online ? (
                <Wifi size={12} className="text-safe-text" aria-hidden />
              ) : (
                <WifiOff size={12} className="text-critical-text" aria-hidden />
              )}
              <span className="text-[11px] font-medium text-text-2">
                {connection?.online ? 'Internet connected' : 'Offline'}
              </span>
            </div>
            <span className="num text-[10px] text-text-3">
              Sync {connection?.lastSyncedAt ? formatAgo(connection.lastSyncedAt, now) : 'never'}
              {connection?.activeGatewayId ? ` · ${connection.activeGatewayId}` : ''}
            </span>
          </div>

          <Button
            variant="ghost"
            size="sm"
            icon={RefreshCw}
            onClick={reload}
            aria-label="Reload operating picture"
          />

          {/* commander */}
          <div className="flex items-center gap-2 border-l border-line pl-3">
            <div className="hidden text-right leading-tight lg:block">
              <div className="text-[11.5px] font-medium text-text">Cdr. Operations</div>
              <div className="text-[10px] text-text-3">Duty commander</div>
            </div>
            <div className="grid size-7 shrink-0 place-items-center rounded-sm border border-line-2 bg-surface-3 text-[10.5px] font-bold text-text-2">
              CO
            </div>
          </div>
        </div>
      </div>

      {/* ---- context strip: origin, selection, distance ---- */}
      <div className="flex h-8 items-center gap-3 border-t border-line bg-surface-2 px-4 text-[11px]">
        <button
          type="button"
          onClick={requestDeviceLocation}
          className="flex items-center gap-1.5 rounded-xs px-1 py-0.5 text-text-3 transition-colors hover:bg-surface-3 hover:text-text-2"
          title={
            derived.origin.kind === 'DEVICE'
              ? 'Distances are measured from this device'
              : 'Use this browser’s location instead of the command post'
          }
        >
          <Crosshair size={11} aria-hidden />
          <span className="eyebrow">Origin</span>
          <span className="text-text-2">{derived.origin.label}</span>
          {derived.origin.kind === 'COMMAND_POST' && (
            <span className="text-text-3">· click to use this device</span>
          )}
        </button>

        {ops.locationError && (
          <span className="flex items-center gap-1 text-warning-text" role="status">
            <TriangleAlert size={11} aria-hidden />
            {ops.locationError}
          </span>
        )}

        <div className="h-4 w-px bg-line" aria-hidden />

        {selected ? (
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <button
              type="button"
              onClick={() => router.push('/emergencies')}
              className="num shrink-0 font-semibold text-text hover:underline"
            >
              {selected.id}
            </button>
            <CategoryBadge category={selected.category} />
            <PriorityBadge priority={selected.priority} score={selected.breakdown.score} />
            <span className="truncate text-text-3">{selected.sector ?? 'No sector resolved'}</span>

            <div className="ml-auto flex shrink-0 items-center gap-4">
              {/* DISTANCE — the one figure that is never hidden behind a hover */}
              <div className="flex items-center gap-1.5">
                <Navigation size={11} className="text-rescuer-text" aria-hidden />
                <span className="eyebrow">Distance to incident</span>
                {distanceToIncident !== null ? (
                  <>
                    <span className="num text-[13px] font-semibold text-text">
                      {formatDistance(distanceToIncident)}
                    </span>
                    <span className="text-text-3">
                      ETA {formatEta(etaSeconds(distanceToIncident))} · from {distanceOriginLabel}
                    </span>
                  </>
                ) : (
                  <span className="text-warning-text italic">
                    {selected.location
                      ? 'Distance unavailable — no origin position'
                      : 'Distance unavailable — incident has no coordinates'}
                  </span>
                )}
              </div>

              {route && (
                <div className="flex items-center gap-1.5 border-l border-line pl-4">
                  <RouteIcon
                    size={11}
                    className={route.clear ? 'text-safe-text' : 'text-critical-text'}
                    aria-hidden
                  />
                  <span className="num text-[12px] text-text-2">
                    {formatDistance(route.distanceMeters)}
                  </span>
                  <span className={route.clear ? 'text-text-3' : 'text-critical-text'}>
                    {route.clear
                      ? route.method === 'HAZARD_AVOIDING'
                        ? 'hazard-avoiding corridor'
                        : 'direct corridor'
                      : 'no clear corridor'}
                  </span>
                </div>
              )}
            </div>
          </div>
        ) : (
          <span className="flex-1 text-text-3">
            No incident selected — choose one from the queue or the map to see distance and routing.
          </span>
        )}

        <span className="num shrink-0 border-l border-line pl-3 text-text-3">
          {formatClock(now)}
        </span>
      </div>
    </header>
  );
}

/** A small mesh glyph: three linked nodes, one of them the gateway. */
function MeshMark(): React.JSX.Element {
  return (
    <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden className="shrink-0">
      <rect x="0.5" y="0.5" width="25" height="25" rx="5" fill="#0e1223" stroke="#2b3650" />
      <path d="M8 17.5 L13 8.5 L18 17.5" stroke="#334155" strokeWidth="1.2" fill="none" />
      <path d="M8 17.5 L18 17.5" stroke="#334155" strokeWidth="1.2" />
      <circle cx="8" cy="17.5" r="2.1" fill="#0b1020" stroke="#22d3ee" strokeWidth="1.3" />
      <circle cx="18" cy="17.5" r="2.1" fill="#0b1020" stroke="#22d3ee" strokeWidth="1.3" />
      <circle cx="13" cy="8.5" r="2.6" fill="#ef4444" />
    </svg>
  );
}
