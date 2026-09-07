import { NextResponse } from 'next/server';
import { liveStore } from '@/lib/data/store';

export const dynamic = 'force-dynamic';

/** GET /api/status — a health probe a rescuer device can poll cheaply. */
export async function GET(): Promise<NextResponse> {
  const now = Date.now();
  return NextResponse.json({
    service: 'DisasterMesh Rescue Command Center',
    ok: true,
    uptimeSeconds: Math.round((now - liveStore.startedAt) / 1000),
    reportsHeld: liveStore.emergencies.size,
    lastSyncedAt: liveStore.lastSyncedAt,
    gateways: Array.from(liveStore.gateways.entries()).map(([id, at]) => ({
      id,
      lastSeenAt: at,
      onlineWithin10Min: now - at < 10 * 60_000,
    })),
  });
}
