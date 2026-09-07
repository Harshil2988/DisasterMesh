'use client';

import { Ambulance, BedDouble, Hospital as HospitalIcon, TriangleAlert } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { Hospital } from '@/lib/types';
import { HOSPITAL_STATUS_META } from '@/lib/constants';
import { etaSeconds, formatCoords, formatDistance, formatEta, safeDistanceMeters } from '@/lib/geo';
import { formatAgo } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import {
  EmptyState,
  Field,
  Panel,
  StatusBadge,
  ValueOrUnavailable,
  cx,
} from '@/components/ui/primitives';

/** Capacity bar. Rendered only when both numbers were actually reported. */
function CapacityBar({
  free,
  total,
  label,
}: {
  free?: number;
  total?: number;
  label: string;
}): React.JSX.Element {
  if (free === undefined || total === undefined || total <= 0) {
    return (
      <div>
        <p className="eyebrow">{label}</p>
        <p className="text-[11px] text-text-3 italic">Not reported</p>
      </div>
    );
  }

  const ratio = Math.max(0, Math.min(1, free / total));
  const colour = ratio > 0.3 ? '#22c55e' : ratio > 0.12 ? '#f59e0b' : '#ef4444';

  return (
    <div>
      <div className="flex items-baseline justify-between">
        <p className="eyebrow">{label}</p>
        <p className="num text-[11px] text-text-2">
          {free} <span className="text-text-3">/ {total}</span>
        </p>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-4">
        <div
          className="h-full rounded-full transition-[width] duration-500"
          style={{ width: `${ratio * 100}%`, background: colour }}
        />
      </div>
    </div>
  );
}

export default function HospitalsPage(): React.JSX.Element {
  const { dataset, derived, now, selectedHospitalId, selectHospital } = useOps();

  if (!dataset) return <></>;

  const origin = derived.origin.point;

  const ranked = dataset.hospitals
    .map((hospital) => ({
      hospital,
      meters: safeDistanceMeters(origin, hospital.location),
    }))
    .sort((a, b) => (a.meters ?? Infinity) - (b.meters ?? Infinity));

  const selected: Hospital | null =
    dataset.hospitals.find((h) => h.id === selectedHospitalId) ?? null;

  const medicalLoad = derived.emergencies.filter(
    (e) => e.category === 'MEDICAL' && e.status !== 'RESOLVED' && e.status !== 'RESCUED',
  ).length;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Hospitals"
        subtitle="Receiving capacity across the operation. Figures are only as current as each facility's last update."
      />

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[1fr_minmax(300px,380px)]">
        <div className="scroll-y min-h-0 p-4">
          {dataset.hospitals.length === 0 ? (
            <EmptyState
              icon={HospitalIcon}
              title="No hospitals on record"
              detail="Hospital records are entered at the command centre. None have been added for this operation."
            />
          ) : (
            <>
              <div className="mb-3 flex items-center gap-2 rounded-sm border border-line bg-surface-2 px-3 py-2">
                <TriangleAlert size={13} className="shrink-0 text-text-3" aria-hidden />
                <p className="text-[11.5px] text-text-3">
                  <span className="num text-text-2">{medicalLoad}</span> open medical cases against{' '}
                  <span className="num text-text-2">
                    {dataset.hospitals.reduce((sum, h) => sum + (h.emergencyBeds ?? 0), 0)}
                  </span>{' '}
                  reported free emergency beds. Facilities that have not reported are excluded from
                  that total rather than assumed empty.
                </p>
              </div>

              <div className="overflow-hidden rounded-md border border-line">
                <table className="w-full border-collapse">
                  <caption className="sr-only">Hospitals, nearest first</caption>
                  <thead>
                    <tr className="border-b border-line bg-surface-1">
                      {['Hospital', 'Status', 'Distance', 'Emergency beds', 'ICU', 'Ambulances', 'Updated'].map(
                        (heading) => (
                          <th
                            key={heading}
                            scope="col"
                            className="px-3 py-2 text-left text-[10px] font-semibold tracking-wider text-text-3 uppercase"
                          >
                            {heading}
                          </th>
                        ),
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {ranked.map(({ hospital, meters }) => {
                      const meta = HOSPITAL_STATUS_META[hospital.status];
                      const isSelected = selectedHospitalId === hospital.id;
                      return (
                        <tr
                          key={hospital.id}
                          onClick={() => selectHospital(hospital.id)}
                          className={cx(
                            'cursor-pointer border-b border-line transition-colors last:border-b-0',
                            isSelected ? 'bg-surface-3' : 'bg-surface-2 hover:bg-surface-3',
                          )}
                        >
                          <th scope="row" className="px-3 py-2 text-left">
                            <span className="text-[12.5px] font-medium text-text">
                              {hospital.name}
                            </span>
                            {hospital.traumaCapable && (
                              <span className="ml-2 rounded-xs border border-line px-1 py-px text-[9px] tracking-wider text-text-3 uppercase">
                                trauma
                              </span>
                            )}
                          </th>
                          <td className="px-3 py-2">
                            <StatusBadge status={hospital.status} kind="hospital" />
                          </td>
                          <td className="num px-3 py-2 text-[12px] text-text-2">
                            {formatDistance(meters)}
                            <span className="ml-1.5 text-[10px] text-text-3">
                              {meters !== null ? formatEta(etaSeconds(meters)) : ''}
                            </span>
                          </td>
                          <td className="num px-3 py-2 text-[12px]" style={{ color: meta.text }}>
                            <ValueOrUnavailable
                              value={hospital.emergencyBeds}
                              reason="not reported"
                            />
                          </td>
                          <td className="num px-3 py-2 text-[12px] text-text-2">
                            <ValueOrUnavailable value={hospital.icuBeds} reason="not reported" />
                          </td>
                          <td className="num px-3 py-2 text-[12px] text-text-2">
                            <ValueOrUnavailable
                              value={hospital.ambulancesAvailable}
                              reason="not reported"
                            />
                          </td>
                          <td className="num px-3 py-2 text-[11px] text-text-3">
                            {hospital.lastUpdated ? formatAgo(hospital.lastUpdated, now) : 'never'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>

        <aside className="scroll-y min-h-0 border-l border-line bg-surface-1 p-4">
          {!selected ? (
            <EmptyState
              icon={HospitalIcon}
              title="No hospital selected"
              detail="Choose a facility to see its reported capacity in full."
            />
          ) : (
            <div className="space-y-3">
              <div>
                <h2 className="text-[16px] leading-tight font-semibold text-text">
                  {selected.name}
                </h2>
                <div className="mt-1 flex items-center gap-2">
                  <StatusBadge status={selected.status} kind="hospital" />
                  <span className="num text-[11px] text-text-3">
                    {formatDistance(safeDistanceMeters(origin, selected.location))} from{' '}
                    {derived.origin.label}
                  </span>
                </div>
              </div>

              {selected.status === 'UNKNOWN' && (
                <div className="flex items-start gap-2 rounded-sm border border-warning/30 bg-warning/8 px-2.5 py-2">
                  <TriangleAlert size={13} className="mt-px shrink-0 text-warning-text" aria-hidden />
                  <p className="text-[11.5px] text-warning-text">
                    No contact with this facility since the operation began. Capacity is unknown,
                    not zero — do not route casualties here on the assumption it is empty.
                  </p>
                </div>
              )}

              <Panel title="Capacity" icon={BedDouble} bodyClassName="space-y-3 p-3">
                <CapacityBar
                  free={selected.availableBeds}
                  total={selected.totalBeds}
                  label="Beds available"
                />
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Emergency beds" mono>
                    <ValueOrUnavailable value={selected.emergencyBeds} reason="Not reported" />
                  </Field>
                  <Field label="ICU beds" mono>
                    <ValueOrUnavailable value={selected.icuBeds} reason="Not reported" />
                  </Field>
                  <Field label="Trauma capable">
                    {selected.traumaCapable === undefined
                      ? 'Not reported'
                      : selected.traumaCapable
                        ? 'Yes'
                        : 'No'}
                  </Field>
                  <Field label="Ambulances" mono>
                    <ValueOrUnavailable
                      value={selected.ambulancesAvailable}
                      reason="Not reported"
                    />
                  </Field>
                </div>
              </Panel>

              <Panel title="Record" icon={Ambulance} bodyClassName="grid grid-cols-2 gap-3 p-3">
                <Field label="Coordinates" mono>
                  {formatCoords(selected.location)}
                </Field>
                <Field label="Last updated">
                  {selected.lastUpdated ? formatAgo(selected.lastUpdated, now) : 'Never'}
                </Field>
                <Field label="Source">{selected.source}</Field>
                <Field label="Identifier" mono>
                  {selected.id}
                </Field>
              </Panel>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
