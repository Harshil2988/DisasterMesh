import L from 'leaflet';
import type { Category, HazardKind, TeamStatus } from '@/lib/types';
import { CATEGORY_META, HAZARD_META, ROLE_COLORS, TEAM_STATUS_META } from '@/lib/constants';

/**
 * Map markers, drawn as SVG.
 *
 * Two rules drive every shape here:
 *
 *  1. No emoji. A pin has to stay legible at 24 px on a dark raster basemap
 *     and print the same way on every platform, which rules emoji out.
 *  2. Category is encoded in the GLYPH as well as the colour — a bar-and-dot
 *     for critical, a cross for medical, a triangle for warning, a carton for
 *     supply, a tick for safe. Someone who cannot separate the red pin from
 *     the orange one can still separate the shapes.
 */

/**
 * Escapes text destined for a divIcon's HTML string.
 *
 * Leaflet writes `html` with innerHTML, and report descriptions arrive from
 * the network via POST /api/emergency — so anything interpolated into a
 * marker has to be escaped or the map becomes an injection surface.
 */
function esc(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

const GLYPHS: Record<Category, string> = {
  // "!" — bar and dot
  CRITICAL: '<rect x="12.7" y="7.5" width="2.6" height="8" rx="1.3"/><circle cx="14" cy="19" r="1.7"/>',
  // medical cross
  MEDICAL: '<path d="M11.9 7.4h4.2v4.5h4.5v4.2h-4.5v4.5h-4.2v-4.5H7.4v-4.2h4.5z"/>',
  // hazard triangle
  WARNING:
    '<path d="M14 6.6 21.6 19.9H6.4z" fill="none" stroke-width="2.2" stroke-linejoin="round"/><rect x="13" y="11" width="2" height="4.4" rx="1"/><circle cx="14" cy="17.4" r="1.1"/>',
  // supply carton
  SUPPLY:
    '<path d="M6.6 10.4 14 7l7.4 3.4v7.2L14 21l-7.4-3.4z" fill="none" stroke-width="2"stroke-linejoin="round"/><path d="M6.6 10.4 14 13.8l7.4-3.4M14 13.8V21" fill="none" stroke-width="1.7"/>',
  // tick
  SAFE: '<path d="M8.2 14.3 12.2 18.2 19.8 10.2" fill="none" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>',
};

/** A teardrop pin, 28 × 34, anchored at its point. */
const PIN_PATH =
  'M14 1.6c-6.8 0-12.3 5.5-12.3 12.3 0 8.4 12.3 18.5 12.3 18.5s12.3-10.1 12.3-18.5C26.3 7.1 20.8 1.6 14 1.6z';

interface EmergencyMarkerOptions {
  category: Category;
  selected: boolean;
  /** Unassigned life-threatening reports are the only thing that pulses. */
  urgent: boolean;
  dimmed?: boolean;
  /**
   * Accessible name. Leaflet's `alt` option applies to image icons only, so a
   * divIcon carries its own role and label or it reaches assistive technology
   * as an unlabelled div.
   */
  label: string;
}

export function emergencyIcon({
  category,
  selected,
  urgent,
  dimmed,
  label,
}: EmergencyMarkerOptions): L.DivIcon {
  const meta = CATEGORY_META[category];
  const scale = selected ? 1.18 : 1;
  const width = 28 * scale;
  const height = 34 * scale;

  const halo = urgent
    ? `<span class="anim-ring" style="position:absolute;left:50%;top:${
        13 * scale
      }px;transform:translate(-50%,-50%);width:${28 * scale}px;height:${
        28 * scale
      }px;border-radius:9999px;border:2px solid ${meta.mark};pointer-events:none"></span>`
    : '';

  const svg = `
    <svg width="${width}" height="${height}" viewBox="0 0 28 34" xmlns="http://www.w3.org/2000/svg">
      <path d="${PIN_PATH}" fill="#0b1020" stroke="${meta.mark}" stroke-width="${
        selected ? 3 : 2.2
      }"/>
      <g fill="${meta.mark}" stroke="${meta.mark}">${GLYPHS[category]}</g>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}" style="position:relative;width:${width}px;height:${height}px;opacity:${
      dimmed ? 0.35 : 1
    };filter:drop-shadow(0 3px 6px rgb(0 0 0 / .6))${
      selected ? ' drop-shadow(0 0 8px ' + meta.mark + '99)' : ''
    }">${halo}${svg}</div>`,
    iconSize: [width, height],
    iconAnchor: [width / 2, height],
    popupAnchor: [0, -height + 6],
  });
}

/** Rescue teams: a chevron, so they never read as an incident. */
export function teamIcon(status: TeamStatus, selected: boolean, label: string): L.DivIcon {
  const colour = TEAM_STATUS_META[status].dot;
  const ring = ROLE_COLORS.rescuer.mark;
  const size = selected ? 30 : 26;

  const svg = `
    <svg width="${size}" height="${size}" viewBox="0 0 28 28" xmlns="http://www.w3.org/2000/svg">
      <rect x="2" y="2" width="24" height="24" rx="6" fill="#0b1020" stroke="${ring}" stroke-width="${
        selected ? 2.8 : 2
      }" transform="rotate(45 14 14)"/>
      <path d="M14 8.4 19.4 19.2 14 16.4 8.6 19.2z" fill="${colour}"/>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}" style="filter:drop-shadow(0 3px 6px rgb(0 0 0 / .6))">${svg}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
}

/** Hospitals: a square plate with a cross. Deliberately neutral in colour. */
export function hospitalIcon(statusColour: string, selected: boolean, label: string): L.DivIcon {
  const size = selected ? 28 : 24;
  const svg = `
    <svg width="${size}" height="${size}" viewBox="0 0 26 26" xmlns="http://www.w3.org/2000/svg">
      <rect x="1.5" y="1.5" width="23" height="23" rx="4" fill="#0b1020" stroke="#cbd5e1" stroke-width="${
        selected ? 2.6 : 1.9
      }"/>
      <path d="M11.1 6.4h3.8v4.7h4.7v3.8h-4.7v4.7h-3.8v-4.7H6.4v-3.8h4.7z" fill="#e2e8f0"/>
      <circle cx="21" cy="5" r="3.4" fill="${statusColour}" stroke="#0b1020" stroke-width="1.4"/>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}" style="filter:drop-shadow(0 3px 6px rgb(0 0 0 / .6))">${svg}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
}

const HAZARD_GLYPH: Record<HazardKind, string> = {
  BLOCKED_ROAD: '<path d="M6 9h12v3H6zM6 14h12v3H6z" />',
  FLOOD: '<path d="M4 10c2-1.6 4-1.6 6 0s4 1.6 6 0 4-1.6 6 0M4 15c2-1.6 4-1.6 6 0s4 1.6 6 0 4-1.6 6 0" fill="none" stroke-width="2" stroke-linecap="round"/>',
  FIRE: '<path d="M12 3c3 4 6 5 6 9a6 6 0 1 1-12 0c0-2 1-3 2-4 .4 1.4 1.2 2 2 2 0-3 1-5 2-7z"/>',
  COLLAPSE: '<path d="M3 18h18v3H3zM6 18 9 8l4 5 3-6 2 11" fill="none" stroke-width="2" stroke-linejoin="round"/>',
  UNSAFE_AREA: '<path d="M12 3 22 20H2z" fill="none" stroke-width="2.2" stroke-linejoin="round"/><rect x="11" y="9" width="2" height="5.5" rx="1"/><circle cx="12" cy="17" r="1.2"/>',
};

export function hazardIcon(kind: HazardKind, active: boolean, label?: string): L.DivIcon {
  const colour = active ? ROLE_COLORS.hazard.mark : '#64748b';
  const svg = `
    <svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <circle cx="12" cy="12" r="11" fill="#0b1020" stroke="${colour}" stroke-width="2"/>
      <g fill="${colour}" stroke="${colour}" transform="translate(0 0) scale(0.72) translate(4.6 4.6)">${HAZARD_GLYPH[kind]}</g>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label ?? HAZARD_META[kind].label)}" title="${esc(
      label ?? HAZARD_META[kind].label,
    )}" style="filter:drop-shadow(0 2px 5px rgb(0 0 0 / .6));opacity:${active ? 1 : 0.5}">${svg}</div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });
}

/** Grouped markers at low zoom. Segments show the category mix at a glance. */
export function groupIcon(
  counts: Record<Category, number>,
  total: number,
  label: string,
): L.DivIcon {
  const size = total >= 25 ? 48 : total >= 10 ? 42 : 36;
  const radius = size / 2 - 3;
  const circumference = 2 * Math.PI * radius;

  let offset = 0;
  const segments = (Object.keys(counts) as Category[])
    .filter((c) => counts[c] > 0)
    .map((category) => {
      const fraction = counts[category] / total;
      const dash = fraction * circumference;
      const seg = `<circle cx="${size / 2}" cy="${size / 2}" r="${radius}" fill="none"
        stroke="${CATEGORY_META[category].mark}" stroke-width="3.2"
        stroke-dasharray="${dash - 1.5} ${circumference - dash + 1.5}"
        stroke-dashoffset="${-offset}" transform="rotate(-90 ${size / 2} ${size / 2})"/>`;
      offset += dash;
      return seg;
    })
    .join('');

  const svg = `
    <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${size / 2}" cy="${size / 2}" r="${radius - 2}" fill="#0b1020" stroke="#2b3650" stroke-width="1"/>
      ${segments}
      <text x="${size / 2}" y="${size / 2 + 4.5}" text-anchor="middle"
        font-family="ui-monospace, monospace" font-size="${total > 99 ? 12 : 13}"
        font-weight="700" fill="#f8fafc">${total}</text>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}" style="filter:drop-shadow(0 4px 10px rgb(0 0 0 / .65))">${svg}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

/** The distance origin — command post or this device. */
export function originIcon(isDevice: boolean, label: string): L.DivIcon {
  const colour = ROLE_COLORS.system.mark;
  const svg = `
    <svg width="26" height="26" viewBox="0 0 26 26" xmlns="http://www.w3.org/2000/svg">
      <circle cx="13" cy="13" r="10" fill="none" stroke="${colour}" stroke-width="1.4" stroke-dasharray="3 2.5"/>
      <circle cx="13" cy="13" r="4.2" fill="${colour}"/>
      ${isDevice ? '' : '<rect x="11.6" y="2.5" width="2.8" height="5" rx="1" fill="' + colour + '"/>'}
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}" style="filter:drop-shadow(0 2px 6px rgb(0 0 0 / .6))">${svg}</div>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  });
}

/** Civilian and relay nodes on the mesh layer. Small, and never dominant. */
export function meshNodeIcon(
  kind: 'CIVILIAN' | 'RESCUER' | 'GATEWAY',
  connected: boolean,
  label: string,
): L.DivIcon {
  const colour =
    kind === 'GATEWAY' ? ROLE_COLORS.system.mark : kind === 'RESCUER' ? ROLE_COLORS.rescuer.mark : '#64748b';
  const size = kind === 'CIVILIAN' ? 10 : 14;
  const svg = `
    <svg width="${size}" height="${size}" viewBox="0 0 14 14" xmlns="http://www.w3.org/2000/svg">
      <circle cx="7" cy="7" r="5.4" fill="${connected ? colour : '#0b1020'}" stroke="${colour}" stroke-width="1.6"/>
    </svg>`;

  return L.divIcon({
    className: 'dm-marker',
    html: `<div role="img" aria-label="${esc(label)}" title="${esc(label)}">${svg}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}
