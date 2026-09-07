'use client';

import type { ReactNode } from 'react';
import { FlaskConical } from 'lucide-react';
import { useOps } from '@/state/ops-store';

/**
 * Page title block.
 *
 * Carries the demonstration marker on every screen rather than only in the
 * header, so a screenshot of any single page still declares what it is.
 */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle: string;
  actions?: ReactNode;
}): React.JSX.Element {
  const { mode, dataset } = useOps();

  return (
    <header className="flex shrink-0 items-start justify-between gap-4 border-b border-line px-5 py-3.5">
      <div className="min-w-0">
        <div className="flex items-center gap-2.5">
          <h1 className="text-[17px] leading-none font-semibold tracking-tight text-text uppercase">
            {title}
          </h1>
          {mode === 'DEMO' ? (
            <span className="flex items-center gap-1 rounded-xs border border-warning/40 bg-warning/12 px-1.5 py-0.5 text-[9.5px] font-bold tracking-widest text-warning-text uppercase">
              <FlaskConical size={9} aria-hidden />
              Demo data
            </span>
          ) : (
            <span className="rounded-xs border border-rescuer/40 bg-rescuer/12 px-1.5 py-0.5 text-[9.5px] font-bold tracking-widest text-rescuer-text uppercase">
              Live data
            </span>
          )}
          {dataset && (
            <span className="num text-[10.5px] text-text-3">{dataset.operationName}</span>
          )}
        </div>
        <p className="mt-1.5 text-[12px] text-text-3">{subtitle}</p>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-1.5">{actions}</div>}
    </header>
  );
}
