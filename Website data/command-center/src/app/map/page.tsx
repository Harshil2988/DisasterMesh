'use client';

import { useState } from 'react';
import { Layers, Maximize2, PanelLeftClose, PanelLeftOpen, Siren, X } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { describeCluster } from '@/lib/clustering';
import { formatDistance } from '@/lib/geo';
import { CATEGORY_META } from '@/lib/constants';
import type { Category } from '@/lib/types';
import { PageHeader } from '@/components/ui/PageHeader';
import { Button, EmptyState, cx } from '@/components/ui/primitives';
import { MapView } from '@/components/map/MapView';
import { EmergencyCard } from '@/components/emergency/EmergencyCard';
import { FilterBar } from '@/components/emergency/FilterBar';
import { EmergencyDetailPanel } from '@/components/emergency/EmergencyDetailPanel';

export default function MapPage(): React.JSX.Element {
  const {
    dataset, derived, now, selected, selectEmergency, selectedEmergencyId,
    selectedClusterId, selectCluster, fit,
  } = useOps();
  const [railOpen, setRailOpen] = useState(true);

  if (!dataset) return <></>;

  const cluster = derived.clusters.find((c) => c.id === selectedClusterId) ?? null;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Live Disaster Map"
        subtitle="Every report with a position, plus teams, hospitals and hazard zones."
        actions={
          <>
            <Button
              size="sm"
              icon={railOpen ? PanelLeftClose : PanelLeftOpen}
              onClick={() => setRailOpen((o) => !o)}
            >
              {railOpen ? 'Hide queue' : 'Show queue'}
            </Button>
            <Button size="sm" icon={Maximize2} onClick={() => fit('ALL')}>
              Fit all
            </Button>
          </>
        }
      />

      <div className="flex min-h-0 flex-1">
        {/* ---- queue rail ---- */}
        {railOpen && (
          <aside className="flex w-[288px] shrink-0 flex-col border-r border-line bg-surface-1">
            <FilterBar />
            <div className="shrink-0 border-b border-line px-3 py-1.5">
              <span className="eyebrow">
                {derived.filtered.length} shown ·{' '}
                {derived.filtered.filter((e) => !e.location).length} without a position
              </span>
            </div>
            <div className="scroll-y min-h-0 flex-1">
              {derived.filtered.length === 0 ? (
                <EmptyState icon={Siren} title="Nothing matches these filters" />
              ) : (
                derived.filtered.map((emergency) => (
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
                      fit('SELECTED');
                    }}
                  />
                ))
              )}
            </div>
          </aside>
        )}

        {/* ---- map ---- */}
        <div className="relative min-w-0 flex-1">
          <MapView />

          {/* cluster callout */}
          {cluster && (
            <div className="anim-in absolute top-3 left-3 z-[600] w-[268px] rounded-md border border-warning/40 bg-surface-2/96 p-3 backdrop-blur">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-1.5">
                  <Layers size={12} className="text-warning-text" aria-hidden />
                  <span className="text-[11px] font-bold tracking-wider text-warning-text uppercase">
                    High-impact area
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => selectCluster(null)}
                  aria-label="Dismiss cluster summary"
                  className="text-text-3 hover:text-text"
                >
                  <X size={13} />
                </button>
              </div>

              <p className="mt-1.5 text-[13px] leading-snug font-medium text-text">
                {describeCluster(cluster)}
              </p>
              <p className="mt-0.5 text-[11px] text-text-3">
                {cluster.sector ?? 'Unnamed sector'} · {cluster.emergencyIds.length} reports ·
                radius {formatDistance(cluster.radiusMeters)}
              </p>

              <dl className="mt-2 grid grid-cols-5 gap-1">
                {(Object.keys(cluster.countsByCategory) as Category[]).map((category) => (
                  <div
                    key={category}
                    className="rounded-xs border border-line bg-surface-3 px-1 py-1 text-center"
                  >
                    <dt
                      className="text-[9px] font-semibold tracking-wide uppercase"
                      style={{ color: CATEGORY_META[category].text }}
                    >
                      {CATEGORY_META[category].short}
                    </dt>
                    <dd className="num text-[13px] leading-none font-semibold text-text">
                      {cluster.countsByCategory[category]}
                    </dd>
                  </div>
                ))}
              </dl>

              <p className="mt-2 rounded-sm border-l-2 border-warning py-1 pl-2 text-[11px] text-text-2">
                Deploy one team to the area rather than to a single incident — one approach covers
                all {cluster.emergencyIds.length} reports.
              </p>

              <div className="mt-2 flex gap-1.5">
                <Button
                  size="sm"
                  variant="primary"
                  onClick={() => {
                    const top = cluster.emergencyIds
                      .map((id) => derived.byId.get(id))
                      .filter(Boolean)
                      .sort((a, b) => b!.breakdown.score - a!.breakdown.score)[0];
                    if (top) selectEmergency(top.id);
                  }}
                >
                  Open top incident
                </Button>
                <Button size="sm" onClick={() => fit('CLUSTER')}>
                  Zoom to area
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* ---- detail ---- */}
        <aside
          className={cx(
            'min-h-0 shrink-0 border-l border-line bg-void transition-[width] duration-200',
            selected ? 'w-[392px]' : 'w-0 overflow-hidden',
          )}
          aria-label="Selected incident"
        >
          {selected && <EmergencyDetailPanel emergency={selected} />}
        </aside>
      </div>
    </div>
  );
}
