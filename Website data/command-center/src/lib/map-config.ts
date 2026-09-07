/**
 * Basemap configuration.
 *
 * Default: Esri's Dark Gray Canvas — a cartography designed specifically to
 * sit underneath operational data, which is exactly this use case. It needs
 * no key, no account and no signup, so `npm run dev` produces a working map
 * on any machine, which is what "runs on localhost first" actually requires.
 *
 * Labels ship as a separate reference layer so place names can be drawn ABOVE
 * the basemap but BELOW the incident overlays. Street names a commander
 * cannot read are useless; street names covering an SOS pin are worse.
 *
 * An operator with their own tile provider sets NEXT_PUBLIC_MAP_TILE_URL. No
 * key is ever hardcoded here — if a provider needs one, it belongs in that
 * environment variable at deploy time, never in this source file.
 */

const ESRI_BASE =
  'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}';

const ESRI_LABELS =
  'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}';

const ESRI_ATTRIBUTION =
  'Tiles &copy; <a href="https://www.esri.com/">Esri</a> — Esri, DeLorme, NAVTEQ';

/** Highest zoom this basemap actually publishes; beyond it, tiles upscale. */
export const MAP_MAX_NATIVE_ZOOM = 16;
export const MAP_MAX_ZOOM = 18;

export const MAP_TILE_URL = process.env.NEXT_PUBLIC_MAP_TILE_URL ?? ESRI_BASE;

export const MAP_LABEL_URL =
  process.env.NEXT_PUBLIC_MAP_TILE_URL === undefined
    ? ESRI_LABELS
    : (process.env.NEXT_PUBLIC_MAP_LABEL_URL ?? null);

export const MAP_ATTRIBUTION =
  process.env.NEXT_PUBLIC_MAP_TILE_ATTRIBUTION ?? ESRI_ATTRIBUTION;

/**
 * Whether the tiles are a LIGHT basemap needing to be graded to dark.
 * The default is already dark, so this is off unless a light override is
 * configured — inverting an already-dark style would ruin it.
 */
export const MAP_TILES_NEED_DARKENING = process.env.NEXT_PUBLIC_MAP_TILE_DARKEN === 'true';
