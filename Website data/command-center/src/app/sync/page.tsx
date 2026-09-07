'use client';

import { CircleCheck, Info, RefreshCw, TriangleAlert, Upload, Wifi } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { formatAgo, formatClockSeconds } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { Button, EmptyState, Panel, cx } from '@/components/ui/primitives';
import { PipelineDiagram } from '@/components/ops/PipelineDiagram';

const KIND_STYLE = {
  GATEWAY: { icon: Wifi, colour: 'var(--color-rescuer-text)' },
  UPLOAD: { icon: Upload, colour: 'var(--color-supply-text)' },
  SUCCESS: { icon: CircleCheck, colour: 'var(--color-safe-text)' },
  FAILURE: { icon: TriangleAlert, colour: 'var(--color-critical-text)' },
  INFO: { icon: Info, colour: 'var(--color-text-3)' },
} as const;

export default function SyncPage(): React.JSX.Element {
  const { dataset, derived, now, mode, demoSimulateSync, reload } = useOps();

  if (!dataset) return <></>;

  const { connection } = dataset;
  const hasGateway = connection.gatewaysOnline > 0;

  const figures = [
    {
      label: 'Last synchronisation',
      value: connection.lastSyncedAt ? formatAgo(connection.lastSyncedAt, now) : 'Waiting for gateway connection',
      tone: connection.lastSyncedAt ? undefined : 'var(--color-warning-text)',
    },
    {
      label: 'Records synchronised',
      value: String(derived.stats.reportsSynchronised),
    },
    {
      label: 'Pending uploads',
      value: String(connection.pendingUploads),
      tone: connection.pendingUploads > 0 ? 'var(--color-warning-text)' : undefined,
    },
    {
      label: 'Rescuer gateways online',
      value: hasGateway ? String(connection.gatewaysOnline) : 'None',
      tone: hasGateway ? 'var(--color-safe-text)' : 'var(--color-warning-text)',
    },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Synchronisation"
        subtitle="How field-collected data reaches this console, and when it last did."
        actions={
          mode === 'DEMO' ? (
            <Button size="sm" icon={RefreshCw} onClick={demoSimulateSync}>
              Simulate gateway sync
            </Button>
          ) : (
            <Button size="sm" icon={RefreshCw} onClick={reload}>
              Poll for new data
            </Button>
          )
        }
      />

      <div className="scroll-y min-h-0 flex-1 space-y-3 p-4">
        <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
          {figures.map((figure) => (
            <div key={figure.label} className="rounded-md border border-line bg-surface-2 p-3">
              <p className="eyebrow">{figure.label}</p>
              <p
                className="num mt-1 text-[19px] leading-none font-semibold"
                style={{ color: figure.tone ?? 'var(--color-text)' }}
              >
                {figure.value}
              </p>
            </div>
          ))}
        </div>

        {!hasGateway && (
          <div className="flex items-start gap-2 rounded-md border border-warning/30 bg-warning/8 px-3 py-2.5">
            <TriangleAlert size={14} className="mt-px shrink-0 text-warning-text" aria-hidden />
            <div>
              <p className="text-[12px] font-medium text-warning-text">
                Waiting for gateway connection
              </p>
              <p className="mt-0.5 text-[11.5px] text-text-2">
                No rescuer device currently has a verified path to the internet. The mesh keeps
                working and reports keep accumulating on rescuer devices — they simply have not
                reached this console yet.
              </p>
            </div>
          </div>
        )}

        {/* ---- the architecture, horizontally ---- */}
        <Panel
          title="Offline to online"
          subtitle="Civilian mesh → rescuer device → offline store → gateway → command centre"
          bodyClassName="p-3"
        >
          <PipelineDiagram orientation="horizontal" />
          <p className="mt-3 border-t border-line pt-2.5 text-[11px] leading-relaxed text-text-3">
            The mesh itself never needs the internet. A report crosses phone-to-phone links,
            store-and-forward and any number of relay hops entirely offline. Only at the gateway
            stage does a rescuer device with a verified connection upload what it has collected —
            which is why the data on this console can be minutes old even when everything is
            working correctly.
          </p>
        </Panel>

        <div className="grid gap-3 lg:grid-cols-[1.4fr_1fr]">
          <Panel title="Synchronisation log" icon={RefreshCw} flush bodyClassName="scroll-y max-h-[420px]">
            {dataset.syncEvents.length === 0 ? (
              <EmptyState
                icon={RefreshCw}
                title="No synchronisation activity"
                detail="Nothing has been uploaded to this console yet."
              />
            ) : (
              <ol>
                {dataset.syncEvents.map((event) => {
                  const style = KIND_STYLE[event.kind];
                  const Icon = style.icon;
                  return (
                    <li
                      key={event.id}
                      className="flex items-start gap-2.5 border-b border-line px-3 py-2 last:border-b-0"
                    >
                      <time
                        className="num w-[46px] shrink-0 pt-px text-[11px] text-text-3"
                        dateTime={new Date(event.at).toISOString()}
                      >
                        {formatClockSeconds(event.at).slice(0, 5)}
                      </time>
                      <Icon
                        size={12}
                        className="mt-0.5 shrink-0"
                        style={{ color: style.colour }}
                        aria-hidden
                      />
                      <div className="min-w-0 flex-1">
                        <p className="text-[12px] leading-snug text-text-2">{event.text}</p>
                        <p className="num text-[10px] text-text-3">
                          {event.gatewayId}
                          {event.recordCount !== undefined && ` · ${event.recordCount} records`}
                        </p>
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}
          </Panel>

          <Panel title="Endpoint" subtitle="What a rescuer device uploads to" bodyClassName="p-3 space-y-2.5">
            <div>
              <p className="eyebrow mb-1">Ingest endpoint</p>
              <code className="num block rounded-sm border border-line bg-void px-2 py-1.5 text-[11px] break-all text-rescuer-text">
                POST /api/emergency
              </code>
              <p className="mt-1.5 text-[10.5px] leading-relaxed text-text-3">
                Accepts exactly the JSON body the Android app’s <span className="num">InternetUplink</span>{' '}
                already sends — <span className="num">reportId</span>,{' '}
                <span className="num">senderNodeId</span>, <span className="num">category</span>,{' '}
                <span className="num">priority</span>, <span className="num">description</span>,{' '}
                <span className="num">timestamp</span>, and optional{' '}
                <span className="num">latitude</span>, <span className="num">longitude</span>,{' '}
                <span className="num">peopleAffected</span>, <span className="num">hops</span>. A
                handset pointed at this URL needs no adapter.
              </p>
            </div>

            <div className="space-y-1 border-t border-line pt-2">
              {[
                { method: 'GET', path: '/api/status', note: 'Cheap health probe for a rescuer device.' },
                { method: 'GET', path: '/api/emergency', note: 'Everything this console has received.' },
                { method: 'GET', path: '/api/dataset', note: 'Full operating picture, as LIVE mode reads it.' },
              ].map((row) => (
                <div key={row.path} className="flex items-baseline gap-2">
                  <span className="num w-9 shrink-0 text-[10px] font-semibold text-text-3">
                    {row.method}
                  </span>
                  <span className="num text-[11px] text-text-2">{row.path}</span>
                  <span className="truncate text-[10px] text-text-3">{row.note}</span>
                </div>
              ))}
            </div>

            <div
              className={cx(
                'rounded-sm border px-2.5 py-2',
                mode === 'DEMO' ? 'border-warning/30 bg-warning/8' : 'border-rescuer/30 bg-rescuer/8',
              )}
            >
              <p
                className="text-[11px] leading-relaxed"
                style={{
                  color: mode === 'DEMO' ? 'var(--color-warning-text)' : 'var(--color-rescuer-text)',
                }}
              >
                {mode === 'DEMO'
                  ? 'This console is showing demonstration data. The endpoint above is still live and will accept real uploads — switch to LIVE to see them.'
                  : 'Showing live data. Everything on screen was uploaded to this endpoint by a rescuer gateway.'}
              </p>
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
