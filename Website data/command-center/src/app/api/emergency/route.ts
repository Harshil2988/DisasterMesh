import { NextResponse } from 'next/server';
import { buildLiveDataset, ingest, liveStore, parseUplink, type UplinkPayload } from '@/lib/data/store';

export const dynamic = 'force-dynamic';

/**
 * POST /api/emergency
 *
 * The endpoint a DisasterMesh rescuer handset uploads to. The accepted body
 * is exactly what `InternetUplink.send()` in the Android app already builds,
 * so pointing a phone at this URL requires no change on either side:
 *
 *   { reportId, senderNodeId, category, priority, description,
 *     timestamp, latitude?, longitude?, peopleAffected?, hops? }
 *
 * Accepts a single object or an array, so a gateway that has been offline can
 * flush its whole queue in one request.
 *
 * Responses follow what the handset's queue does with them: 2xx marks the
 * report delivered, 4xx is a permanent rejection it will not retry, and 5xx
 * is retryable. Returning the wrong class here would make a gateway either
 * drop an emergency or spin on it forever.
 */
export async function POST(request: Request): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: 'Body must be JSON' }, { status: 400 });
  }

  const items: UplinkPayload[] = Array.isArray(body)
    ? (body as UplinkPayload[])
    : [body as UplinkPayload];

  if (items.length === 0) {
    return NextResponse.json({ error: 'No reports in request' }, { status: 400 });
  }
  if (items.length > 500) {
    return NextResponse.json({ error: 'Batch limited to 500 reports' }, { status: 400 });
  }

  const gatewayId = request.headers.get('x-gateway-id');
  const accepted: string[] = [];
  const rejected: { index: number; error: string }[] = [];
  let created = 0;

  items.forEach((item, index) => {
    const parsed = parseUplink(item);
    if ('error' in parsed) {
      rejected.push({ index, error: parsed.error });
      return;
    }
    const result = ingest(parsed.report, gatewayId);
    if (result.created) created += 1;
    accepted.push(parsed.report.id);
  });

  // Nothing usable arrived: a permanent rejection, so the gateway stops
  // retrying a payload that will never parse.
  if (accepted.length === 0) {
    return NextResponse.json({ error: 'No valid reports', rejected }, { status: 400 });
  }

  return NextResponse.json(
    { accepted: accepted.length, created, updated: accepted.length - created, rejected },
    { status: 202 },
  );
}

/** GET /api/emergency — every report this console has received. */
export async function GET(): Promise<NextResponse> {
  return NextResponse.json({
    count: liveStore.emergencies.size,
    lastSyncedAt: liveStore.lastSyncedAt,
    emergencies: buildLiveDataset().emergencies,
  });
}
