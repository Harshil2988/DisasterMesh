'use client';

import dynamic from 'next/dynamic';
import { LoadingState } from '@/components/ui/primitives';

/**
 * Leaflet touches `window` at import time, so the canvas is loaded on the
 * client only. Everything above it renders normally on the server.
 */
export const MapView = dynamic(() => import('./MapCanvas'), {
  ssr: false,
  loading: () => (
    <div className="grid h-full place-items-center bg-void">
      <LoadingState label="Loading operational map" />
    </div>
  ),
});
