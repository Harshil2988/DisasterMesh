'use client';

import type L from 'leaflet';
import { Crosshair, Maximize2, Minus, Plus, Target } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { cx } from '@/components/ui/primitives';

function ControlButton({
  label,
  icon: Icon,
  onClick,
  disabled,
}: {
  label: string;
  icon: typeof Plus;
  onClick: () => void;
  disabled?: boolean;
}): React.JSX.Element {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className={cx(
        'grid size-8 place-items-center border-b border-line last:border-b-0',
        'text-text-3 transition-colors',
        disabled ? 'cursor-not-allowed opacity-35' : 'hover:bg-surface-3 hover:text-text',
      )}
    >
      <Icon size={14} strokeWidth={2} aria-hidden />
    </button>
  );
}

export function MapControls({ map }: { map: L.Map | null }): React.JSX.Element {
  const { fit, selected, requestDeviceLocation } = useOps();

  return (
    <div className="flex flex-col overflow-hidden rounded-md border border-line-2 bg-surface-2/95 backdrop-blur">
      <ControlButton label="Zoom in" icon={Plus} onClick={() => map?.zoomIn()} disabled={!map} />
      <ControlButton label="Zoom out" icon={Minus} onClick={() => map?.zoomOut()} disabled={!map} />
      <ControlButton label="Fit all incidents" icon={Maximize2} onClick={() => fit('ALL')} />
      <ControlButton
        label={selected ? `Centre on ${selected.id}` : 'No incident selected'}
        icon={Target}
        onClick={() => fit('SELECTED')}
        disabled={!selected?.location}
      />
      <ControlButton
        label="Use this device as the distance origin"
        icon={Crosshair}
        onClick={requestDeviceLocation}
      />
    </div>
  );
}
