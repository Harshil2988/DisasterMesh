'use client';

import { useState } from 'react';
import {
  ChevronDown,
  FlaskConical,
  HeartPulse,
  Package,
  RefreshCw,
  ShieldCheck,
  Siren,
  TriangleAlert,
  Zap,
} from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { Button, cx } from '@/components/ui/primitives';
import type { Category } from '@/lib/types';

const GENERATORS: { category: Category; label: string; icon: typeof Siren }[] = [
  { category: 'CRITICAL', label: 'SOS', icon: Siren },
  { category: 'MEDICAL', label: 'Medical', icon: HeartPulse },
  { category: 'WARNING', label: 'Warning', icon: TriangleAlert },
  { category: 'SUPPLY', label: 'Supply', icon: Package },
  { category: 'SAFE', label: 'Safe', icon: ShieldCheck },
];

/**
 * Demonstration console.
 *
 * Present only in DEMO mode, and visibly a test instrument rather than an
 * operational control — the whole point is that nobody can mistake a
 * generated incident for a reported one.
 */
export function DemoControls(): React.JSX.Element | null {
  const { mode, demoGenerateReport, demoSimulateSync, demoCompleteRescue, dataset } = useOps();
  const [open, setOpen] = useState(false);

  if (mode !== 'DEMO') return null;

  const activeMission = dataset?.missions.find(
    (m) => m.status !== 'COMPLETED' && m.status !== 'ABORTED',
  );

  return (
    <div className="w-full">
      <div
        className={cx(
          'overflow-hidden rounded-md border bg-surface-2/97 shadow-[0_12px_40px_rgb(0_0_0/0.6)] backdrop-blur',
          'border-warning/40',
        )}
      >
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          aria-expanded={open}
          className="flex w-full items-center gap-1.5 bg-warning/10 px-2.5 py-1.5 text-left transition-colors hover:bg-warning/15"
        >
          <FlaskConical size={13} className="text-warning-text" aria-hidden />
          <span className="flex-1 truncate text-[10px] font-bold tracking-wider text-warning-text uppercase">
            Demo controls
          </span>
          <ChevronDown
            size={13}
            className={cx('text-warning-text transition-transform duration-200', open && 'rotate-180')}
            aria-hidden
          />
        </button>

        {open && (
          <div className="space-y-2.5 p-2.5">
            <p className="text-[10.5px] leading-relaxed text-text-3">
              Injects records into the demonstration dataset only. Nothing here reaches a real
              network or a real rescue team.
            </p>

            <div>
              <p className="eyebrow mb-1.5">Inject a report</p>
              <div className="grid grid-cols-3 gap-1.5">
                {GENERATORS.map(({ category, label, icon: Icon }) => (
                  <button
                    key={category}
                    type="button"
                    onClick={() => demoGenerateReport(category)}
                    className="flex flex-col items-center gap-1 rounded-sm border border-line bg-surface-3 py-1.5 text-[10px] text-text-2 transition-colors hover:border-line-2 hover:bg-surface-4 hover:text-text"
                  >
                    <Icon size={13} aria-hidden />
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <p className="eyebrow">Pipeline</p>
              <Button block size="sm" icon={RefreshCw} onClick={demoSimulateSync}>
                Simulate gateway sync
              </Button>
              <Button
                block
                size="sm"
                icon={Zap}
                onClick={demoCompleteRescue}
                disabled={!activeMission}
                title={
                  activeMission
                    ? `Advance ${activeMission.id} to its next status`
                    : 'No active mission to advance'
                }
              >
                {activeMission ? 'Advance active mission' : 'No active mission'}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
