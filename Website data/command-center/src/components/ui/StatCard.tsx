'use client';

import type { LucideIcon } from 'lucide-react';
import { cx } from './primitives';

interface StatCardProps {
  label: string;
  value: string | number;
  /** Small qualifier under the figure: units, share, or a stated absence. */
  detail?: string;
  icon?: LucideIcon;
  tone?: string;
  onClick?: () => void;
  /** Marks a figure this console computed rather than received. */
  computed?: boolean;
  emphasis?: boolean;
}

/**
 * One operational figure.
 *
 * Deliberately quiet: no gradient, no oversized number, no card shadow. The
 * emphasis budget on this screen belongs to the recommendation panel and the
 * priority queue, not to a grid of counters.
 */
export function StatCard({
  label,
  value,
  detail,
  icon: Icon,
  tone,
  onClick,
  computed,
  emphasis,
}: StatCardProps): React.JSX.Element {
  const interactive = Boolean(onClick);
  const Element = interactive ? 'button' : 'div';

  return (
    <Element
      {...(interactive ? { type: 'button' as const, onClick } : {})}
      className={cx(
        'group flex min-w-0 flex-col gap-1 rounded-md border p-2.5 text-left transition-colors duration-150',
        emphasis ? 'border-line-2 bg-surface-3' : 'border-line bg-surface-2',
        interactive && 'hover:border-line-2 hover:bg-surface-3',
      )}
      title={interactive ? `Filter the queue to ${label.toLowerCase()}` : undefined}
    >
      <div className="flex items-center gap-1.5">
        {Icon && (
          <Icon size={12} strokeWidth={2} style={{ color: tone ?? 'var(--color-text-3)' }} aria-hidden />
        )}
        <span className="eyebrow leading-tight">{label}</span>
      </div>
      <span
        className="num text-[24px] leading-none font-semibold tracking-tight"
        style={{ color: tone ?? 'var(--color-text)' }}
      >
        {value}
      </span>
      <span className="truncate text-[10.5px] text-text-3">
        {detail ?? (computed ? 'computed here' : ' ')}
      </span>
    </Element>
  );
}
