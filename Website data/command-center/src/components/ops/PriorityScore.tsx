'use client';

import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import type { PriorityBreakdown } from '@/lib/types';
import { PRIORITY_META } from '@/lib/constants';
import { SCORING_MODEL_NOTE } from '@/lib/priority';
import { cx } from '@/components/ui/primitives';

/**
 * The score, and the arithmetic behind it.
 *
 * The breakdown is one click away rather than hidden in a tooltip, because a
 * commander overruling the queue order needs to see what the queue was
 * weighing — and because an unexplainable number invites either blind trust
 * or blanket distrust, and both are dangerous.
 */
export function PriorityScore({
  breakdown,
  defaultOpen = false,
}: {
  breakdown: PriorityBreakdown;
  defaultOpen?: boolean;
}): React.JSX.Element {
  const [open, setOpen] = useState(defaultOpen);
  const meta = PRIORITY_META[breakdown.level];

  return (
    <div className="rounded-sm border border-line bg-surface-3">
      <div className="flex items-center gap-3 px-3 py-2.5">
        <div className="flex items-baseline gap-1">
          <span className="num text-[28px] leading-none font-bold" style={{ color: meta.text }}>
            {breakdown.score}
          </span>
          <span className="num text-[13px] text-text-3">/100</span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <span className="text-[12px] font-bold" style={{ color: meta.text }}>
              {breakdown.level}
            </span>
            <span className="text-[11.5px] text-text-2">— {meta.label}</span>
          </div>
          <p className="truncate text-[11px] text-text-3">{breakdown.summary}</p>
        </div>
      </div>

      {/* A track, so the score reads as a position on a scale rather than a
          bare number whose range the reader has to guess. */}
      <div className="px-3 pb-2">
        <div className="h-1 overflow-hidden rounded-full bg-surface-4">
          <div
            className="h-full rounded-full transition-[width] duration-500"
            style={{ width: `${breakdown.score}%`, background: meta.mark }}
          />
        </div>
      </div>

      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-1.5 border-t border-line px-3 py-1.5 text-left text-[10.5px] text-text-3 transition-colors hover:bg-surface-4 hover:text-text-2"
      >
        <span className="flex-1">
          {open ? 'Hide' : 'Show'} how this score was calculated
        </span>
        <ChevronDown
          size={11}
          className={cx('transition-transform duration-200', open && 'rotate-180')}
          aria-hidden
        />
      </button>

      {open && (
        <div className="space-y-1.5 border-t border-line px-3 py-2">
          <table className="w-full">
            <caption className="sr-only">Priority score factors</caption>
            <tbody>
              {breakdown.factors.map((factor) => (
                <tr key={factor.label} className="align-top">
                  <th scope="row" className="py-0.5 pr-2 text-left text-[11px] font-medium text-text-2">
                    {factor.label}
                    <span className="block text-[10px] font-normal text-text-3">{factor.detail}</span>
                  </th>
                  <td
                    className={cx(
                      'num w-10 py-0.5 text-right text-[11.5px] font-semibold',
                      factor.points > 0
                        ? 'text-text'
                        : factor.points < 0
                          ? 'text-safe-text'
                          : 'text-text-3',
                    )}
                  >
                    {factor.points > 0 ? '+' : ''}
                    {factor.points}
                  </td>
                </tr>
              ))}
              <tr className="border-t border-line">
                <th scope="row" className="pt-1 text-left text-[11px] font-semibold text-text">
                  Total
                </th>
                <td className="num pt-1 text-right text-[12px] font-bold text-text">
                  {breakdown.score}
                </td>
              </tr>
            </tbody>
          </table>
          <p className="text-[9.5px] leading-relaxed text-text-3">{SCORING_MODEL_NOTE}</p>
        </div>
      )}
    </div>
  );
}
