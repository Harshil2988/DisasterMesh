'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { CircleCheck, Clock, Info, Siren, TriangleAlert } from 'lucide-react';
import { useOps, TIMELINE_CHANNELS } from '@/state/ops-store';
import type { TimelineChannel, TimelineEvent } from '@/lib/types';
import { TIMELINE_CHANNEL_META } from '@/lib/constants';
import { formatClockSeconds, formatDateTime } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, SourceTag, cx } from '@/components/ui/primitives';

const SEVERITY_ICON = {
  CRITICAL: Siren,
  WARNING: TriangleAlert,
  SUCCESS: CircleCheck,
  INFO: Info,
} as const;

const SEVERITY_COLOUR = {
  CRITICAL: 'var(--color-critical-text)',
  WARNING: 'var(--color-warning-text)',
  SUCCESS: 'var(--color-safe-text)',
  INFO: 'var(--color-text-3)',
} as const;

export default function TimelinePage(): React.JSX.Element {
  const router = useRouter();
  const { dataset, selectEmergency } = useOps();
  const [channels, setChannels] = useState<Set<TimelineChannel>>(new Set(TIMELINE_CHANNELS));

  const events = useMemo(
    () => (dataset?.timeline ?? []).filter((e) => channels.has(e.channel)).slice(0, 400),
    [dataset, channels],
  );

  if (!dataset) return <></>;

  const toggle = (channel: TimelineChannel): void => {
    setChannels((current) => {
      const next = new Set(current);
      if (next.has(channel)) next.delete(channel);
      else next.add(channel);
      return next;
    });
  };

  /** Events are grouped by day so a multi-day operation stays readable. */
  const groups = events.reduce<Record<string, TimelineEvent[]>>((acc, event) => {
    const key = new Date(event.at).toDateString();
    (acc[key] ??= []).push(event);
    return acc;
  }, {});

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Operation Timeline"
        subtitle="Every event this console recorded, newest first. Nothing here is synthesised for presentation."
      />

      <div className="flex shrink-0 flex-wrap items-center gap-1.5 border-b border-line px-4 py-2.5">
        <button
          type="button"
          onClick={() => setChannels(new Set(TIMELINE_CHANNELS))}
          className={cx(
            'rounded-xs border px-2 py-1 text-[10.5px] font-medium transition-colors',
            channels.size === TIMELINE_CHANNELS.length
              ? 'border-line-2 bg-surface-3 text-text'
              : 'border-line text-text-3 hover:text-text-2',
          )}
        >
          All
        </button>
        {TIMELINE_CHANNELS.map((channel) => {
          const meta = TIMELINE_CHANNEL_META[channel];
          const on = channels.has(channel);
          const count = dataset.timeline.filter((e) => e.channel === channel).length;
          return (
            <button
              key={channel}
              type="button"
              onClick={() => toggle(channel)}
              aria-pressed={on}
              className={cx(
                'flex items-center gap-1.5 rounded-xs border px-2 py-1 text-[10.5px] font-medium transition-colors',
                on ? 'border-line-2 bg-surface-3' : 'border-line opacity-50 hover:opacity-80',
              )}
              style={{ color: on ? meta.text : 'var(--color-text-3)' }}
            >
              {meta.label}
              <span className="num opacity-65">{count}</span>
            </button>
          );
        })}
        <span className="num ml-auto text-[10.5px] text-text-3">{events.length} events shown</span>
      </div>

      <div className="scroll-y min-h-0 flex-1 px-4 py-3">
        {events.length === 0 ? (
          <EmptyState
            icon={Clock}
            title="No events on this channel"
            detail="Select more channels above, or wait for the next synchronisation."
          />
        ) : (
          Object.entries(groups).map(([day, dayEvents]) => (
            <section key={day} className="mb-5">
              <h2 className="eyebrow sticky top-0 z-10 -mx-4 mb-2 bg-void/95 px-4 py-1.5 backdrop-blur">
                {formatDateTime(dayEvents[0].at).split(' ').slice(0, 2).join(' ')} · {dayEvents.length} events
              </h2>

              <ol className="relative">
                {/* the spine */}
                <span className="absolute top-1 bottom-1 left-[52px] w-px bg-line" aria-hidden />

                {dayEvents.map((event) => {
                  const Icon = SEVERITY_ICON[event.severity];
                  const colour = SEVERITY_COLOUR[event.severity];
                  const channelMeta = TIMELINE_CHANNEL_META[event.channel];

                  return (
                    <li key={event.id} className="relative flex gap-3 py-1.5">
                      <time
                        className="num w-[44px] shrink-0 pt-0.5 text-right text-[11px] text-text-3"
                        dateTime={new Date(event.at).toISOString()}
                      >
                        {formatClockSeconds(event.at).slice(0, 5)}
                      </time>

                      <span
                        className="relative z-10 mt-0.5 grid size-4 shrink-0 place-items-center rounded-full border border-line bg-surface-2"
                        style={{ borderColor: colour }}
                      >
                        <Icon size={9} style={{ color: colour }} aria-hidden />
                      </span>

                      <div className="min-w-0 flex-1 pb-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className="text-[10px] font-semibold tracking-wider uppercase"
                            style={{ color: channelMeta.text }}
                          >
                            {channelMeta.label}
                          </span>
                          <SourceTag source={event.source} />
                          {event.emergencyId && (
                            <button
                              type="button"
                              onClick={() => {
                                selectEmergency(event.emergencyId!);
                                router.push('/emergencies');
                              }}
                              className="num text-[10.5px] text-rescuer-text hover:underline"
                            >
                              {event.emergencyId}
                            </button>
                          )}
                        </div>
                        <p className="text-[12.5px] leading-snug text-text">{event.title}</p>
                        {event.detail && (
                          <p className="mt-0.5 text-[11px] leading-snug text-text-3">
                            {event.detail}
                          </p>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ol>
            </section>
          ))
        )}
      </div>
    </div>
  );
}
