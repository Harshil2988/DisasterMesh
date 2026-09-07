import { NextResponse } from 'next/server';
import { buildLiveDataset } from '@/lib/data/store';

export const dynamic = 'force-dynamic';

/** GET /api/dataset — the full operating picture, as LIVE mode consumes it. */
export async function GET(): Promise<NextResponse> {
  return NextResponse.json(buildLiveDataset());
}
