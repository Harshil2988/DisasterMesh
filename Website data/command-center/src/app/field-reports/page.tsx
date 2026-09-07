'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { FileText, Headphones, Image as ImageIcon, StickyNote, Video } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { FieldReport, FieldReportKind } from '@/lib/types';
import { formatCoords, formatDistance, safeDistanceMeters } from '@/lib/geo';
import { formatAgo, formatClock } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, Field, Panel, SourceTag, cx } from '@/components/ui/primitives';
import { AudioPlayer } from '@/components/emergency/MediaPanel';

const KIND_META: Record<FieldReportKind, { label: string; icon: typeof FileText; colour: string }> = {
  TEXT: { label: 'Text', icon: FileText, colour: 'var(--color-text-3)' },
  PHOTO: { label: 'Photo', icon: ImageIcon, colour: 'var(--color-supply-text)' },
  AUDIO: { label: 'Audio', icon: Headphones, colour: 'var(--color-system-text)' },
  VIDEO: { label: 'Video', icon: Video, colour: 'var(--color-medical-text)' },
  NOTE: { label: 'Note', icon: StickyNote, colour: 'var(--color-warning-text)' },
};

const KINDS = Object.keys(KIND_META) as FieldReportKind[];

export default function FieldReportsPage(): React.JSX.Element {
  const router = useRouter();
  const { dataset, derived, now, selectEmergency } = useOps();
  const [kinds, setKinds] = useState<Set<FieldReportKind>>(new Set(KINDS));
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (!dataset) return <></>;

  const reports = dataset.fieldReports
    .filter((r) => kinds.has(r.kind))
    .sort((a, b) => b.capturedAt - a.capturedAt);

  const selected: FieldReport | null =
    dataset.fieldReports.find((r) => r.id === selectedId) ?? reports[0] ?? null;

  const toggle = (kind: FieldReportKind): void =>
    setKinds((current) => {
      const next = new Set(current);
      if (next.has(kind)) next.delete(kind);
      else next.add(kind);
      return next;
    });

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Field Reports"
        subtitle="Evidence captured in the disaster zone and carried out by rescuer devices."
      />

      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[1fr_minmax(320px,420px)]">
        <div className="flex min-h-0 flex-col">
          <div className="flex shrink-0 flex-wrap gap-1.5 border-b border-line px-4 py-2.5">
            {KINDS.map((kind) => {
              const meta = KIND_META[kind];
              const Icon = meta.icon;
              const on = kinds.has(kind);
              const count = dataset.fieldReports.filter((r) => r.kind === kind).length;
              return (
                <button
                  key={kind}
                  type="button"
                  onClick={() => toggle(kind)}
                  aria-pressed={on}
                  className={cx(
                    'flex items-center gap-1.5 rounded-xs border px-2 py-1 text-[10.5px] transition-colors',
                    on ? 'border-line-2 bg-surface-3 text-text-2' : 'border-line text-text-3 opacity-55',
                  )}
                >
                  <Icon size={11} style={{ color: on ? meta.colour : undefined }} aria-hidden />
                  {meta.label}
                  <span className="num opacity-65">{count}</span>
                </button>
              );
            })}
          </div>

          <div className="scroll-y min-h-0 flex-1 p-4">
            {reports.length === 0 ? (
              <EmptyState
                icon={FileText}
                title="No field reports"
                detail="Nothing has been synchronised from a rescuer device for these types."
              />
            ) : (
              <div className="grid gap-2 md:grid-cols-2 2xl:grid-cols-3">
                {reports.map((report) => {
                  const meta = KIND_META[report.kind];
                  const Icon = meta.icon;
                  const isSelected = selected?.id === report.id;

                  return (
                    <button
                      key={report.id}
                      type="button"
                      onClick={() => setSelectedId(report.id)}
                      className={cx(
                        'flex flex-col gap-2 rounded-md border p-2.5 text-left transition-colors',
                        isSelected
                          ? 'border-rescuer/50 bg-rescuer/8'
                          : 'border-line bg-surface-2 hover:border-line-2 hover:bg-surface-3',
                      )}
                    >
                      {/* preview plate */}
                      <div className="relative flex aspect-16/9 items-center justify-center overflow-hidden rounded-sm border border-line bg-surface-3">
                        <Icon size={20} style={{ color: meta.colour }} aria-hidden />
                        <span className="absolute top-1.5 left-1.5 rounded-xs border border-line-2 bg-surface-2/90 px-1 py-px text-[9px] font-semibold tracking-wider uppercase" style={{ color: meta.colour }}>
                          {meta.label}
                        </span>
                        {report.durationSeconds !== undefined && (
                          <span className="num absolute right-1.5 bottom-1.5 rounded-xs bg-void/80 px-1 py-px text-[9.5px] text-text-2">
                            {Math.floor(report.durationSeconds / 60)}:
                            {String(report.durationSeconds % 60).padStart(2, '0')}
                          </span>
                        )}
                        {!report.mediaAvailable && (
                          <span className="absolute bottom-1.5 left-1.5 text-[9px] text-text-3">
                            media not bundled
                          </span>
                        )}
                      </div>

                      <div className="min-w-0">
                        <p className="line-clamp-1 text-[12px] font-medium text-text">
                          {report.title}
                        </p>
                        <p className="mt-0.5 line-clamp-2 text-[11px] leading-snug text-text-3">
                          {report.body}
                        </p>
                        <p className="num mt-1 text-[10px] text-text-3">
                          {report.reporterLabel} · {formatAgo(report.capturedAt, now)}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <aside className="scroll-y min-h-0 border-l border-line bg-surface-1 p-4">
          {!selected ? (
            <EmptyState icon={FileText} title="No report selected" />
          ) : (
            <div className="space-y-3">
              <div>
                <h2 className="text-[15px] leading-snug font-semibold text-text">
                  {selected.title}
                </h2>
                <p className="mt-1 text-[12px] leading-relaxed text-text-2">{selected.body}</p>
              </div>

              {selected.kind === 'AUDIO' && (
                <AudioPlayer clipId={selected.id} />
              )}

              {(selected.kind === 'PHOTO' || selected.kind === 'VIDEO') && (
                <div className="flex aspect-16/9 flex-col items-center justify-center gap-1.5 rounded-md border border-line bg-surface-3">
                  {selected.kind === 'PHOTO' ? (
                    <ImageIcon size={22} className="text-line-2" aria-hidden />
                  ) : (
                    <Video size={22} className="text-line-2" aria-hidden />
                  )}
                  <p className="text-[11px] text-text-3">
                    {selected.kind === 'PHOTO' ? 'Photograph' : 'Video'} captured in the field
                  </p>
                  <p className="max-w-[240px] text-center text-[10px] text-text-3">
                    The file itself is not bundled with the demonstration dataset, so no preview is
                    shown rather than a placeholder standing in for real evidence.
                  </p>
                </div>
              )}

              <Panel title="Record" bodyClassName="grid grid-cols-2 gap-3 p-3">
                <Field label="Reporter">{selected.reporterLabel}</Field>
                <Field label="Type">{KIND_META[selected.kind].label}</Field>
                <Field label="Captured" mono>
                  {formatClock(selected.capturedAt)}
                </Field>
                <Field label="Synchronised">
                  {selected.syncedAt ? formatAgo(selected.syncedAt, now) : 'Not yet uploaded'}
                </Field>
                <Field label="Coordinates" mono>
                  {selected.location ? formatCoords(selected.location) : 'No position'}
                </Field>
                <Field label="Distance from origin" mono>
                  {formatDistance(safeDistanceMeters(derived.origin.point, selected.location))}
                </Field>
                <Field label="Team">{selected.teamId ?? 'None'}</Field>
                <Field label="Identifier" mono>
                  {selected.id}
                </Field>
              </Panel>

              <div className="flex items-center gap-2">
                <SourceTag source={selected.teamId ? 'rescuer' : 'mesh'} />
                {selected.emergencyId && (
                  <button
                    type="button"
                    onClick={() => {
                      selectEmergency(selected.emergencyId!);
                      router.push('/emergencies');
                    }}
                    className="num text-[11.5px] text-rescuer-text hover:underline"
                  >
                    Linked to {selected.emergencyId}
                  </button>
                )}
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
