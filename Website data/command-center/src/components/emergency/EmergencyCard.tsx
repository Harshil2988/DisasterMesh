'use client';

import { Clock, Headphones, Image as ImageIcon, Navigation, Users } from 'lucide-react';
import type { ScoredEmergency } from '@/lib/recommendations';
import { CATEGORY_META, PRIORITY_META } from '@/lib/constants';
import { formatDistance } from '@/lib/geo';
import { formatDuration } from '@/lib/format';
import { CategoryBadge, StatusBadge, cx } from '@/components/ui/primitives';

interface EmergencyCardProps {
  emergency: ScoredEmergency;
  selected: boolean;
  onSelect: () => void;
  teamName?: string | null;
  now: number;
}

/**
 * One row in the priority queue.
 *
 * Ordered so the eye lands on severity, then identity, then the numbers that
 * decide dispatch. The left rule carries the category colour at full
 * saturation — it is the only place a 3:1 mark can be that large — while all
 * text uses the lighter 4.5:1 tint.
 */
export function EmergencyCard({
  emergency,
  selected,
  onSelect,
  teamName,
  now,
}: EmergencyCardProps): React.JSX.Element {
  const meta = CATEGORY_META[emergency.category];
  const priorityMeta = PRIORITY_META[emergency.priority];
  const waited = now - emergency.reportedAt;
  const isUrgent = emergency.category === 'CRITICAL' && emergency.status === 'UNASSIGNED';

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={selected ? 'true' : undefined}
      className={cx(
        'relative flex w-full flex-col gap-1.5 border-b border-line py-2.5 pr-3 pl-3.5 text-left transition-colors duration-150',
        selected ? 'bg-surface-3' : 'hover:bg-surface-2',
      )}
    >
      {/* category rule */}
      <span
        className={cx('absolute top-0 bottom-0 left-0 w-[3px]', isUrgent && 'anim-sos')}
        style={{ background: meta.mark }}
        aria-hidden
      />

      <div className="flex items-center gap-2">
        <CategoryBadge category={emergency.category} />
        <span className="num text-[12.5px] font-semibold text-text">{emergency.id}</span>
        <span
          className="num ml-auto shrink-0 text-[11px] font-bold"
          style={{ color: priorityMeta.text }}
          title={`${emergency.priority} — ${priorityMeta.label} · score ${emergency.breakdown.score}/100`}
        >
          {emergency.priority}
          <span className="ml-1 font-medium text-text-3">{emergency.breakdown.score}</span>
        </span>
      </div>

      <p className="line-clamp-2 text-[12px] leading-snug text-text-2">{emergency.description}</p>

      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[10.5px] text-text-3">
        <span className="truncate">{emergency.sector ?? 'No sector resolved'}</span>

        <span className="flex items-center gap-1" title="People affected">
          <Users size={10} aria-hidden />
          <span className="num">{emergency.peopleAffected ?? '—'}</span>
        </span>

        <span className="flex items-center gap-1" title="Waiting since first report">
          <Clock size={10} aria-hidden />
          <span className={cx('num', waited > 30 * 60_000 && 'text-warning-text')}>
            {formatDuration(waited)}
          </span>
        </span>

        <span className="flex items-center gap-1" title="Distance from the current origin">
          <Navigation size={10} aria-hidden />
          <span className="num">{formatDistance(emergency.distanceFromCommand)}</span>
        </span>

        {emergency.audioClipId && (
          <Headphones size={10} aria-hidden className="text-system-text" />
        )}
        {emergency.evidenceIds.length > 0 && (
          <ImageIcon size={10} aria-hidden className="text-system-text" />
        )}
      </div>

      <div className="flex items-center gap-2">
        <StatusBadge status={emergency.status} />
        {teamName && (
          <span className="truncate text-[10.5px] text-rescuer-text">→ {teamName}</span>
        )}
      </div>
    </button>
  );
}
