/**
 * Time formatting.
 *
 * All of it takes an explicit `now` so that a render is a pure function of
 * its inputs — server and client agree, and React never hydrates a
 * "3 min ago" that the server rendered as "2 min ago".
 */

export function formatClock(ts: number | null | undefined): string {
  if (!ts) return '—';
  return new Date(ts).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function formatClockSeconds(ts: number | null | undefined): string {
  if (!ts) return '—';
  return new Date(ts).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

export function formatDateTime(ts: number | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  return `${d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })} ${formatClock(ts)}`;
}

/** Compact elapsed time: "41 min", "2 h 05", "3 d". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || !Number.isFinite(ms)) return '—';
  const secs = Math.max(0, Math.floor(ms / 1000));
  if (secs < 60) return `${secs} s`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins} min`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h ${String(mins % 60).padStart(2, '0')}`;
  return `${Math.floor(hours / 24)} d`;
}

export function formatAgo(ts: number | null | undefined, now: number): string {
  if (!ts) return 'never';
  const delta = now - ts;
  if (delta < 45_000) return 'just now';
  return `${formatDuration(delta)} ago`;
}

/** Human sentence for a waiting time, used in priority explanations. */
export function formatWaiting(ts: number | null | undefined, now: number): string {
  if (!ts) return 'unknown';
  return formatDuration(now - ts);
}

export function pluralise(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`;
}

/** Rounds to at most `dp` decimals and strips a trailing ".0". */
export function trimNumber(value: number, dp = 1): string {
  const s = value.toFixed(dp);
  return s.endsWith('.0') ? s.slice(0, -2) : s;
}
