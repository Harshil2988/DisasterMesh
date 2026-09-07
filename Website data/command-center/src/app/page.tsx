'use client';

import { useRouter } from 'next/navigation';
import {
  Activity,
  ArrowRight,
  HeartPulse,
  Layers,
  Lightbulb,
  Map as MapIcon,
  Package,
  RefreshCw,
  ShieldCheck,
  Siren,
  TriangleAlert,
  Users,
} from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { CATEGORY_META } from '@/lib/constants';
import { describeCluster } from '@/lib/clustering';
import { formatAgo } from '@/lib/format';
import { formatDistance } from '@/lib/geo';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatCard } from '@/components/ui/StatCard';
import { Button, EmptyState, Panel } from '@/components/ui/primitives';
import { EmergencyCard } from '@/components/emergency/EmergencyCard';
import { DecisionSupportNote, RecommendationCard } from '@/components/ops/RecommendationCard';
import { PipelineDiagram } from '@/components/ops/PipelineDiagram';
import type { Category, EmergencyStatus } from '@/lib/types';

export default function OverviewPage(): React.JSX.Element {
  const router = useRouter();
  const {
    dataset, derived, now, selectEmergency, selectCluster, fit,
    setFilters, resetFilters, selectedEmergencyId,
  } = useOps();

  if (!dataset) return <></>;

  const { stats, recommendations, clusters } = derived;

  /** Sends the operator to the queue with exactly the filter they clicked. */
  const openQueue = (categories?: Category[], statuses?: EmergencyStatus[]): void => {
    resetFilters();
    if (categories) setFilters({ categories: new Set(categories) });
    if (statuses) setFilters({ statuses: new Set(statuses) });
    router.push('/emergencies');
  };

  const queue = derived.emergencies
    .filter((e) => e.status !== 'RESOLVED' && e.status !== 'RESCUED' && e.status !== 'INVALID')
    .sort((a, b) => b.breakdown.score - a.breakdown.score);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Disaster Overview"
        subtitle="Real-time situational awareness from field-collected data."
        actions={
          <>
            <Button icon={MapIcon} onClick={() => router.push('/map')}>
              Open live map
            </Button>
            <Button variant="primary" icon={ArrowRight} onClick={() => openQueue()}>
              Work the queue
            </Button>
          </>
        }
      />

      <div className="scroll-y min-h-0 flex-1 space-y-3 p-4">
        {/* ---- headline figures ---- */}
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard
            label="Critical / SOS"
            value={stats.critical}
            detail="open, life-threatening"
            icon={Siren}
            tone={CATEGORY_META.CRITICAL.text}
            emphasis
            onClick={() => openQueue(['CRITICAL'])}
          />
          <StatCard
            label="Medical"
            value={stats.medical}
            detail="open cases"
            icon={HeartPulse}
            tone={CATEGORY_META.MEDICAL.text}
            onClick={() => openQueue(['MEDICAL'])}
          />
          <StatCard
            label="Assistance"
            value={stats.warning + stats.supply}
            detail="warnings and supply"
            icon={Package}
            tone={CATEGORY_META.SUPPLY.text}
            onClick={() => openQueue(['WARNING', 'SUPPLY'])}
          />
          <StatCard
            label="Safe reports"
            value={stats.safe}
            detail="people accounted for"
            icon={ShieldCheck}
            tone={CATEGORY_META.SAFE.text}
            onClick={() => openQueue(['SAFE'])}
          />
          <StatCard
            label="People tracked"
            value={stats.peopleTracked}
            detail="summed across all reports"
            icon={Users}
            computed
          />
          <StatCard
            label="Teams available"
            value={`${stats.availableTeams}/${dataset.teams.length}`}
            detail={`${stats.activeTeams} on task`}
            icon={Activity}
            tone={stats.availableTeams === 0 ? 'var(--color-warning-text)' : undefined}
            onClick={() => router.push('/teams')}
          />
        </div>

        {/* ---- decision support + priority queue ---- */}
        <div className="grid gap-3 xl:grid-cols-[1.35fr_1fr]">
          <Panel
            title="Recommended actions"
            subtitle="Operational decision support"
            icon={Lightbulb}
            bodyClassName="p-3 space-y-2.5"
          >
            {recommendations.length === 0 ? (
              <EmptyState
                icon={ShieldCheck}
                title="Nothing needs escalating"
                detail="No rule currently matches: no critical case is overdue, no cluster is unattended, and no facility is under pressure."
              />
            ) : (
              recommendations
                .slice(0, 5)
                .map((recommendation) => (
                  <RecommendationCard key={recommendation.id} recommendation={recommendation} />
                ))
            )}
            <DecisionSupportNote recommendations={recommendations} />
          </Panel>

          <div className="flex min-h-0 flex-col gap-3">
            <Panel
              title="Priority queue"
              subtitle={`${queue.length} open · ranked by rescue priority score`}
              icon={Siren}
              flush
              bodyClassName="scroll-y max-h-[620px]"
              actions={
                <Button size="sm" variant="ghost" onClick={() => openQueue()}>
                  View all
                </Button>
              }
            >
              {queue.length === 0 ? (
                <EmptyState
                  icon={ShieldCheck}
                  title="No open incidents"
                  detail="Every report received has been resolved, rescued or closed."
                />
              ) : (
                queue.slice(0, 10).map((emergency) => (
                  <EmergencyCard
                    key={emergency.id}
                    emergency={emergency}
                    selected={selectedEmergencyId === emergency.id}
                    now={now}
                    teamName={
                      dataset.teams.find((t) => t.id === emergency.assignedTeamId)?.name ?? null
                    }
                    onSelect={() => {
                      selectEmergency(emergency.id);
                      router.push('/emergencies');
                    }}
                  />
                ))
              )}
            </Panel>
          </div>
        </div>

        {/* ---- clusters, pipeline, synchronisation ---- */}
        <div className="grid gap-3 lg:grid-cols-3">
          <Panel title="High-impact areas" subtitle="Geographic clustering" icon={Layers} bodyClassName="p-3 space-y-2">
            {clusters.length === 0 ? (
              <EmptyState
                icon={Layers}
                title="No clusters detected"
                detail="No three reports currently sit within 400 m of one another."
              />
            ) : (
              clusters.slice(0, 3).map((cluster) => (
                <button
                  key={cluster.id}
                  type="button"
                  onClick={() => {
                    selectCluster(cluster.id);
                    fit('CLUSTER');
                    router.push('/map');
                  }}
                  className="w-full rounded-sm border border-line bg-surface-3 p-2.5 text-left transition-colors hover:border-line-2 hover:bg-surface-4"
                >
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="truncate text-[12px] font-medium text-text">
                      {cluster.sector ?? 'Unnamed sector'}
                    </span>
                    <span className="num shrink-0 text-[10.5px] text-text-3">
                      r {formatDistance(cluster.radiusMeters)}
                    </span>
                  </div>
                  <p className="mt-0.5 text-[11px] text-text-2">{describeCluster(cluster)}</p>
                  <div className="mt-1.5 flex flex-wrap gap-x-2.5 gap-y-0.5">
                    {(Object.keys(cluster.countsByCategory) as Category[])
                      .filter((c) => cluster.countsByCategory[c] > 0)
                      .map((c) => (
                        <span key={c} className="flex items-center gap-1 text-[10px]">
                          <span
                            className="size-1.5 rounded-full"
                            style={{ background: CATEGORY_META[c].mark }}
                            aria-hidden
                          />
                          <span style={{ color: CATEGORY_META[c].text }}>{CATEGORY_META[c].short}</span>
                          <span className="num text-text-3">{cluster.countsByCategory[c]}</span>
                        </span>
                      ))}
                  </div>
                </button>
              ))
            )}
          </Panel>

          <Panel
            title="Data pipeline"
            subtitle="Where this information came from"
            icon={Activity}
            bodyClassName="p-3"
          >
            <PipelineDiagram />
          </Panel>

          <Panel
            title="Synchronisation"
            subtitle="Rescuer gateways"
            icon={RefreshCw}
            bodyClassName="p-3 space-y-2"
            actions={
              <Button size="sm" variant="ghost" onClick={() => router.push('/sync')}>
                Details
              </Button>
            }
          >
            <dl className="grid grid-cols-2 gap-2">
              {[
                {
                  label: 'Last synchronisation',
                  value: dataset.connection.lastSyncedAt
                    ? formatAgo(dataset.connection.lastSyncedAt, now)
                    : 'Waiting for gateway',
                },
                { label: 'Records synchronised', value: String(stats.reportsSynchronised) },
                { label: 'Gateways online', value: String(dataset.connection.gatewaysOnline) },
                { label: 'Pending uploads', value: String(dataset.connection.pendingUploads) },
              ].map((row) => (
                <div key={row.label} className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
                  <dt className="eyebrow">{row.label}</dt>
                  <dd className="num mt-0.5 text-[14px] leading-none font-semibold text-text">
                    {row.value}
                  </dd>
                </div>
              ))}
            </dl>

            <ul className="space-y-1">
              {dataset.syncEvents.slice(0, 4).map((event) => (
                <li key={event.id} className="flex gap-2 text-[10.5px]">
                  <span className="num shrink-0 text-text-3">{formatAgo(event.at, now)}</span>
                  <span className="truncate text-text-2">{event.text}</span>
                </li>
              ))}
            </ul>

            {stats.meshNodes > 0 && (
              <p className="border-t border-line pt-2 text-[10.5px] text-text-3">
                <span className="num text-text-2">{stats.meshNodes}</span> mesh nodes have
                contributed to this operating picture.
              </p>
            )}
          </Panel>
        </div>

        {/* ---- lower-priority totals ---- */}
        <Panel title="Operation totals" subtitle="Cumulative, all statuses" icon={TriangleAlert} bodyClassName="p-3">
          <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
            {[
              { label: 'Reports received', value: derived.emergencies.length },
              { label: 'Open', value: stats.openIncidents },
              { label: 'Unassigned', value: stats.unassigned },
              { label: 'Rescued', value: stats.rescued },
              { label: 'Resolved', value: stats.resolvedIncidents },
              { label: 'Mesh nodes', value: stats.meshNodes },
              { label: 'Field reports', value: dataset.fieldReports.length },
            ].map((row) => (
              <div key={row.label} className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
                <dt className="eyebrow leading-tight">{row.label}</dt>
                <dd className="num mt-0.5 text-[18px] leading-none font-semibold text-text">
                  {row.value}
                </dd>
              </div>
            ))}
          </dl>
        </Panel>
      </div>
    </div>
  );
}
