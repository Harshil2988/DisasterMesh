'use client';

import { useMemo } from 'react';
import { BedDouble, Hospital as HospitalIcon, Route as RouteIcon } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { GeoPoint, Hospital } from '@/lib/types';
import { HOSPITAL_STATUS_META } from '@/lib/constants';
import { etaSeconds, formatDistance, formatEta, safeDistanceMeters } from '@/lib/geo';
import { Button, EmptyState, StatusBadge } from '@/components/ui/primitives';

interface Ranked {
  hospital: Hospital;
  meters: number | null;
  score: number;
  reasons: string[];
}

/** Facilities that cannot receive anyone are ranked out, not hidden. */
const STATUS_WEIGHT: Record<Hospital['status'], number> = {
  OPEN: 40,
  LIMITED: 22,
  NEAR_CAPACITY: 10,
  FULL: -40,
  UNKNOWN: 0,
};

/**
 * Where to take this casualty.
 *
 * Ranks on distance first, then reported capacity, then trauma capability —
 * and prints the reasoning next to each option, because "nearest" and "best"
 * are frequently different hospitals and the commander picks, not the console.
 */
export function HospitalRecommendation({
  location,
  compact,
}: {
  location: GeoPoint | null;
  compact?: boolean;
}): React.JSX.Element {
  const { dataset, selectHospital } = useOps();

  const ranked = useMemo<Ranked[]>(() => {
    if (!dataset) return [];
    return dataset.hospitals
      .map<Ranked>((hospital) => {
        const meters = safeDistanceMeters(location, hospital.location);
        const reasons: string[] = [];

        // Distance term: full marks inside 1 km, decaying to zero at 12 km.
        const distanceScore =
          meters === null ? 0 : Math.max(0, 50 - Math.max(0, (meters - 1000) / 12000) * 50);
        if (meters !== null) reasons.push(`${formatDistance(meters)} away`);

        const statusScore = STATUS_WEIGHT[hospital.status];
        reasons.push(HOSPITAL_STATUS_META[hospital.status].label.toLowerCase());

        const beds = hospital.emergencyBeds;
        const bedScore = beds === undefined ? 0 : Math.min(20, beds * 2);
        reasons.push(
          beds === undefined ? 'capacity not reported' : `${beds} emergency beds reported free`,
        );

        const traumaScore = hospital.traumaCapable ? 12 : 0;
        if (hospital.traumaCapable) reasons.push('trauma capable');

        return {
          hospital,
          meters,
          score: distanceScore + statusScore + bedScore + traumaScore,
          reasons,
        };
      })
      .sort((a, b) => b.score - a.score);
  }, [dataset, location]);

  if (!location) {
    return (
      <EmptyState
        icon={HospitalIcon}
        title="No destination ranking available"
        detail="This report carries no coordinates, so hospitals cannot be ranked by distance."
      />
    );
  }

  if (ranked.length === 0) {
    return (
      <EmptyState
        icon={HospitalIcon}
        title="No hospitals on record"
        detail="Hospital records are held at the command centre. None have been added for this operation."
      />
    );
  }

  return (
    <div className="space-y-1.5">
      {ranked.slice(0, compact ? 2 : 4).map((entry, index) => {
        const meta = HOSPITAL_STATUS_META[entry.hospital.status];
        return (
          <div
            key={entry.hospital.id}
            className="rounded-sm border border-line bg-surface-3 p-2.5"
            style={index === 0 ? { borderColor: 'rgb(34 197 94 / 0.35)' } : undefined}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  {index === 0 && (
                    <span className="rounded-xs border border-safe/40 bg-safe/12 px-1 py-px text-[9.5px] font-bold tracking-wider text-safe-text uppercase">
                      Recommended
                    </span>
                  )}
                  <span className="truncate text-[12.5px] font-medium text-text">
                    {entry.hospital.name}
                  </span>
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <StatusBadge status={entry.hospital.status} kind="hospital" />
                  {entry.hospital.emergencyBeds !== undefined && (
                    <span className="flex items-center gap-1 text-[11px] text-text-3">
                      <BedDouble size={10} aria-hidden />
                      <span className="num">{entry.hospital.emergencyBeds}</span> emergency
                    </span>
                  )}
                </div>
                <p className="mt-1 text-[10.5px] text-text-3">{entry.reasons.join(' · ')}</p>
              </div>

              <div className="shrink-0 text-right">
                <div className="num text-[16px] leading-none font-semibold" style={{ color: meta.text }}>
                  {formatDistance(entry.meters)}
                </div>
                <div className="num mt-1 text-[10px] text-text-3">
                  {entry.meters !== null ? formatEta(etaSeconds(entry.meters)) : '—'}
                </div>
              </div>
            </div>

            {!compact && (
              <div className="mt-2 flex gap-1.5">
                <Button size="sm" icon={HospitalIcon} onClick={() => selectHospital(entry.hospital.id)}>
                  View hospital
                </Button>
                <Button size="sm" variant="ghost" icon={RouteIcon} disabled title="Hospital routing uses the same corridor router as incident routing">
                  Route from scene
                </Button>
              </div>
            )}
          </div>
        );
      })}

      <p className="pt-1 text-[9.5px] leading-relaxed text-text-3">
        Ranked by distance, reported capacity and trauma capability. Capacity figures are only as
        current as the last update from each facility.
      </p>
    </div>
  );
}
