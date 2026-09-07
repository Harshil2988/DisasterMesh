'use client';

import { Navigation, TriangleAlert } from 'lucide-react';
import { etaSeconds, formatDistance, formatEta } from '@/lib/geo';
import { cx } from '@/components/ui/primitives';

interface DistanceIndicatorProps {
  meters: number | null;
  originLabel: string;
  /** Stated when the distance cannot be computed. Never fabricated. */
  unavailableReason?: string;
  size?: 'sm' | 'lg';
  label?: string;
}

/**
 * The distance readout.
 *
 * Prominent by design and never hidden behind a hover: it is the figure that
 * decides which team goes where, so it gets the same treatment everywhere it
 * appears. When it cannot be computed it says why, and shows nothing else —
 * a plausible-looking number here would be the most dangerous thing on the
 * screen.
 */
export function DistanceIndicator({
  meters,
  originLabel,
  unavailableReason,
  size = 'lg',
  label = 'Distance to incident',
}: DistanceIndicatorProps): React.JSX.Element {
  if (meters === null) {
    return (
      <div className="flex items-start gap-2 rounded-sm border border-warning/30 bg-warning/8 px-2.5 py-2">
        <TriangleAlert size={13} className="mt-px shrink-0 text-warning-text" aria-hidden />
        <div>
          <p className="eyebrow">{label}</p>
          <p className="text-[11.5px] text-warning-text">
            {unavailableReason ?? 'Distance unavailable — location unavailable'}
          </p>
        </div>
      </div>
    );
  }

  const seconds = etaSeconds(meters);

  return (
    <div className="rounded-sm border border-rescuer/25 bg-rescuer/8 px-2.5 py-2">
      <div className="flex items-center gap-1.5">
        <Navigation size={11} className="text-rescuer-text" aria-hidden />
        <span className="eyebrow">{label}</span>
      </div>
      <div className="mt-1 flex items-baseline gap-2.5">
        <span
          className={cx(
            'num font-semibold tracking-tight text-text',
            size === 'lg' ? 'text-[26px] leading-none' : 'text-[17px] leading-none',
          )}
        >
          {formatDistance(meters)}
        </span>
        <span className="text-[11.5px] text-text-2">
          ETA <span className="num font-medium text-text">{formatEta(seconds)}</span>
        </span>
      </div>
      <p className="mt-1 text-[10px] text-text-3">
        Straight-line from {originLabel} · travel estimate at 26 km/h ground speed
      </p>
    </div>
  );
}
