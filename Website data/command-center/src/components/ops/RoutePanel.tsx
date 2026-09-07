'use client';

import { CircleCheck, CornerDownRight, Route as RouteIcon, TriangleAlert } from 'lucide-react';
import type { Hazard, RouteSolution } from '@/lib/types';
import { ROUTING_METHOD_NOTE, detourCost } from '@/lib/routing';
import { formatDistance, formatEta } from '@/lib/geo';
import { EmptyState, cx } from '@/components/ui/primitives';

/**
 * Route result.
 *
 * The headline is not the distance — it is whether the corridor is clear.
 * A commander reading this panel is deciding whether to send people into a
 * flood zone, so "no clear corridor" gets the same visual weight as a P0.
 */
export function RoutePanel({
  route,
  hazards,
  error,
}: {
  route: RouteSolution | null;
  hazards: Hazard[];
  error: string | null;
}): React.JSX.Element {
  if (error) {
    return (
      <div className="flex items-start gap-2 rounded-sm border border-warning/30 bg-warning/8 px-2.5 py-2">
        <TriangleAlert size={13} className="mt-px shrink-0 text-warning-text" aria-hidden />
        <p className="text-[11.5px] text-warning-text">{error}</p>
      </div>
    );
  }

  if (!route) {
    return (
      <EmptyState
        icon={RouteIcon}
        title="No route calculated"
        detail="Calculate a route to see the corridor, its length, and any hazard zones on the way."
      />
    );
  }

  const blocking = hazards.filter((h) => route.blockedByHazardIds.includes(h.id));
  const detour = detourCost(route);

  const label = (ids: string[]): string =>
    ids.map((id) => hazards.find((h) => h.id === id)?.label ?? id).join(', ');

  /**
   * When an endpoint sits inside a hazard zone, no corridor can exist by
   * construction. Saying that plainly is far more useful than "no path found",
   * because the fix is different: move the team, or reassess the zone.
   */
  const failureReason =
    route.originInHazardIds.length > 0 && route.destinationInHazardIds.length > 0
      ? `Both ends of this route are inside hazard zones — the origin is within ${label(
          route.originInHazardIds,
        )} and the incident is within ${label(route.destinationInHazardIds)}.`
      : route.originInHazardIds.length > 0
        ? `The origin is already inside ${label(
            route.originInHazardIds,
          )}. No corridor out of that zone can be clear until the team moves clear of it, or the zone is reassessed.`
        : route.destinationInHazardIds.length > 0
          ? `The incident itself lies inside ${label(
              route.destinationInHazardIds,
            )}. Any approach must enter that zone — plan for it rather than around it.`
          : `The direct line crosses ${blocking.length} active hazard ${
              blocking.length === 1 ? 'zone' : 'zones'
            }, and no path around ${
              blocking.length === 1 ? 'it' : 'them'
            } could be found. Treat this corridor as unsafe.`;

  return (
    <div className="space-y-2.5">
      {/* verdict */}
      <div
        className={cx(
          'flex items-start gap-2 rounded-sm border px-2.5 py-2',
          route.clear ? 'border-safe/30 bg-safe/8' : 'border-critical/40 bg-critical/10',
        )}
      >
        {route.clear ? (
          <CircleCheck size={14} className="mt-px shrink-0 text-safe-text" aria-hidden />
        ) : (
          <TriangleAlert size={14} className="mt-px shrink-0 text-critical-text" aria-hidden />
        )}
        <div className="min-w-0">
          <p
            className={cx(
              'text-[12px] font-semibold',
              route.clear ? 'text-safe-text' : 'text-critical-text',
            )}
          >
            {route.clear
              ? route.method === 'HAZARD_AVOIDING'
                ? 'Clear corridor found — detours around known hazards'
                : 'Clear corridor — no known hazard on the direct line'
              : 'No clear corridor exists'}
          </p>
          <p className="mt-0.5 text-[11px] text-text-2">
            {route.clear ? 'Every leg below stays outside all active hazard zones.' : failureReason}
          </p>
        </div>
      </div>

      {/* figures */}
      <dl className="grid grid-cols-3 gap-2">
        <div className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
          <dt className="eyebrow">Distance</dt>
          <dd className="num mt-0.5 text-[17px] leading-none font-semibold text-text">
            {formatDistance(route.distanceMeters)}
          </dd>
        </div>
        <div className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
          <dt className="eyebrow">Est. travel</dt>
          <dd className="num mt-0.5 text-[17px] leading-none font-semibold text-text">
            {formatEta(route.etaSeconds)}
          </dd>
        </div>
        <div className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
          <dt className="eyebrow">Detour cost</dt>
          <dd className="num mt-0.5 text-[17px] leading-none font-semibold text-text">
            {detour > 20 ? `+${formatDistance(detour)}` : '—'}
          </dd>
        </div>
      </dl>

      <div className="space-y-1 text-[11px]">
        <div className="flex gap-2">
          <span className="w-16 shrink-0 text-text-3">Origin</span>
          <span className="truncate text-text-2">{route.originLabel}</span>
        </div>
        <div className="flex gap-2">
          <span className="w-16 shrink-0 text-text-3">Destination</span>
          <span className="truncate text-text-2">{route.destinationLabel}</span>
        </div>
        <div className="flex gap-2">
          <span className="w-16 shrink-0 text-text-3">Method</span>
          <span className="text-text-2">
            {route.method === 'HAZARD_AVOIDING' ? 'Hazard-avoiding corridor' : 'Direct corridor'}
          </span>
        </div>
      </div>

      {/* legs */}
      <div>
        <p className="eyebrow mb-1">
          {route.legs.length} {route.legs.length === 1 ? 'leg' : 'legs'}
        </p>
        <ol className="space-y-0.5">
          {route.legs.map((leg, index) => (
            <li
              key={index}
              className="flex items-center gap-2 rounded-xs px-1.5 py-1 text-[11px] odd:bg-surface-3/60"
            >
              <CornerDownRight size={10} className="shrink-0 text-text-3" aria-hidden />
              <span className="num w-4 shrink-0 text-text-3">{index + 1}</span>
              <span className="num w-16 shrink-0 text-text-2">
                {formatDistance(leg.distanceMeters)}
              </span>
              {leg.hazardIds.length > 0 ? (
                <span className="truncate text-critical-text">
                  crosses {leg.hazardIds.map((id) => hazards.find((h) => h.id === id)?.label ?? id).join(', ')}
                </span>
              ) : (
                <span className="text-text-3">clear of all known hazards</span>
              )}
            </li>
          ))}
        </ol>
      </div>

      {blocking.length > 0 && (
        <ul className="space-y-1">
          {blocking.map((hazard) => (
            <li
              key={hazard.id}
              className="flex items-start gap-2 rounded-sm border border-critical/25 bg-critical/8 px-2 py-1.5 text-[11px] text-critical-text"
            >
              <TriangleAlert size={11} className="mt-0.5 shrink-0" aria-hidden />
              <span>
                {hazard.label} — avoidance radius {hazard.radiusMeters} m
              </span>
            </li>
          ))}
        </ul>
      )}

      <p className="border-t border-line pt-2 text-[9.5px] leading-relaxed text-text-3">
        {ROUTING_METHOD_NOTE}
      </p>
    </div>
  );
}
