'use client';

import { useState } from 'react';
import { Network, Radio, Server, Smartphone } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { MeshNode, MeshNodeKind } from '@/lib/types';
import { formatAgo } from '@/lib/format';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, Field, Panel, cx } from '@/components/ui/primitives';
import { MeshGraph } from '@/components/ops/MeshGraph';

const KIND_LABEL: Record<MeshNodeKind, string> = {
  CIVILIAN: 'Civilian node',
  RESCUER: 'Rescuer device',
  GATEWAY: 'Gateway',
};

const STATE_COLOUR = {
  CONNECTED: 'var(--color-safe-text)',
  RECENTLY_SYNCED: 'var(--color-warning-text)',
  DISCONNECTED: 'var(--color-text-3)',
} as const;

export default function NetworkPage(): React.JSX.Element {
  const { dataset, now } = useOps();
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  if (!dataset) return <></>;

  const nodes = dataset.meshNodes;
  const selected: MeshNode | null = nodes.find((n) => n.id === selectedNodeId) ?? null;

  const counts = {
    civilian: nodes.filter((n) => n.kind === 'CIVILIAN').length,
    rescuer: nodes.filter((n) => n.kind === 'RESCUER').length,
    gateway: nodes.filter((n) => n.kind === 'GATEWAY').length,
    connected: nodes.filter((n) => n.state === 'CONNECTED').length,
    positioned: nodes.filter((n) => n.location).length,
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="Mesh Network"
        subtitle="What is actually known about the network that carried these reports."
      />

      <div className="scroll-y min-h-0 flex-1 space-y-3 p-4">
        {nodes.length === 0 ? (
          <Panel>
            <EmptyState
              icon={Network}
              title="No mesh nodes reported"
              detail="Node information reaches this console only when a rescuer device uploads it. Nothing has arrived yet."
            />
          </Panel>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-2 lg:grid-cols-5">
              {[
                { label: 'Civilian nodes', value: counts.civilian, icon: Smartphone },
                { label: 'Rescuer devices', value: counts.rescuer, icon: Radio },
                { label: 'Gateways', value: counts.gateway, icon: Server },
                { label: 'Connected now', value: counts.connected, icon: Network },
                { label: 'Known position', value: counts.positioned, icon: Network },
              ].map((figure) => {
                const Icon = figure.icon;
                return (
                  <div key={figure.label} className="rounded-md border border-line bg-surface-2 p-3">
                    <div className="flex items-center gap-1.5">
                      <Icon size={11} className="text-text-3" aria-hidden />
                      <p className="eyebrow leading-tight">{figure.label}</p>
                    </div>
                    <p className="num mt-1 text-[20px] leading-none font-semibold text-text">
                      {figure.value}
                    </p>
                  </div>
                );
              })}
            </div>

            <Panel
              title="Recorded topology"
              subtitle={`${dataset.meshLinks.length} observed links`}
              icon={Network}
              bodyClassName="p-3"
            >
              <MeshGraph
                nodes={nodes}
                links={dataset.meshLinks}
                onSelect={setSelectedNodeId}
                selectedId={selectedNodeId}
              />
            </Panel>

            <div className="grid gap-3 lg:grid-cols-[1.5fr_1fr]">
              <Panel title="Nodes" flush bodyClassName="scroll-y max-h-[420px]">
                <table className="w-full border-collapse">
                  <caption className="sr-only">Mesh nodes</caption>
                  <thead className="sticky top-0 bg-surface-1">
                    <tr className="border-b border-line">
                      {['Node', 'Role', 'State', 'Reports', 'Position', 'Last heard'].map((heading) => (
                        <th
                          key={heading}
                          scope="col"
                          className="px-3 py-1.5 text-left text-[10px] font-semibold tracking-wider text-text-3 uppercase"
                        >
                          {heading}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {[...nodes]
                      .sort(
                        (a, b) =>
                          b.reportsContributed - a.reportsContributed ||
                          a.kind.localeCompare(b.kind),
                      )
                      .map((node) => (
                        <tr
                          key={node.id}
                          onClick={() => setSelectedNodeId(node.id)}
                          className={cx(
                            'cursor-pointer border-b border-line transition-colors last:border-b-0',
                            selectedNodeId === node.id ? 'bg-surface-3' : 'hover:bg-surface-3',
                          )}
                        >
                          <th scope="row" className="num px-3 py-1.5 text-left text-[11.5px] text-text">
                            {node.id}
                          </th>
                          <td className="px-3 py-1.5 text-[11px] text-text-3">
                            {KIND_LABEL[node.kind]}
                          </td>
                          <td
                            className="px-3 py-1.5 text-[11px]"
                            style={{ color: STATE_COLOUR[node.state] }}
                          >
                            {node.state.replace('_', ' ').toLowerCase()}
                          </td>
                          <td className="num px-3 py-1.5 text-[11px] text-text-2">
                            {node.reportsContributed || '—'}
                          </td>
                          <td className="px-3 py-1.5 text-[11px] text-text-3">
                            {node.location ? 'known' : <span className="italic">not reported</span>}
                          </td>
                          <td className="num px-3 py-1.5 text-[11px] text-text-3">
                            {formatAgo(node.lastHeardAt, now)}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </Panel>

              <div className="space-y-3">
                <Panel title="Node detail" bodyClassName="p-3">
                  {!selected ? (
                    <EmptyState
                      icon={Network}
                      title="No node selected"
                      detail="Choose a node from the diagram or the table."
                    />
                  ) : (
                    <div className="grid grid-cols-2 gap-3">
                      <Field label="Node id" mono>
                        {selected.id}
                      </Field>
                      <Field label="Role">{KIND_LABEL[selected.kind]}</Field>
                      <Field label="State">{selected.state.replace('_', ' ').toLowerCase()}</Field>
                      <Field label="Reports contributed" mono>
                        {selected.reportsContributed}
                      </Field>
                      <Field label="Last heard">{formatAgo(selected.lastHeardAt, now)}</Field>
                      <Field label="Device">
                        {selected.deviceModel ?? (
                          <span className="text-text-3 italic">Not reported</span>
                        )}
                      </Field>
                      <div className="col-span-2">
                        <Field label="Position">
                          {selected.location ? (
                            'Known from a report this node originated'
                          ) : (
                            <span className="text-text-3 italic">
                              Not reported — a relay does not broadcast its own position
                            </span>
                          )}
                        </Field>
                      </div>
                    </div>
                  )}
                </Panel>

                <Panel title="What this console does not know" bodyClassName="p-3">
                  <ul className="space-y-1.5 text-[11px] leading-relaxed text-text-3">
                    {[
                      'The exact node-by-node path a report took. The relay copies the message envelope without appending a route, so only the hop count survives.',
                      'The position of any node that has not itself originated a report carrying coordinates.',
                      'Link quality, signal strength or radio range — Nearby Connections does not expose them.',
                      'Nodes that never reached a rescuer device. If nothing was heard from them, they are not here.',
                    ].map((line) => (
                      <li key={line} className="flex gap-1.5">
                        <span className="mt-[6px] size-1 shrink-0 rounded-full bg-line-2" aria-hidden />
                        <span>{line}</span>
                      </li>
                    ))}
                  </ul>
                </Panel>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
