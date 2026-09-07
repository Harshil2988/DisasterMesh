'use client';

import { useState } from 'react';
import { ChevronDown, Eye, EyeOff } from 'lucide-react';
import { useOps, type MapLayers } from '@/state/ops-store';
import { CATEGORY_META, CATEGORY_ORDER, ROLE_COLORS } from '@/lib/constants';
import { CATEGORY_ICON, cx } from '@/components/ui/primitives';

interface LegendRow {
  label: string;
  colour: string;
  shape: 'pin' | 'diamond' | 'square' | 'ring' | 'dash';
  layer: keyof MapLayers;
}

const ROLE_ROWS: LegendRow[] = [
  { label: 'Rescue team', colour: ROLE_COLORS.rescuer.mark, shape: 'diamond', layer: 'teams' },
  { label: 'Hospital', colour: ROLE_COLORS.hospital.mark, shape: 'square', layer: 'hospitals' },
  { label: 'Hazard / blocked road', colour: ROLE_COLORS.hazard.mark, shape: 'ring', layer: 'hazards' },
  { label: 'High-impact area', colour: '#f59e0b', shape: 'dash', layer: 'clusters' },
  { label: 'Computed route', colour: ROLE_COLORS.rescuer.mark, shape: 'dash', layer: 'route' },
  { label: 'Mesh nodes', colour: ROLE_COLORS.system.mark, shape: 'ring', layer: 'meshNodes' },
];

function Swatch({ colour, shape }: { colour: string; shape: LegendRow['shape'] }): React.JSX.Element {
  if (shape === 'diamond') {
    return (
      <span
        className="size-2.5 shrink-0 rotate-45 border"
        style={{ borderColor: colour, background: `${colour}33` }}
        aria-hidden
      />
    );
  }
  if (shape === 'square') {
    return (
      <span
        className="size-2.5 shrink-0 rounded-xs border"
        style={{ borderColor: colour, background: `${colour}33` }}
        aria-hidden
      />
    );
  }
  if (shape === 'ring') {
    return (
      <span
        className="size-2.5 shrink-0 rounded-full border"
        style={{ borderColor: colour, background: `${colour}22` }}
        aria-hidden
      />
    );
  }
  if (shape === 'dash') {
    return (
      <span
        className="h-0 w-3 shrink-0 border-t-2 border-dashed"
        style={{ borderColor: colour }}
        aria-hidden
      />
    );
  }
  return <span className="size-2.5 shrink-0 rounded-full" style={{ background: colour }} aria-hidden />;
}

/**
 * Legend and layer switch in one control.
 *
 * They belong together: the question "what is that orange pin?" and the
 * question "can I hide the orange pins?" are asked half a second apart.
 */
export function MapLegend(): React.JSX.Element {
  const { layers, toggleLayer, filters, toggleCategory } = useOps();
  // On a narrow laptop or tablet the panel covers a meaningful share of the
  // map, so it starts collapsed there. This is the INITIAL state only — once
  // the operator opens it, it stays open. Safe to read `window` in the
  // initialiser because the map canvas is a client-only chunk.
  const [open, setOpen] = useState(
    () => typeof window === 'undefined' || !window.matchMedia('(max-width: 1279px)').matches,
  );

  return (
    <div className="w-[186px] overflow-hidden rounded-md border border-line-2 bg-surface-2/95 backdrop-blur">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-2.5 py-1.5 text-left transition-colors hover:bg-surface-3"
      >
        <span className="eyebrow flex-1">Legend &amp; layers</span>
        <ChevronDown
          size={12}
          className={cx('text-text-3 transition-transform duration-200', open && 'rotate-180')}
          aria-hidden
        />
      </button>

      {open && (
        <div className="space-y-2 border-t border-line px-2.5 py-2">
          <div className="space-y-0.5">
            {CATEGORY_ORDER.map((category) => {
              const meta = CATEGORY_META[category];
              const Icon = CATEGORY_ICON[category];
              const on = filters.categories.has(category);
              return (
                <button
                  key={category}
                  type="button"
                  onClick={() => toggleCategory(category)}
                  aria-pressed={on}
                  className={cx(
                    'flex w-full items-center gap-2 rounded-xs px-1 py-1 text-left text-[11px] transition-colors',
                    on ? 'text-text-2 hover:bg-surface-3' : 'text-text-3 opacity-45 hover:opacity-70',
                  )}
                >
                  <Icon size={11} style={{ color: meta.mark }} aria-hidden />
                  <span className="flex-1 truncate">{meta.label}</span>
                  {on ? (
                    <Eye size={10} className="text-text-3" aria-hidden />
                  ) : (
                    <EyeOff size={10} className="text-text-3" aria-hidden />
                  )}
                </button>
              );
            })}
          </div>

          <div className="space-y-0.5 border-t border-line pt-1.5">
            {ROLE_ROWS.map((row) => {
              const on = layers[row.layer];
              return (
                <button
                  key={row.label}
                  type="button"
                  onClick={() => toggleLayer(row.layer)}
                  aria-pressed={on}
                  className={cx(
                    'flex w-full items-center gap-2 rounded-xs px-1 py-1 text-left text-[11px] transition-colors',
                    on ? 'text-text-2 hover:bg-surface-3' : 'text-text-3 opacity-45 hover:opacity-70',
                  )}
                >
                  <Swatch colour={row.colour} shape={row.shape} />
                  <span className="flex-1 truncate">{row.label}</span>
                  {on ? (
                    <Eye size={10} className="text-text-3" aria-hidden />
                  ) : (
                    <EyeOff size={10} className="text-text-3" aria-hidden />
                  )}
                </button>
              );
            })}
          </div>

          <p className="border-t border-line pt-1.5 text-[9.5px] leading-tight text-text-3">
            Hiding a layer never discards data — filters affect the view only.
          </p>
        </div>
      )}
    </div>
  );
}
