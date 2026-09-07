'use client';

import { ArrowDown, Network, Radio, Smartphone } from 'lucide-react';
import type { MeshProvenance as Provenance } from '@/lib/types';
import { formatAgo, formatClock } from '@/lib/format';
import { SourceTag } from '@/components/ui/primitives';

/**
 * Where this report came from.
 *
 * The critical restraint: the Android relay copies the message envelope
 * without appending a route, so hop count is known and the node-by-node path
 * is not. This panel therefore says "relayed through 3 hops" and refuses to
 * draw "PHONE A → PHONE B → PHONE C" unless the path was genuinely recorded.
 */
export function MeshProvenance({ mesh, now }: { mesh: Provenance; now: number }): React.JSX.Element {
  const stages = [
    {
      icon: Smartphone,
      label: 'Originating node',
      value: mesh.senderNodeId,
      detail: `Reported ${formatClock(mesh.originTimestamp)}`,
      tag: 'mesh' as const,
    },
    {
      icon: Network,
      label: 'Mesh relay',
      value:
        mesh.hopCount === undefined
          ? 'Hop count not recorded'
          : mesh.hopCount === 0
            ? 'Received directly — no relay'
            : `Relayed through ${mesh.hopCount} ${mesh.hopCount === 1 ? 'hop' : 'hops'}`,
      detail: mesh.pathRecorded
        ? `Path: ${mesh.relayPath?.join(' → ') ?? 'unavailable'}`
        : 'The exact node path is not recorded by the mesh, so it is not shown.',
      tag: 'mesh' as const,
    },
    {
      icon: Radio,
      label: 'Collected by',
      value: mesh.collectedByRescuerId ?? 'Not recorded',
      detail: mesh.lastSyncedAt
        ? `Synchronised to this console ${formatAgo(mesh.lastSyncedAt, now)}`
        : 'Not yet synchronised',
      tag: 'rescuer' as const,
    },
  ];

  return (
    <ol className="space-y-0">
      {stages.map((stage, index) => {
        const Icon = stage.icon;
        return (
          <li key={stage.label}>
            <div className="flex gap-2.5">
              <div className="flex flex-col items-center">
                <span className="grid size-6 shrink-0 place-items-center rounded-sm border border-line bg-surface-3">
                  <Icon size={12} className="text-text-3" aria-hidden />
                </span>
                {index < stages.length - 1 && <span className="w-px flex-1 bg-line" aria-hidden />}
              </div>
              <div className="min-w-0 flex-1 pb-3">
                <div className="flex items-center gap-1.5">
                  <span className="eyebrow">{stage.label}</span>
                  <SourceTag source={stage.tag} />
                </div>
                <p className="num mt-0.5 text-[12px] text-text">{stage.value}</p>
                <p className="mt-0.5 text-[10.5px] leading-snug text-text-3">{stage.detail}</p>
              </div>
            </div>
            {index < stages.length - 1 && (
              <ArrowDown size={0} className="sr-only" aria-hidden />
            )}
          </li>
        );
      })}
    </ol>
  );
}
