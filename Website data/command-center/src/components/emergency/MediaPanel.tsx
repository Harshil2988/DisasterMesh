'use client';

import { useState } from 'react';
import { Headphones, Image as ImageIcon, Pause, Play } from 'lucide-react';
import { cx } from '@/components/ui/primitives';

/**
 * Audio and evidence.
 *
 * The demonstration dataset ships no media files, and this panel says so
 * rather than rendering a dead player or a broken thumbnail. When a real
 * deployment attaches a URL, the same component plays it.
 */
export function AudioPlayer({
  clipId,
  src,
}: {
  clipId: string;
  src?: string;
}): React.JSX.Element {
  const [playing, setPlaying] = useState(false);

  if (!src) {
    return (
      <div className="flex items-center gap-2.5 rounded-sm border border-line bg-surface-3 px-2.5 py-2">
        <span className="grid size-7 shrink-0 place-items-center rounded-sm border border-line-2 bg-surface-4">
          <Headphones size={13} className="text-system-text" aria-hidden />
        </span>
        <div className="min-w-0">
          <p className="num text-[11.5px] text-text-2">{clipId}</p>
          <p className="text-[10.5px] text-text-3">
            Audio recorded in the field. The clip itself is not bundled with the demonstration
            dataset, so playback is unavailable here.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2.5 rounded-sm border border-line bg-surface-3 px-2.5 py-2">
      <button
        type="button"
        onClick={() => setPlaying((p) => !p)}
        aria-label={playing ? 'Pause audio report' : 'Play audio report'}
        className="grid size-7 shrink-0 place-items-center rounded-sm border border-system/40 bg-system/15 text-system-text transition-colors hover:bg-system/25"
      >
        {playing ? <Pause size={13} /> : <Play size={13} />}
      </button>
      <div className="min-w-0 flex-1">
        <p className="num text-[11.5px] text-text-2">{clipId}</p>
        <audio src={src} controls className="mt-1 h-7 w-full" />
      </div>
    </div>
  );
}

export function EvidenceViewer({ ids }: { ids: string[] }): React.JSX.Element {
  if (ids.length === 0) {
    return <p className="text-[11px] text-text-3">No evidence attached to this report.</p>;
  }

  return (
    <div className="grid grid-cols-3 gap-1.5">
      {ids.map((id) => (
        <figure
          key={id}
          className={cx(
            'flex aspect-4/3 flex-col items-center justify-center gap-1 rounded-sm border border-line bg-surface-3 px-1 text-center',
          )}
        >
          <ImageIcon size={15} className="text-line-2" aria-hidden />
          <figcaption className="num text-[9.5px] leading-tight text-text-3">{id}</figcaption>
          <span className="text-[9px] text-text-3">not bundled</span>
        </figure>
      ))}
    </div>
  );
}
