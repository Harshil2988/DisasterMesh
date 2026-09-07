'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { CircleCheck, Info, Siren, TriangleAlert, X } from 'lucide-react';
import { useOps, type Alert } from '@/state/ops-store';
import { formatClock } from '@/lib/format';
import { cx } from '@/components/ui/primitives';

const TONE: Record<Alert['severity'], { icon: typeof Info; color: string; rgb: string }> = {
  CRITICAL: { icon: Siren, color: 'var(--color-critical-text)', rgb: '239 68 68' },
  WARNING: { icon: TriangleAlert, color: 'var(--color-warning-text)', rgb: '245 158 11' },
  SUCCESS: { icon: CircleCheck, color: 'var(--color-safe-text)', rgb: '34 197 94' },
  INFO: { icon: Info, color: 'var(--color-rescuer-text)', rgb: '6 182 212' },
};

/** Non-critical alerts clear themselves; a critical one waits to be read. */
const AUTO_DISMISS_MS = 9000;

function AlertCard({ alert }: { alert: Alert }): React.JSX.Element {
  const router = useRouter();
  const { dismissAlert, selectEmergency } = useOps();
  const tone = TONE[alert.severity];
  const Icon = tone.icon;

  useEffect(() => {
    if (alert.severity === 'CRITICAL') return;
    const timer = window.setTimeout(() => dismissAlert(alert.id), AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
  }, [alert.id, alert.severity, dismissAlert]);

  return (
    <div
      role={alert.severity === 'CRITICAL' ? 'alert' : 'status'}
      className="anim-in pointer-events-auto flex w-[320px] items-start gap-2.5 rounded-md border bg-surface-2/95 p-2.5 shadow-[0_12px_32px_rgb(0_0_0/0.55)] backdrop-blur"
      style={{ borderColor: `rgb(${tone.rgb} / 0.4)` }}
    >
      <Icon
        size={14}
        className={cx('mt-px shrink-0', alert.severity === 'CRITICAL' && 'anim-sos')}
        style={{ color: tone.color }}
        aria-hidden
      />
      <div className="min-w-0 flex-1">
        <p className="text-[12px] leading-snug font-medium text-text">{alert.title}</p>
        {alert.detail && <p className="mt-0.5 text-[11px] text-text-3">{alert.detail}</p>}
        <div className="mt-1.5 flex items-center gap-2">
          <span className="num text-[10px] text-text-3">{formatClock(alert.at)}</span>
          {alert.emergencyId && (
            <button
              type="button"
              onClick={() => {
                selectEmergency(alert.emergencyId!);
                router.push('/emergencies');
                dismissAlert(alert.id);
              }}
              className="text-[10.5px] font-medium text-rescuer-text hover:underline"
            >
              Open incident
            </button>
          )}
        </div>
      </div>
      <button
        type="button"
        onClick={() => dismissAlert(alert.id)}
        aria-label="Dismiss alert"
        className="shrink-0 text-text-3 transition-colors hover:text-text"
      >
        <X size={13} />
      </button>
    </div>
  );
}

export function AlertStack(): React.JSX.Element {
  const { alerts } = useOps();
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed top-[70px] right-4 z-[900] flex flex-col gap-2"
    >
      {alerts.map((alert) => (
        <AlertCard key={alert.id} alert={alert} />
      ))}
    </div>
  );
}
