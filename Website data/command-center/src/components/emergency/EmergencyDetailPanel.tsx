'use client';

import { useState } from 'react';
import {
  Ban,
  CircleCheck,
  Clock,
  Eye,
  Hospital as HospitalIcon,
  MapPin,
  Network,
  Route as RouteIcon,
  Send,
  Siren,
  StickyNote,
  Target,
  Users,
} from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { ScoredEmergency } from '@/lib/recommendations';
import { CATEGORY_META } from '@/lib/constants';
import { formatCoords, formatDistance, safeDistanceMeters } from '@/lib/geo';
import { formatAgo, formatClock, formatDuration } from '@/lib/format';
import {
  Button,
  CategoryBadge,
  Field,
  PriorityBadge,
  SourceTag,
  StatusBadge,
  cx,
} from '@/components/ui/primitives';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { PriorityScore } from '@/components/ops/PriorityScore';
import { DistanceIndicator } from '@/components/ops/DistanceIndicator';
import { RoutePanel } from '@/components/ops/RoutePanel';
import { AssignTeamDialog } from '@/components/ops/AssignTeamDialog';
import { HospitalRecommendation } from '@/components/ops/HospitalRecommendation';
import { MeshProvenance } from './MeshProvenance';
import { AudioPlayer, EvidenceViewer } from './MediaPanel';

type Tab = 'DETAIL' | 'ROUTE' | 'HOSPITALS' | 'ORIGIN';

const TABS: { id: Tab; label: string; icon: typeof Eye }[] = [
  { id: 'DETAIL', label: 'Detail', icon: Eye },
  { id: 'ROUTE', label: 'Route', icon: RouteIcon },
  { id: 'HOSPITALS', label: 'Destination', icon: HospitalIcon },
  { id: 'ORIGIN', label: 'Origin', icon: Network },
];

export function EmergencyDetailPanel({
  emergency,
}: {
  emergency: ScoredEmergency;
}): React.JSX.Element {
  const {
    dataset, derived, now, route, routeSubjectId, routeError,
    setEmergencyStatus, calculateRoute, addNote, unassign, fit,
  } = useOps();

  const [tab, setTab] = useState<Tab>('DETAIL');
  const [assignOpen, setAssignOpen] = useState(false);
  const [invalidOpen, setInvalidOpen] = useState(false);
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [note, setNote] = useState('');

  const meta = CATEGORY_META[emergency.category];
  const isCritical = emergency.category === 'CRITICAL';
  const team = emergency.assignedTeamId
    ? (dataset?.teams.find((t) => t.id === emergency.assignedTeamId) ?? null)
    : null;

  // Distance is measured from the assigned team when there is one — that is
  // the vehicle that actually has to get there.
  const originPoint = team?.location ?? derived.origin.point;
  const originLabel = team ? team.name : derived.origin.label;
  const distance = safeDistanceMeters(originPoint, emergency.location);

  const unavailableReason = !emergency.location
    ? 'Distance unavailable — this report carries no coordinates'
    : team && !team.location
      ? `Distance unavailable — ${team.name} has not reported a position`
      : 'Distance unavailable — no origin position';

  const routeForThis = routeSubjectId === emergency.id ? route : null;

  return (
    <div className="flex h-full min-h-0 flex-col">
      {/* ---------- header ---------- */}
      <header
        className={cx('shrink-0 border-b border-line px-4 py-3')}
        style={
          isCritical
            ? { background: `linear-gradient(180deg, rgb(${meta.rgb} / 0.14), transparent)` }
            : undefined
        }
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              {isCritical && (
                <span
                  className={cx(
                    'flex items-center gap-1.5 rounded-xs border border-critical/50 bg-critical/15 px-1.5 py-0.5',
                    emergency.status === 'UNASSIGNED' && 'anim-sos',
                  )}
                >
                  <Siren size={12} className="text-critical-text" aria-hidden />
                  <span className="text-[11px] font-bold tracking-widest text-critical-text">SOS</span>
                </span>
              )}
              <CategoryBadge category={emergency.category} size="md" />
              <PriorityBadge priority={emergency.priority} score={emergency.breakdown.score} />
              <StatusBadge status={emergency.status} />
            </div>
            <h1 className="num mt-1.5 text-[19px] leading-none font-semibold tracking-tight text-text">
              {emergency.id}
            </h1>
            <p className="mt-1.5 text-[12.5px] leading-snug text-text-2">{emergency.description}</p>
          </div>

          <Button
            size="sm"
            variant="ghost"
            icon={Target}
            onClick={() => fit('SELECTED')}
            disabled={!emergency.location}
            title={emergency.location ? 'Centre the map here' : 'No coordinates to centre on'}
          >
            Locate
          </Button>
        </div>
      </header>

      {/* ---------- primary actions ---------- */}
      <div className="flex shrink-0 flex-wrap gap-1.5 border-b border-line bg-surface-1 px-4 py-2.5">
        <Button
          variant="primary"
          icon={Users}
          onClick={() => setAssignOpen(true)}
          disabled={emergency.status === 'INVALID'}
        >
          {team ? 'Reassign team' : 'Assign team'}
        </Button>
        <Button
          icon={RouteIcon}
          onClick={() => {
            calculateRoute(emergency.id, emergency.assignedTeamId);
            setTab('ROUTE');
          }}
          disabled={!emergency.location}
          title={emergency.location ? undefined : 'This report carries no coordinates'}
        >
          Calculate route
        </Button>
        <Button
          icon={Eye}
          onClick={() => setEmergencyStatus(emergency.id, 'ACKNOWLEDGED')}
          disabled={emergency.status !== 'UNASSIGNED'}
        >
          Acknowledge
        </Button>
        <Button
          icon={Clock}
          onClick={() => setEmergencyStatus(emergency.id, 'IN_PROGRESS')}
          disabled={emergency.status === 'RESCUED' || emergency.status === 'RESOLVED'}
        >
          In progress
        </Button>
        <Button
          variant="success"
          icon={CircleCheck}
          onClick={() => setEmergencyStatus(emergency.id, 'RESCUED')}
          disabled={emergency.status === 'RESCUED' || emergency.status === 'INVALID'}
        >
          Mark rescued
        </Button>
        <Button variant="danger" icon={Ban} onClick={() => setInvalidOpen(true)}>
          Mark invalid
        </Button>
      </div>

      {/* ---------- distance + assignment ---------- */}
      <div className="grid shrink-0 grid-cols-1 gap-2 border-b border-line px-4 py-3 lg:grid-cols-2">
        <DistanceIndicator
          meters={distance}
          originLabel={originLabel}
          unavailableReason={unavailableReason}
        />

        {team ? (
          <div className="rounded-sm border border-rescuer/25 bg-rescuer/8 px-2.5 py-2">
            <div className="flex items-center gap-1.5">
              <Users size={11} className="text-rescuer-text" aria-hidden />
              <span className="eyebrow">Assigned team</span>
            </div>
            <div className="mt-1 flex items-baseline justify-between gap-2">
              <span className="text-[15px] leading-none font-semibold text-text">{team.name}</span>
              <StatusBadge status={team.status} kind="team" />
            </div>
            <p className="mt-1.5 text-[10.5px] text-text-3">
              {team.members.length} members · last sync {formatAgo(team.lastSyncedAt, now)}
            </p>
            <button
              type="button"
              onClick={() => setReleaseOpen(true)}
              className="mt-1.5 text-[10.5px] text-warning-text hover:underline"
            >
              Withdraw assignment
            </button>
          </div>
        ) : (
          <div className="flex flex-col justify-center rounded-sm border border-line bg-surface-3 px-2.5 py-2">
            <span className="eyebrow">Assigned team</span>
            <p className="mt-1 text-[12.5px] text-text-2">No team assigned</p>
            <p className="mt-0.5 text-[10.5px] text-text-3">
              This report is still waiting for a dispatch decision.
            </p>
          </div>
        )}
      </div>

      {/* ---------- tabs ---------- */}
      <div className="flex shrink-0 gap-0.5 border-b border-line px-3" role="tablist">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={cx(
              'flex items-center gap-1.5 border-b-2 px-2.5 py-2 text-[11.5px] font-medium transition-colors',
              tab === id
                ? 'border-rescuer text-text'
                : 'border-transparent text-text-3 hover:text-text-2',
            )}
          >
            <Icon size={12} aria-hidden />
            {label}
          </button>
        ))}
      </div>

      {/* ---------- tab body ---------- */}
      <div className="scroll-y min-h-0 flex-1 p-4">
        {tab === 'DETAIL' && (
          <div className="space-y-4">
            <PriorityScore breakdown={emergency.breakdown} />

            <div className="grid grid-cols-2 gap-x-4 gap-y-3 lg:grid-cols-3">
              <Field label="People affected">
                {emergency.peopleAffected !== null ? (
                  <span className="num">{emergency.peopleAffected}</span>
                ) : (
                  <span className="text-text-3 italic">Not reported</span>
                )}
              </Field>
              <Field label="Sector">{emergency.sector ?? <span className="text-text-3 italic">Unresolved</span>}</Field>
              <Field label="Reported" mono>
                {formatClock(emergency.reportedAt)}
              </Field>
              <Field label="Waiting" mono>
                <span className={now - emergency.reportedAt > 30 * 60_000 ? 'text-warning-text' : undefined}>
                  {formatDuration(now - emergency.reportedAt)}
                </span>
              </Field>
              <Field label="Coordinates" mono hint="As reported by the originating device">
                {emergency.location ? (
                  formatCoords(emergency.location)
                ) : (
                  <span className="font-sans text-text-3 italic">No position received</span>
                )}
              </Field>
              <Field label="Distance from origin" mono>
                {formatDistance(distance)}
              </Field>
              <Field label="Vulnerabilities">
                {emergency.vulnerabilities.length > 0 ? (
                  emergency.vulnerabilities.map((v) => v.toLowerCase()).join(', ')
                ) : (
                  <span className="text-text-3 italic">None reported</span>
                )}
              </Field>
              <Field label="Originating node" mono>
                {emergency.mesh.senderNodeId}
              </Field>
              <Field label="Last synchronised">
                {formatAgo(emergency.mesh.lastSyncedAt, now)}
              </Field>
            </div>

            {emergency.invalidReason && (
              <div className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
                <p className="eyebrow">Marked invalid</p>
                <p className="mt-0.5 text-[11.5px] text-text-2">{emergency.invalidReason}</p>
              </div>
            )}

            {/* audio + evidence */}
            <div className="space-y-2">
              <p className="eyebrow">Field media</p>
              {emergency.audioClipId ? (
                <AudioPlayer clipId={emergency.audioClipId} />
              ) : (
                <p className="text-[11px] text-text-3">No audio attached to this report.</p>
              )}
              <EvidenceViewer ids={emergency.evidenceIds} />
            </div>

            {/* notes */}
            <div className="space-y-2">
              <div className="flex items-center gap-1.5">
                <StickyNote size={11} className="text-text-3" aria-hidden />
                <p className="eyebrow">Command notes</p>
                <SourceTag source="command" />
              </div>

              {emergency.notes.length === 0 ? (
                <p className="text-[11px] text-text-3">No notes recorded.</p>
              ) : (
                <ul className="space-y-1.5">
                  {emergency.notes.map((entry) => (
                    <li key={entry.id} className="rounded-sm border border-line bg-surface-3 px-2.5 py-1.5">
                      <p className="text-[11.5px] text-text-2">{entry.text}</p>
                      <p className="num mt-0.5 text-[10px] text-text-3">
                        {entry.authorLabel} · {formatClock(entry.at)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}

              <form
                className="flex gap-1.5"
                onSubmit={(event) => {
                  event.preventDefault();
                  addNote(emergency.id, note);
                  setNote('');
                }}
              >
                <label htmlFor="note-input" className="sr-only">
                  Add a note to {emergency.id}
                </label>
                <input
                  id="note-input"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Add an operational note…"
                  className="h-8 flex-1 rounded-sm border border-line bg-surface-2 px-2.5 text-[12px] text-text placeholder:text-text-3 focus:border-line-2"
                />
                <Button type="submit" icon={Send} disabled={!note.trim()}>
                  Add
                </Button>
              </form>
            </div>
          </div>
        )}

        {tab === 'ROUTE' && (
          <div className="space-y-3">
            <div className="flex flex-wrap gap-1.5">
              <Button
                variant="primary"
                icon={RouteIcon}
                onClick={() => calculateRoute(emergency.id, emergency.assignedTeamId)}
                disabled={!emergency.location}
              >
                {team ? `Route from ${team.name}` : 'Route from origin'}
              </Button>
              {dataset?.teams
                .filter((t) => t.location && t.id !== emergency.assignedTeamId)
                .slice(0, 3)
                .map((t) => (
                  <Button key={t.id} size="sm" onClick={() => calculateRoute(emergency.id, t.id)}>
                    From {t.callsign}
                  </Button>
                ))}
            </div>
            <RoutePanel route={routeForThis} hazards={dataset?.hazards ?? []} error={routeError} />
          </div>
        )}

        {tab === 'HOSPITALS' && (
          <div className="space-y-2">
            <p className="text-[11.5px] text-text-2">
              Recommended destination for casualties from this incident.
            </p>
            <HospitalRecommendation location={emergency.location} />
          </div>
        )}

        {tab === 'ORIGIN' && (
          <div className="space-y-4">
            <div>
              <p className="eyebrow mb-2">How this report reached the command centre</p>
              <MeshProvenance mesh={emergency.mesh} now={now} />
            </div>

            <div className="rounded-sm border border-line bg-surface-3 px-2.5 py-2">
              <div className="flex items-center gap-1.5">
                <MapPin size={11} className="text-text-3" aria-hidden />
                <p className="eyebrow">Position provenance</p>
              </div>
              <p className="mt-1 text-[11px] leading-relaxed text-text-2">
                {emergency.location
                  ? `Coordinates were carried in the report envelope from ${emergency.mesh.senderNodeId}. ` +
                    `The command centre resolved the sector name; it did not adjust the position.`
                  : 'No coordinates were received with this report. The command centre has not estimated one — an invented position would be worse than none.'}
              </p>
            </div>
          </div>
        )}
      </div>

      {/* ---------- dialogs ---------- */}
      <AssignTeamDialog emergency={emergency} open={assignOpen} onClose={() => setAssignOpen(false)} />

      <ConfirmDialog
        open={invalidOpen}
        title={`Mark ${emergency.id} as invalid?`}
        description="The report stays on record and in the timeline. It leaves the active queue and stops being counted as an open incident."
        confirmLabel="Mark invalid"
        confirmVariant="danger"
        onConfirm={() => {
          setEmergencyStatus(emergency.id, 'INVALID', 'Marked invalid by the duty commander.');
          setInvalidOpen(false);
        }}
        onClose={() => setInvalidOpen(false)}
      />

      <ConfirmDialog
        open={releaseOpen}
        title={`Withdraw ${team?.name ?? 'the team'} from ${emergency.id}?`}
        description="The mission is aborted, the team returns to available, and the incident goes back to unassigned."
        confirmLabel="Withdraw assignment"
        confirmVariant="danger"
        onConfirm={() => {
          unassign(emergency.id);
          setReleaseOpen(false);
        }}
        onClose={() => setReleaseOpen(false)}
      />
    </div>
  );
}
