'use client';

import { useMemo, useState } from 'react';
import type { MeshLink, MeshNode } from '@/lib/types';
import { ROLE_COLORS } from '@/lib/constants';
import { cx } from '@/components/ui/primitives';

/**
 * Mesh topology, drawn from recorded adjacencies only.
 *
 * The layout is by ROLE, not by geography: civilian nodes on the left,
 * rescuers in the middle, gateways on the right, the command centre at the
 * end. That is deliberate — the mesh does not report node positions, so a
 * map-like layout would be an invented picture. What is real is who was heard
 * by whom, and that is what the edges show.
 *
 * A node with no recorded link is drawn unconnected rather than attached to a
 * plausible neighbour.
 */
export function MeshGraph({
  nodes,
  links,
  onSelect,
  selectedId,
}: {
  nodes: MeshNode[];
  links: MeshLink[];
  onSelect: (id: string) => void;
  selectedId: string | null;
}): React.JSX.Element {
  const [hover, setHover] = useState<string | null>(null);

  const layout = useMemo(() => {
    const civilians = nodes.filter((n) => n.kind === 'CIVILIAN');
    const rescuers = nodes.filter((n) => n.kind === 'RESCUER');
    const gateways = nodes.filter((n) => n.kind === 'GATEWAY');

    const width = 760;
    const height = Math.max(340, Math.ceil(civilians.length / 4) * 34 + 80);

    const positions = new Map<string, { x: number; y: number }>();

    // Civilians: a grid on the left. Four columns keeps labels readable.
    const columns = 4;
    civilians.forEach((node, index) => {
      const col = index % columns;
      const row = Math.floor(index / columns);
      positions.set(node.id, {
        x: 44 + col * 62,
        y: 46 + row * 32,
      });
    });

    const spread = (count: number, index: number): number =>
      count === 1 ? height / 2 : 60 + (index * (height - 120)) / (count - 1);

    rescuers.forEach((node, index) => {
      positions.set(node.id, { x: width * 0.56, y: spread(rescuers.length, index) });
    });
    gateways.forEach((node, index) => {
      positions.set(node.id, { x: width * 0.78, y: spread(gateways.length, index) });
    });

    return { positions, width, height, civilians, rescuers, gateways };
  }, [nodes]);

  const colourFor = (node: MeshNode): string =>
    node.kind === 'GATEWAY'
      ? ROLE_COLORS.system.mark
      : node.kind === 'RESCUER'
        ? ROLE_COLORS.rescuer.mark
        : '#64748b';

  const active = hover ?? selectedId;
  const activeLinks = new Set(
    links.filter((l) => l.fromId === active || l.toId === active).map((l) => `${l.fromId}->${l.toId}`),
  );

  const commandPost = { x: layout.width - 42, y: layout.height / 2 };

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${layout.width} ${layout.height}`}
        className="h-auto w-full min-w-[680px]"
        role="img"
        aria-label={`Mesh topology: ${layout.civilians.length} civilian nodes, ${layout.rescuers.length} rescuer nodes, ${layout.gateways.length} gateways, ${links.length} recorded links`}
      >
        {/* column labels */}
        {[
          { x: 96, label: 'Civilian nodes' },
          { x: layout.width * 0.56, label: 'Rescuer devices' },
          { x: layout.width * 0.78, label: 'Gateways' },
        ].map((column) => (
          <text
            key={column.label}
            x={column.x}
            y={18}
            textAnchor="middle"
            fill="#94a3b8"
            fontSize="9.5"
            fontWeight="600"
            letterSpacing="1"
            style={{ textTransform: 'uppercase' }}
          >
            {column.label}
          </text>
        ))}

        {/* recorded links */}
        <g>
          {links.map((link, index) => {
            const from = layout.positions.get(link.fromId);
            const to = layout.positions.get(link.toId);
            if (!from || !to) return null;
            const key = `${link.fromId}->${link.toId}`;
            const highlighted = active !== null && activeLinks.has(key);
            const midX = (from.x + to.x) / 2;

            return (
              <path
                key={`${key}-${index}`}
                d={`M ${from.x} ${from.y} C ${midX} ${from.y}, ${midX} ${to.y}, ${to.x} ${to.y}`}
                fill="none"
                stroke={highlighted ? ROLE_COLORS.rescuer.mark : '#2b3650'}
                strokeWidth={highlighted ? 1.5 : 0.8}
                opacity={active !== null && !highlighted ? 0.25 : 0.9}
              />
            );
          })}

          {/* gateway → command centre */}
          {layout.gateways.map((gateway) => {
            const from = layout.positions.get(gateway.id);
            if (!from) return null;
            return (
              <path
                key={`cmd-${gateway.id}`}
                d={`M ${from.x} ${from.y} C ${(from.x + commandPost.x) / 2} ${from.y}, ${
                  (from.x + commandPost.x) / 2
                } ${commandPost.y}, ${commandPost.x} ${commandPost.y}`}
                fill="none"
                stroke={gateway.state === 'CONNECTED' ? ROLE_COLORS.system.mark : '#2b3650'}
                strokeWidth="1.2"
                strokeDasharray={gateway.state === 'CONNECTED' ? undefined : '3 3'}
                opacity="0.8"
              />
            );
          })}
        </g>

        {/* nodes */}
        <g>
          {nodes.map((node) => {
            const position = layout.positions.get(node.id);
            if (!position) return null;
            const colour = colourFor(node);
            const radius = node.kind === 'CIVILIAN' ? 4.5 : 7;
            const connected = node.state === 'CONNECTED';
            const isActive = active === node.id;

            return (
              <g
                key={node.id}
                transform={`translate(${position.x} ${position.y})`}
                onMouseEnter={() => setHover(node.id)}
                onMouseLeave={() => setHover(null)}
                onClick={() => onSelect(node.id)}
                style={{ cursor: 'pointer' }}
              >
                {isActive && <circle r={radius + 4} fill="none" stroke={colour} strokeWidth="1" opacity="0.6" />}
                <circle
                  r={radius}
                  fill={connected ? colour : '#0b1020'}
                  stroke={colour}
                  strokeWidth="1.5"
                  opacity={node.state === 'DISCONNECTED' ? 0.5 : 1}
                />
                {node.kind !== 'CIVILIAN' && (
                  <text
                    x={0}
                    y={radius + 11}
                    textAnchor="middle"
                    fill="#cbd5e1"
                    fontSize="8.5"
                    fontFamily="ui-monospace, monospace"
                  >
                    {node.id}
                  </text>
                )}
                <title>
                  {node.id} — {node.kind.toLowerCase()}, {node.state.replace('_', ' ').toLowerCase()}
                  {node.reportsContributed > 0 ? `, ${node.reportsContributed} reports` : ''}
                </title>
              </g>
            );
          })}
        </g>

        {/* command centre */}
        <g transform={`translate(${commandPost.x} ${commandPost.y})`}>
          <rect
            x={-14}
            y={-10}
            width={28}
            height={20}
            rx={3}
            fill="#0b1020"
            stroke={ROLE_COLORS.system.mark}
            strokeWidth="1.6"
          />
          <text
            x={0}
            y={22}
            textAnchor="middle"
            fill="#a78bfa"
            fontSize="8.5"
            fontWeight="600"
            fontFamily="ui-monospace, monospace"
          >
            COMMAND
          </text>
        </g>
      </svg>

      <p className={cx('mt-2 text-[10px] leading-relaxed text-text-3')}>
        Positions here are by role, not geography — the mesh does not report node coordinates, so no
        spatial layout would be truthful. Edges are recorded adjacencies only: a link is drawn when a
        report from that node actually reached that collector. Nothing is inferred.
      </p>
    </div>
  );
}
