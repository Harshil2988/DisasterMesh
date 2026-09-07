'use client';

import { useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useOps } from '@/state/ops-store';
import type { Category } from '@/lib/types';
import { CATEGORY_META, CATEGORY_ORDER, PRIORITY_META, PRIORITY_ORDER } from '@/lib/constants';
import { formatDuration } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, Panel, cx } from '@/components/ui/primitives';
import { AXIS, ChartFrame, ChartTooltip, GRID_STROKE } from '@/components/charts/chart-parts';
import { Activity } from 'lucide-react';

const WINDOWS = [
  { id: '1h', label: 'Last hour', ms: 60 * 60_000 },
  { id: '3h', label: 'Last 3 hours', ms: 3 * 60 * 60_000 },
  { id: '12h', label: 'Last 12 hours', ms: 12 * 60 * 60_000 },
  { id: 'all', label: 'All time', ms: null },
] as const;

export default function ReportsPage(): React.JSX.Element {
  const { dataset, derived, now } = useOps();
  const [windowId, setWindowId] = useState<(typeof WINDOWS)[number]['id']>('all');

  const activeWindow = WINDOWS.find((w) => w.id === windowId) ?? WINDOWS[3];

  const scoped = useMemo(
    () =>
      derived.emergencies.filter((e) =>
        activeWindow.ms === null ? true : now - e.reportedAt <= activeWindow.ms,
      ),
    [derived.emergencies, activeWindow, now],
  );

  const byCategory = useMemo(
    () =>
      CATEGORY_ORDER.map((category) => ({
        category,
        label: CATEGORY_META[category].label,
        short: CATEGORY_META[category].short,
        count: scoped.filter((e) => e.category === category).length,
        people: scoped
          .filter((e) => e.category === category)
          .reduce((sum, e) => sum + (e.peopleAffected ?? 0), 0),
      })),
    [scoped],
  );

  const byPriority = useMemo(
    () =>
      PRIORITY_ORDER.map((priority) => ({
        priority,
        label: `${priority} — ${PRIORITY_META[priority].label}`,
        count: scoped.filter((e) => e.priority === priority).length,
      })),
    [scoped],
  );

  /** Reports over time, in 15-minute buckets. */
  const overTime = useMemo(() => {
    if (scoped.length === 0) return [];
    const bucketMs = 15 * 60_000;
    const oldest = Math.min(...scoped.map((e) => e.reportedAt));
    const start = Math.floor(oldest / bucketMs) * bucketMs;
    const buckets = Math.min(48, Math.ceil((now - start) / bucketMs) + 1);

    return Array.from({ length: buckets }, (_, index) => {
      const from = start + index * bucketMs;
      const inBucket = scoped.filter((e) => e.reportedAt >= from && e.reportedAt < from + bucketMs);
      return {
        time: new Date(from).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }),
        critical: inBucket.filter((e) => e.category === 'CRITICAL').length,
        medical: inBucket.filter((e) => e.category === 'MEDICAL').length,
        other: inBucket.filter((e) => !['CRITICAL', 'MEDICAL'].includes(e.category)).length,
      };
    });
  }, [scoped, now]);

  const bySector = useMemo(() => {
    const map = new Map<string, { count: number; people: number; critical: number }>();
    for (const e of scoped) {
      const key = e.sector ?? 'No sector resolved';
      const entry = map.get(key) ?? { count: 0, people: 0, critical: 0 };
      entry.count += 1;
      entry.people += e.peopleAffected ?? 0;
      if (e.category === 'CRITICAL') entry.critical += 1;
      map.set(key, entry);
    }
    return Array.from(map.entries())
      .map(([sector, value]) => ({ sector: sector.replace(' — ', ' · '), ...value }))
      .sort((a, b) => b.count - a.count);
  }, [scoped]);

  const teamWorkload = useMemo(() => {
    if (!dataset) return [];
    return dataset.teams
      .map((team) => ({
        team: team.callsign,
        active: dataset.missions.filter(
          (m) => m.teamId === team.id && m.status !== 'COMPLETED' && m.status !== 'ABORTED',
        ).length,
        completed: team.completedMissions,
      }))
      .sort((a, b) => b.active + b.completed - (a.active + a.completed));
  }, [dataset]);

  /** Response metrics. Only computable where the underlying times exist. */
  const metrics = useMemo(() => {
    const acknowledged = scoped.filter((e) => e.acknowledgedAt);
    const resolved = scoped.filter((e) => e.resolvedAt);
    const criticalResolved = resolved.filter((e) => e.category === 'CRITICAL');

    const mean = (values: number[]): number | null =>
      values.length === 0 ? null : values.reduce((a, b) => a + b, 0) / values.length;

    const open = scoped.filter(
      (e) => !['RESOLVED', 'RESCUED', 'INVALID'].includes(e.status),
    );

    return [
      {
        label: 'Avg. acknowledgement',
        value: mean(acknowledged.map((e) => (e.acknowledgedAt ?? 0) - e.reportedAt)),
        empty: 'No report has been acknowledged yet',
      },
      {
        label: 'Avg. time to resolve',
        value: mean(resolved.map((e) => (e.resolvedAt ?? 0) - e.reportedAt)),
        empty: 'Nothing resolved in this window',
      },
      {
        label: 'Critical response time',
        value: mean(criticalResolved.map((e) => (e.resolvedAt ?? 0) - e.reportedAt)),
        empty: 'No critical case resolved yet',
      },
      {
        label: 'Avg. current wait',
        value: mean(open.map((e) => now - e.reportedAt)),
        empty: 'No open incidents',
      },
    ];
  }, [scoped, now]);

  if (!dataset) return <></>;

  const completionRate =
    scoped.length === 0
      ? 0
      : Math.round(
          (scoped.filter((e) => e.status === 'RESCUED' || e.status === 'RESOLVED').length /
            scoped.length) *
            100,
        );

  const teamUtilisation =
    dataset.teams.length === 0
      ? 0
      : Math.round(
          (dataset.teams.filter((t) => t.status !== 'AVAILABLE' && t.status !== 'OFFLINE').length /
            dataset.teams.length) *
            100,
        );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Reports"
        subtitle="Analysis of everything received during this operation."
        actions={
          <div className="flex items-center gap-1" role="group" aria-label="Time window">
            {WINDOWS.map((w) => (
              <button
                key={w.id}
                type="button"
                onClick={() => setWindowId(w.id)}
                aria-pressed={windowId === w.id}
                className={cx(
                  'rounded-xs border px-2 py-1 text-[10.5px] font-medium transition-colors',
                  windowId === w.id
                    ? 'border-line-2 bg-surface-3 text-text'
                    : 'border-line text-text-3 hover:text-text-2',
                )}
              >
                {w.label}
              </button>
            ))}
          </div>
        }
      />

      <div className="scroll-y min-h-0 flex-1 space-y-3 p-4">
        {scoped.length === 0 ? (
          <Panel>
            <EmptyState
              icon={Activity}
              title="No reports in this window"
              detail="Widen the time window, or wait for the next synchronisation."
            />
          </Panel>
        ) : (
          <>
            {/* ---- response metrics ---- */}
            <div className="grid grid-cols-2 gap-2 md:grid-cols-3 xl:grid-cols-6">
              {metrics.map((metric) => (
                <div key={metric.label} className="rounded-md border border-line bg-surface-2 p-3">
                  {/* Labels wrap rather than truncate: "Avg. acknowledgement"
                      and "Avg. current wait" are not interchangeable, and an
                      ellipsis makes them look it. */}
                  <p className="eyebrow leading-tight">{metric.label}</p>
                  {metric.value === null ? (
                    <p className="mt-1 text-[11px] leading-tight text-text-3 italic">{metric.empty}</p>
                  ) : (
                    <p className="num mt-1 text-[19px] leading-none font-semibold text-text">
                      {formatDuration(metric.value)}
                    </p>
                  )}
                </div>
              ))}
              <div className="rounded-md border border-line bg-surface-2 p-3">
                <p className="eyebrow leading-tight">Completion rate</p>
                <p className="num mt-1 text-[19px] leading-none font-semibold text-safe-text">
                  {completionRate}%
                </p>
              </div>
              <div className="rounded-md border border-line bg-surface-2 p-3">
                <p className="eyebrow leading-tight">Team utilisation</p>
                <p className="num mt-1 text-[19px] leading-none font-semibold text-rescuer-text">
                  {teamUtilisation}%
                </p>
              </div>
            </div>

            {/* ---- charts ---- */}
            <div className="grid gap-3 xl:grid-cols-2">
              <ChartFrame
                title="Reports over time"
                subtitle="15-minute buckets. Critical and medical shown separately."
                height={210}
                table={{
                  headers: ['Time', 'Critical', 'Medical', 'Other'],
                  rows: overTime.map((b) => [b.time, b.critical, b.medical, b.other]),
                }}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={overTime} margin={{ top: 4, right: 6, left: -22, bottom: 0 }}>
                    <defs>
                      {[
                        ['gCrit', CATEGORY_META.CRITICAL.mark],
                        ['gMed', CATEGORY_META.MEDICAL.mark],
                        ['gOther', '#64748b'],
                      ].map(([id, colour]) => (
                        <linearGradient key={id} id={id} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor={colour} stopOpacity={0.35} />
                          <stop offset="100%" stopColor={colour} stopOpacity={0.02} />
                        </linearGradient>
                      ))}
                    </defs>
                    <CartesianGrid stroke={GRID_STROKE} vertical={false} />
                    <XAxis dataKey="time" {...AXIS} interval="preserveStartEnd" minTickGap={28} />
                    <YAxis {...AXIS} allowDecimals={false} width={38} />
                    <Tooltip content={<ChartTooltip />} cursor={{ stroke: '#2b3650' }} />
                    {/* Distinct dash patterns as well as hue: series must not be
                        separable by colour alone. */}
                    <Area
                      type="monotone" dataKey="other" name="Other" stackId="1"
                      stroke="#64748b" strokeDasharray="2 3" fill="url(#gOther)" strokeWidth={1.4} isAnimationActive={false}
                    />
                    <Area
                      type="monotone" dataKey="medical" name="Medical" stackId="1"
                      stroke={CATEGORY_META.MEDICAL.mark} strokeDasharray="6 3" fill="url(#gMed)" strokeWidth={1.6} isAnimationActive={false}
                    />
                    <Area
                      type="monotone" dataKey="critical" name="Critical" stackId="1"
                      stroke={CATEGORY_META.CRITICAL.mark} fill="url(#gCrit)" strokeWidth={2} isAnimationActive={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
                <div className="mt-1.5 flex flex-wrap gap-3">
                  {[
                    { label: 'Critical', colour: CATEGORY_META.CRITICAL.mark, dash: 'solid' },
                    { label: 'Medical', colour: CATEGORY_META.MEDICAL.mark, dash: 'dashed' },
                    { label: 'Other', colour: '#64748b', dash: 'dotted' },
                  ].map((entry) => (
                    <span key={entry.label} className="flex items-center gap-1.5 text-[10.5px] text-text-3">
                      <span
                        className="w-4 border-t-2"
                        style={{ borderColor: entry.colour, borderStyle: entry.dash }}
                        aria-hidden
                      />
                      {entry.label}
                    </span>
                  ))}
                </div>
              </ChartFrame>

              <ChartFrame
                title="Reports by category"
                subtitle="Count of reports, and people affected."
                height={210}
                table={{
                  headers: ['Category', 'Reports', 'People'],
                  rows: byCategory.map((c) => [c.label, c.count, c.people]),
                }}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={byCategory} margin={{ top: 4, right: 6, left: -22, bottom: 0 }}>
                    <CartesianGrid stroke={GRID_STROKE} vertical={false} />
                    <XAxis dataKey="short" {...AXIS} />
                    <YAxis {...AXIS} allowDecimals={false} width={38} />
                    <Tooltip content={<ChartTooltip />} cursor={{ fill: '#ffffff08' }} />
                    <Bar dataKey="count" name="Reports" radius={[2, 2, 0, 0]} maxBarSize={44} isAnimationActive={false}>
                      {byCategory.map((entry) => (
                        <Cell key={entry.category} fill={CATEGORY_META[entry.category as Category].mark} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </ChartFrame>

              <ChartFrame
                title="Reports by area"
                subtitle="Sectors ranked by report count."
                height={210}
                table={{
                  headers: ['Sector', 'Reports', 'People', 'Critical'],
                  rows: bySector.map((s) => [s.sector, s.count, s.people, s.critical]),
                }}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={bySector}
                    layout="vertical"
                    margin={{ top: 4, right: 10, left: 6, bottom: 0 }}
                  >
                    <CartesianGrid stroke={GRID_STROKE} horizontal={false} />
                    <XAxis type="number" {...AXIS} allowDecimals={false} />
                    <YAxis
                      type="category"
                      dataKey="sector"
                      {...AXIS}
                      width={112}
                      tick={{ fill: '#94a3b8', fontSize: 9.5 }}
                    />
                    <Tooltip content={<ChartTooltip />} cursor={{ fill: '#ffffff08' }} />
                    <Bar dataKey="count" name="Reports" fill="#3b82f6" radius={[0, 2, 2, 0]} maxBarSize={16} isAnimationActive={false} />
                    <Bar dataKey="critical" name="Critical" fill="#ef4444" radius={[0, 2, 2, 0]} maxBarSize={16} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </ChartFrame>

              <ChartFrame
                title="Team workload"
                subtitle="Active and completed missions per team."
                height={210}
                table={{
                  headers: ['Team', 'Active', 'Completed'],
                  rows: teamWorkload.map((t) => [t.team, t.active, t.completed]),
                }}
              >
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={teamWorkload} margin={{ top: 4, right: 6, left: -22, bottom: 0 }}>
                    <CartesianGrid stroke={GRID_STROKE} vertical={false} />
                    <XAxis dataKey="team" {...AXIS} />
                    <YAxis {...AXIS} allowDecimals={false} width={38} />
                    <Tooltip content={<ChartTooltip />} cursor={{ fill: '#ffffff08' }} />
                    <Bar dataKey="active" name="Active" fill="#06b6d4" radius={[2, 2, 0, 0]} maxBarSize={22} isAnimationActive={false} />
                    <Bar dataKey="completed" name="Completed" fill="#334155" radius={[2, 2, 0, 0]} maxBarSize={22} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
                <div className="mt-1.5 flex gap-3">
                  {[
                    { label: 'Active missions', colour: '#06b6d4' },
                    { label: 'Completed', colour: '#334155' },
                  ].map((entry) => (
                    <span key={entry.label} className="flex items-center gap-1.5 text-[10.5px] text-text-3">
                      <span className="size-2 rounded-xs" style={{ background: entry.colour }} aria-hidden />
                      {entry.label}
                    </span>
                  ))}
                </div>
              </ChartFrame>
            </div>

            {/* ---- priority distribution ---- */}
            <Panel title="Cases by priority" subtitle="Current banding from the rescue priority score" bodyClassName="p-3">
              <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
                {byPriority.map((band) => {
                  const meta = PRIORITY_META[band.priority];
                  const share = scoped.length === 0 ? 0 : (band.count / scoped.length) * 100;
                  return (
                    <div key={band.priority} className="rounded-sm border border-line bg-surface-3 p-2.5">
                      <div className="flex items-baseline justify-between">
                        <span className="text-[11.5px] font-bold" style={{ color: meta.text }}>
                          {band.priority}
                        </span>
                        <span className="num text-[17px] leading-none font-semibold text-text">
                          {band.count}
                        </span>
                      </div>
                      <p className="mt-0.5 text-[10.5px] text-text-3">{meta.label}</p>
                      <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-surface-4">
                        <div
                          className="h-full rounded-full"
                          style={{ width: `${share}%`, background: meta.mark }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </Panel>
          </>
        )}
      </div>
    </div>
  );
}
