'use client';

import { Siren } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, Panel } from '@/components/ui/primitives';
import { EmergencyCard } from '@/components/emergency/EmergencyCard';
import { FilterBar } from '@/components/emergency/FilterBar';
import { EmergencyDetailPanel } from '@/components/emergency/EmergencyDetailPanel';

export default function EmergenciesPage(): React.JSX.Element {
  const { dataset, derived, now, selected, selectEmergency, selectedEmergencyId, resetFilters } =
    useOps();

  if (!dataset) return <></>;

  const list = derived.filtered;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Emergencies"
        subtitle="Every report received, ranked by rescue priority score."
      />

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(300px,360px)_1fr]">
        {/* ---- queue ---- */}
        <div className="flex min-h-0 flex-col border-r border-line bg-surface-1">
          <FilterBar />

          <div className="flex shrink-0 items-baseline justify-between border-b border-line px-3 py-1.5">
            <span className="eyebrow">
              {list.length} of {derived.emergencies.length} reports
            </span>
            {list.length !== derived.emergencies.length && (
              <span className="text-[10px] text-warning-text">filtered</span>
            )}
          </div>

          <div className="scroll-y min-h-0 flex-1">
            {list.length === 0 ? (
              <EmptyState
                icon={Siren}
                title={
                  derived.emergencies.length === 0
                    ? 'No emergency reports available'
                    : 'No reports match these filters'
                }
                detail={
                  derived.emergencies.length === 0
                    ? 'Nothing has been synchronised from a rescuer gateway yet.'
                    : 'Widen the category, status or text filter to see more of the queue.'
                }
                action={
                  derived.emergencies.length > 0 ? (
                    <button
                      type="button"
                      onClick={resetFilters}
                      className="text-[11.5px] text-rescuer-text hover:underline"
                    >
                      Reset filters
                    </button>
                  ) : undefined
                }
              />
            ) : (
              list.map((emergency) => (
                <EmergencyCard
                  key={emergency.id}
                  emergency={emergency}
                  selected={selectedEmergencyId === emergency.id}
                  now={now}
                  teamName={dataset.teams.find((t) => t.id === emergency.assignedTeamId)?.name ?? null}
                  onSelect={() => selectEmergency(emergency.id)}
                />
              ))
            )}
          </div>
        </div>

        {/* ---- detail ---- */}
        <div className="min-h-0 bg-void">
          {selected ? (
            <EmergencyDetailPanel emergency={selected} />
          ) : (
            <div className="grid h-full place-items-center p-6">
              <Panel className="w-full max-w-md">
                <EmptyState
                  icon={Siren}
                  title="No incident selected"
                  detail="Choose a report from the queue to see its priority breakdown, distance, routing options and origin in the mesh."
                />
              </Panel>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
