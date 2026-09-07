'use client';

import { ArrowDown, ArrowRight, HardDrive, Monitor, Radio, Smartphone, Wifi } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { cx } from '@/components/ui/primitives';

/**
 * The offline-to-online path, drawn from the data actually held.
 *
 * Its job is to make one thing obvious: everything to the left of the gateway
 * happened with no internet at all. The counts are read from the dataset, so
 * an empty live deployment renders an honest chain of zeros rather than a
 * decorative diagram of a system that is not running.
 */
export function PipelineDiagram({ orientation = 'vertical' }: { orientation?: 'vertical' | 'horizontal' }) {
  const { dataset, derived } = useOps();
  if (!dataset) return null;

  const civilians = dataset.meshNodes.filter((n) => n.kind === 'CIVILIAN').length;
  const rescuers = dataset.meshNodes.filter((n) => n.kind === 'RESCUER').length;
  const gateways = dataset.connection.gatewaysOnline;

  const stages = [
    {
      icon: Smartphone,
      label: 'Civilian mesh',
      value: civilians > 0 ? `${civilians} nodes` : 'No nodes reported',
      detail: 'Phone-to-phone. No cell network, no internet.',
      colour: 'var(--color-text-3)',
      offline: true,
    },
    {
      icon: Radio,
      label: 'Rescuer device',
      value: rescuers > 0 ? `${rescuers} rescuers` : 'None reported',
      detail: 'Joins the mesh and collects what it can hear.',
      colour: 'var(--color-rescuer-text)',
      offline: true,
    },
    {
      icon: HardDrive,
      label: 'Offline store',
      value: `${dataset.connection.pendingUploads} pending`,
      detail: 'Held on the device until a path out exists.',
      colour: 'var(--color-warning-text)',
      offline: true,
    },
    {
      icon: Wifi,
      label: 'Gateway uplink',
      value: gateways > 0 ? `${gateways} online` : 'Waiting for gateway',
      detail: 'The first point at which internet is required.',
      colour: gateways > 0 ? 'var(--color-safe-text)' : 'var(--color-warning-text)',
      offline: false,
    },
    {
      icon: Monitor,
      label: 'Command centre',
      value: `${derived.emergencies.length} reports`,
      detail: 'Prioritisation, assignment, routing.',
      colour: 'var(--color-system-text)',
      offline: false,
    },
  ];

  return (
    <div className={cx(orientation === 'horizontal' ? 'flex items-stretch gap-2' : 'space-y-0')}>
      {stages.map((stage, index) => {
        const Icon = stage.icon;
        const last = index === stages.length - 1;

        if (orientation === 'horizontal') {
          return (
            <div key={stage.label} className="flex min-w-0 flex-1 items-center gap-2">
              <div className="min-w-0 flex-1 rounded-sm border border-line bg-surface-3 p-2.5">
                <Icon size={14} style={{ color: stage.colour }} aria-hidden />
                <p className="mt-1.5 truncate text-[11.5px] font-medium text-text">{stage.label}</p>
                <p className="num truncate text-[11px]" style={{ color: stage.colour }}>
                  {stage.value}
                </p>
                <p className="mt-1 text-[10px] leading-tight text-text-3">{stage.detail}</p>
                <p
                  className={cx(
                    'mt-1.5 inline-block rounded-xs border px-1 py-px text-[9px] font-semibold tracking-wider uppercase',
                    stage.offline
                      ? 'border-line-2 text-text-3'
                      : 'border-safe/40 text-safe-text',
                  )}
                >
                  {stage.offline ? 'no internet' : 'internet'}
                </p>
              </div>
              {/* A real arrow at readable contrast: the → character rendered
                  at 1.6:1 against the panel, which is invisible in practice. */}
              {!last && <ArrowRight size={13} className="shrink-0 text-text-3" aria-hidden />}
            </div>
          );
        }

        return (
          <div key={stage.label}>
            <div className="flex items-start gap-2.5">
              <div className="flex flex-col items-center self-stretch">
                <span
                  className="grid size-6 shrink-0 place-items-center rounded-sm border border-line bg-surface-3"
                  style={{ borderColor: `color-mix(in srgb, ${stage.colour} 35%, transparent)` }}
                >
                  <Icon size={12} style={{ color: stage.colour }} aria-hidden />
                </span>
                {!last && <span className="w-px flex-1 bg-line" aria-hidden />}
              </div>

              <div className={cx('min-w-0 flex-1', !last && 'pb-2.5')}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="truncate text-[11.5px] font-medium text-text">{stage.label}</span>
                  <span className="num shrink-0 text-[10.5px]" style={{ color: stage.colour }}>
                    {stage.value}
                  </span>
                </div>
                <p className="text-[10px] leading-tight text-text-3">{stage.detail}</p>
              </div>
            </div>
            {!last && <ArrowDown size={0} className="sr-only" aria-hidden />}
          </div>
        );
      })}

      {orientation === 'vertical' && (
        <p className="mt-1 border-t border-line pt-2 text-[10px] leading-relaxed text-text-3">
          Only the last two stages need internet. Everything before them works with no network at
          all.
        </p>
      )}
    </div>
  );
}
