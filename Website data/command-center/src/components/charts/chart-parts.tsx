'use client';

import type { ReactNode } from 'react';
import { cx } from '@/components/ui/primitives';

/** Shared axis/grid styling so every chart reads as one system. */
export const AXIS = {
  stroke: '#2b3650',
  tick: { fill: '#94a3b8', fontSize: 10 },
  tickLine: false,
  axisLine: { stroke: '#2b3650' },
} as const;

export const GRID_STROKE = '#1c2437';

interface TooltipPayloadEntry {
  name?: string | number;
  value?: string | number;
  color?: string;
  payload?: Record<string, unknown>;
}

/** Dark tooltip. Recharts' default is a white card. */
export function ChartTooltip({
  active,
  payload,
  label,
  unit,
}: {
  active?: boolean;
  payload?: TooltipPayloadEntry[];
  label?: string | number;
  unit?: string;
}): React.JSX.Element | null {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-sm border border-line-2 bg-surface-3 px-2.5 py-1.5 shadow-[0_10px_28px_rgb(0_0_0/0.6)]">
      {label !== undefined && (
        <p className="num mb-1 text-[10.5px] text-text-3">{String(label)}</p>
      )}
      {payload.map((entry, index) => (
        <p key={index} className="flex items-center gap-1.5 text-[11.5px] text-text-2">
          {entry.color && (
            <span
              className="size-1.5 shrink-0 rounded-full"
              style={{ background: entry.color }}
              aria-hidden
            />
          )}
          <span>{entry.name}</span>
          <span className="num ml-auto pl-3 font-semibold text-text">
            {entry.value}
            {unit ?? ''}
          </span>
        </p>
      ))}
    </div>
  );
}

/**
 * Chart frame with a built-in data table.
 *
 * The table is not a fallback bolted on for compliance — a commander
 * frequently wants the exact figure, and a screen reader needs one. Both get
 * the same numbers the chart was drawn from.
 */
export function ChartFrame({
  title,
  subtitle,
  children,
  table,
  height = 200,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  table?: { headers: string[]; rows: (string | number)[][] };
  height?: number;
}): React.JSX.Element {
  return (
    <figure className="rounded-md border border-line bg-surface-2 p-3">
      <figcaption className="mb-2">
        <h3 className="text-[12.5px] font-semibold text-text">{title}</h3>
        {subtitle && <p className="text-[10.5px] text-text-3">{subtitle}</p>}
      </figcaption>

      <div style={{ height }} className="w-full">
        {children}
      </div>

      {table && (
        <details className="mt-2 border-t border-line pt-2">
          <summary className="cursor-pointer text-[10.5px] text-text-3 hover:text-text-2">
            Show the figures
          </summary>
          <table className="mt-1.5 w-full border-collapse">
            <thead>
              <tr>
                {table.headers.map((heading) => (
                  <th
                    key={heading}
                    scope="col"
                    className="border-b border-line px-1.5 py-1 text-left text-[10px] tracking-wider text-text-3 uppercase"
                  >
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((row, index) => (
                <tr key={index}>
                  {row.map((cell, cellIndex) => (
                    <td
                      key={cellIndex}
                      className={cx(
                        'border-b border-line px-1.5 py-1 text-[11px] last:border-b-0',
                        cellIndex === 0 ? 'text-text-2' : 'num text-text',
                      )}
                    >
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </figure>
  );
}
