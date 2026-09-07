'use client';

import 'leaflet/dist/leaflet.css';
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Circle,
  MapContainer,
  Marker,
  Polyline,
  ScaleControl,
  TileLayer,
  Tooltip,
  useMap,
  useMapEvents,
} from 'react-leaflet';
import L from 'leaflet';
import { useOps } from '@/state/ops-store';
import type { Category, GeoPoint } from '@/lib/types';
import { CATEGORY_META, HAZARD_META, HOSPITAL_STATUS_META, ROLE_COLORS } from '@/lib/constants';
import { boundsOf, formatDistance } from '@/lib/geo';
import { DEMO_CENTER } from '@/lib/data/seed';
import {
  MAP_ATTRIBUTION,
  MAP_LABEL_URL,
  MAP_MAX_NATIVE_ZOOM,
  MAP_MAX_ZOOM,
  MAP_TILES_NEED_DARKENING,
  MAP_TILE_URL,
} from '@/lib/map-config';
import {
  emergencyIcon,
  groupIcon,
  hazardIcon,
  hospitalIcon,
  meshNodeIcon,
  originIcon,
  teamIcon,
} from './markers';
import type { ScoredEmergency } from '@/lib/recommendations';
import { MapLegend } from './MapLegend';
import { MapControls } from './MapControls';

/**
 * The basemap is key-less by default (see `lib/map-config`). If the tiles
 * cannot be reached the map keeps working — every operational overlay is
 * drawn by this application, not by the tile server — and the interface says
 * so rather than presenting an empty rectangle as if it were the world.
 */

/** Below this zoom, nearby pins are drawn as one group marker. */
const GROUPING_ZOOM = 15;
/** Screen-space cell size used to group markers, in pixels. */
const GROUP_CELL_PX = 58;

const toLatLng = (p: GeoPoint): [number, number] => [p.latitude, p.longitude];

/** What a screen reader hears when it lands on an incident pin. */
function describeMarker(e: ScoredEmergency): string {
  return (
    `${CATEGORY_META[e.category].label}, ${e.id}, priority ${e.priority}, ` +
    `score ${e.breakdown.score} of 100, ${e.sector ?? 'no sector'}. ${e.description}`
  );
}

// ---------------------------------------------------------------------------

/**
 * Hands the Leaflet instance up to the overlay controls.
 *
 * MapContainer's `ref` competes with react-leaflet's own initialisation under
 * React StrictMode's double mount, which surfaces as "Map container is being
 * reused by another instance". Reading the instance from inside the tree with
 * useMap() is the idiomatic route and side-steps it entirely.
 */
function MapInstanceBridge({ onReady }: { onReady: (map: L.Map) => void }): null {
  const map = useMap();

  useEffect(() => {
    onReady(map);
  }, [map, onReady]);

  /**
   * Keep Leaflet's idea of the viewport in step with the actual element.
   *
   * Leaflet caches the container size and does not watch it. Two things here
   * change that size without a window resize — the collapsible queue rail and
   * the incident detail panel — and both would otherwise leave the tiles
   * offset from the overlays. It also matters at mount: fitting bounds against
   * a container that has not been laid out yet is what produces a map opened
   * at maximum zoom on a 50 m scale bar.
   */
  useEffect(() => {
    const container = map.getContainer();
    map.invalidateSize({ animate: false });

    const observer = new ResizeObserver(() => map.invalidateSize({ animate: false }));
    observer.observe(container);
    return () => observer.disconnect();
  }, [map]);

  return null;
}

function FitController(): null {
  const map = useMap();
  const { fitToken, fitTarget, derived, selected, selectedClusterId, dataset } = useOps();
  const lastToken = useRef(0);
  const didInitialFit = useRef(false);

  // Open on the whole incident area rather than an arbitrary zoom level, so
  // the first thing a commander sees is the extent of the event.
  useEffect(() => {
    if (didInitialFit.current || !dataset || derived.emergencies.length === 0) return;
    const points = derived.emergencies.map((e) => e.location).filter(Boolean) as GeoPoint[];
    const bounds = boundsOf(points);
    if (!bounds) return;

    // Deferred a frame so the container has been laid out. Fitting against a
    // zero-size element silently yields maximum zoom.
    const frame = requestAnimationFrame(() => {
      map.invalidateSize({ animate: false });
      if (map.getSize().x === 0 || map.getSize().y === 0) return;
      didInitialFit.current = true;
      map.fitBounds(L.latLngBounds([bounds.south, bounds.west], [bounds.north, bounds.east]), {
        padding: [64, 64],
      });
    });
    return () => cancelAnimationFrame(frame);
  }, [map, dataset, derived.emergencies]);

  useEffect(() => {
    if (fitToken === lastToken.current || !dataset) return;
    lastToken.current = fitToken;

    if (fitTarget === 'SELECTED' && selected?.location) {
      map.flyTo(toLatLng(selected.location), Math.max(map.getZoom(), 16), { duration: 0.6 });
      return;
    }

    if (fitTarget === 'CLUSTER' && selectedClusterId) {
      const cluster = derived.clusters.find((c) => c.id === selectedClusterId);
      if (cluster) {
        const circle = L.circle(toLatLng(cluster.center), {
          radius: Math.max(cluster.radiusMeters, 120) * 1.6,
        });
        map.flyToBounds(circle.getBounds(), { duration: 0.6 });
        return;
      }
    }

    const points = [
      ...derived.emergencies.map((e) => e.location),
      ...dataset.teams.map((t) => t.location),
      ...dataset.hospitals.map((h) => h.location),
    ].filter(Boolean) as GeoPoint[];

    const bounds = boundsOf(points);
    if (bounds) {
      map.flyToBounds(
        L.latLngBounds([bounds.south, bounds.west], [bounds.north, bounds.east]),
        { padding: [56, 56], duration: 0.6 },
      );
    }
  }, [fitToken, fitTarget, map, derived, selected, selectedClusterId, dataset]);

  return null;
}

// ---------------------------------------------------------------------------

interface Group {
  key: string;
  center: GeoPoint;
  members: ScoredEmergency[];
}

/**
 * Emergency layer with screen-space grouping.
 *
 * Grouping happens in pixels rather than degrees so that a group is always
 * about the same size on screen regardless of latitude or zoom — the point is
 * legibility, and legibility is a screen property.
 */
function EmergencyLayer(): React.JSX.Element {
  const map = useMap();
  const { derived, layers, selected, selectEmergency, filters } = useOps();
  const [zoom, setZoom] = useState(() => map.getZoom());
  const [version, setVersion] = useState(0);

  useMapEvents({
    zoomend: () => setZoom(map.getZoom()),
    moveend: () => setVersion((v) => v + 1),
  });

  const visible = useMemo(
    () =>
      derived.emergencies.filter((e) => {
        if (!e.location) return false;
        if (e.category === 'SAFE') return layers.safe && filters.categories.has('SAFE');
        return layers.emergencies && filters.categories.has(e.category);
      }),
    [derived.emergencies, layers.emergencies, layers.safe, filters.categories],
  );

  const groups = useMemo<Group[]>(() => {
    // Grouping is computed in screen space, so it depends on the current
    // viewport — something React cannot observe. `version` ticks on every
    // moveend; touching it here makes that dependency explicit rather than
    // leaving a lint suppression to explain it.
    void version;

    if (zoom >= GROUPING_ZOOM) return [];
    const cells = new Map<string, ScoredEmergency[]>();

    for (const e of visible) {
      const point = map.latLngToLayerPoint(toLatLng(e.location!));
      const key = `${Math.floor(point.x / GROUP_CELL_PX)}:${Math.floor(point.y / GROUP_CELL_PX)}`;
      const bucket = cells.get(key);
      if (bucket) bucket.push(e);
      else cells.set(key, [e]);
    }

    return Array.from(cells.entries()).map(([key, members]) => ({
      key,
      members,
      center: {
        latitude: members.reduce((s, m) => s + m.location!.latitude, 0) / members.length,
        longitude: members.reduce((s, m) => s + m.location!.longitude, 0) / members.length,
      },
    }));
  }, [visible, zoom, map, version]);

  if (zoom >= GROUPING_ZOOM) {
    return (
      <>
        {visible.map((e) => (
          <Marker
            key={e.id}
            position={toLatLng(e.location!)}
            icon={emergencyIcon({
              category: e.category,
              selected: selected?.id === e.id,
              urgent: e.category === 'CRITICAL' && e.status === 'UNASSIGNED',
              label: describeMarker(e),
            })}
            zIndexOffset={e.category === 'CRITICAL' ? 900 : e.category === 'MEDICAL' ? 700 : 300}
            eventHandlers={{ click: () => selectEmergency(e.id) }}
            keyboard
          >
            <Tooltip direction="top" offset={[0, -34]} opacity={1}>
              <MarkerTooltip emergency={e} />
            </Tooltip>
          </Marker>
        ))}
      </>
    );
  }

  return (
    <>
      {groups.map((group) => {
        if (group.members.length === 1) {
          const e = group.members[0];
          return (
            <Marker
              key={e.id}
              position={toLatLng(e.location!)}
              icon={emergencyIcon({
                category: e.category,
                selected: selected?.id === e.id,
                urgent: e.category === 'CRITICAL' && e.status === 'UNASSIGNED',
                label: describeMarker(e),
              })}
              zIndexOffset={e.category === 'CRITICAL' ? 900 : 300}
              eventHandlers={{ click: () => selectEmergency(e.id) }}
            >
              <Tooltip direction="top" offset={[0, -34]} opacity={1}>
                <MarkerTooltip emergency={e} />
              </Tooltip>
            </Marker>
          );
        }

        const counts: Record<Category, number> = {
          CRITICAL: 0, MEDICAL: 0, WARNING: 0, SUPPLY: 0, SAFE: 0,
        };
        for (const m of group.members) counts[m.category] += 1;

        return (
          <Marker
            key={group.key}
            position={toLatLng(group.center)}
            icon={groupIcon(
              counts,
              group.members.length,
              `Group of ${group.members.length} reports: ` +
                (Object.keys(counts) as Category[])
                  .filter((c) => counts[c] > 0)
                  .map((c) => `${counts[c]} ${CATEGORY_META[c].label}`)
                  .join(', '),
            )}
            zIndexOffset={600}
            eventHandlers={{
              click: () => {
                const bounds = boundsOf(group.members.map((m) => m.location!));
                if (bounds) {
                  map.flyToBounds(
                    L.latLngBounds([bounds.south, bounds.west], [bounds.north, bounds.east]),
                    { padding: [80, 80], maxZoom: 17, duration: 0.5 },
                  );
                }
              },
            }}
          >
            <Tooltip direction="top" opacity={1}>
              <div className="space-y-1">
                <p className="text-[11.5px] font-semibold text-text">
                  {group.members.length} reports in this area
                </p>
                <ul className="space-y-0.5">
                  {(Object.keys(counts) as Category[])
                    .filter((c) => counts[c] > 0)
                    .map((c) => (
                      <li key={c} className="flex items-center gap-1.5 text-[10.5px]">
                        <span
                          className="size-1.5 rounded-full"
                          style={{ background: CATEGORY_META[c].mark }}
                        />
                        <span style={{ color: CATEGORY_META[c].text }}>{CATEGORY_META[c].label}</span>
                        <span className="num text-text-3">{counts[c]}</span>
                      </li>
                    ))}
                </ul>
                <p className="text-[10px] text-text-3">Click to zoom in</p>
              </div>
            </Tooltip>
          </Marker>
        );
      })}
    </>
  );
}

function MarkerTooltip({ emergency }: { emergency: ScoredEmergency }): React.JSX.Element {
  const meta = CATEGORY_META[emergency.category];
  return (
    <div className="max-w-[240px] space-y-1">
      <div className="flex items-center gap-1.5">
        <span className="size-1.5 rounded-full" style={{ background: meta.mark }} />
        <span className="num text-[11.5px] font-semibold text-text">{emergency.id}</span>
        <span className="text-[10px] font-bold" style={{ color: meta.text }}>
          {emergency.priority}
        </span>
        <span className="num text-[10px] text-text-3">{emergency.breakdown.score}/100</span>
      </div>
      <p className="text-[11px] leading-snug text-text-2">{emergency.description}</p>
      <p className="text-[10px] text-text-3">
        {emergency.sector ?? 'No sector'} · {formatDistance(emergency.distanceFromCommand)} from origin
      </p>
    </div>
  );
}

// ---------------------------------------------------------------------------

function OperationalLayers(): React.JSX.Element {
  const {
    dataset, derived, layers, route, selectedTeamId, selectTeam,
    selectedHospitalId, selectHospital, selectedClusterId, selectCluster,
  } = useOps();

  if (!dataset) return <></>;

  return (
    <>
      {/* --- high-impact areas ------------------------------------------- */}
      {layers.clusters &&
        derived.clusters.map((cluster) => (
          <Circle
            key={cluster.id}
            center={toLatLng(cluster.center)}
            radius={Math.max(cluster.radiusMeters, 90)}
            pathOptions={{
              color: cluster.countsByCategory.CRITICAL >= 3 ? '#ef4444' : '#f59e0b',
              weight: selectedClusterId === cluster.id ? 2 : 1.2,
              opacity: 0.75,
              fillColor: cluster.countsByCategory.CRITICAL >= 3 ? '#ef4444' : '#f59e0b',
              fillOpacity: selectedClusterId === cluster.id ? 0.12 : 0.06,
              dashArray: '5 4',
            }}
            eventHandlers={{ click: () => selectCluster(cluster.id) }}
          >
            <Tooltip direction="top" opacity={1} sticky>
              <div className="space-y-0.5">
                <p className="text-[11.5px] font-semibold text-text">High-impact area</p>
                <p className="text-[10.5px] text-text-2">
                  {cluster.peopleAffected} people across {cluster.emergencyIds.length} reports
                </p>
                <p className="text-[10px] text-text-3">
                  within {Math.round(cluster.radiusMeters)} m · {cluster.sector ?? 'unnamed sector'}
                </p>
              </div>
            </Tooltip>
          </Circle>
        ))}

      {/* --- hazards ------------------------------------------------------ */}
      {layers.hazards &&
        dataset.hazards.map((hazard) => (
          <Fragment key={hazard.id}>
            <Circle
              center={toLatLng(hazard.center)}
              radius={hazard.radiusMeters}
              pathOptions={{
                color: hazard.active ? ROLE_COLORS.hazard.mark : '#64748b',
                weight: 1.4,
                opacity: hazard.active ? 0.85 : 0.4,
                fillColor: hazard.active ? ROLE_COLORS.hazard.mark : '#64748b',
                fillOpacity: hazard.active ? 0.1 : 0.04,
                dashArray: '3 4',
              }}
            />
            <Marker
              position={toLatLng(hazard.center)}
              icon={hazardIcon(
                hazard.kind,
                hazard.active,
                `${HAZARD_META[hazard.kind].label}: ${hazard.label}. ${
                  hazard.active ? 'Active' : 'Cleared'
                }.`,
              )}
              zIndexOffset={400}
            >
              <Tooltip direction="top" opacity={1}>
                <div className="max-w-[220px] space-y-0.5">
                  <p className="text-[11.5px] font-semibold text-warning-text">{hazard.label}</p>
                  <p className="text-[10.5px] text-text-3">
                    Avoidance radius {hazard.radiusMeters} m ·{' '}
                    {hazard.active ? 'active' : 'cleared'}
                  </p>
                  <p className="text-[10px] text-text-3">Reported by {hazard.reportedBy}</p>
                </div>
              </Tooltip>
            </Marker>
          </Fragment>
        ))}

      {/* --- route -------------------------------------------------------- */}
      {layers.route && route && (
        <>
          {route.legs.map((leg, index) => (
            <Polyline
              key={`${route.id}-${index}`}
              positions={[toLatLng(leg.from), toLatLng(leg.to)]}
              pathOptions={{
                // A leg that crosses a hazard is drawn dashed and red. The map
                // must never render an unsafe corridor the same way as a clear one.
                color: leg.hazardIds.length > 0 ? '#ef4444' : '#22d3ee',
                weight: leg.hazardIds.length > 0 ? 3 : 3.4,
                opacity: 0.95,
                dashArray: leg.hazardIds.length > 0 ? '7 6' : undefined,
                lineCap: 'round',
              }}
            >
              <Tooltip sticky opacity={1}>
                <span className="text-[10.5px]">
                  {formatDistance(leg.distanceMeters)}
                  {leg.hazardIds.length > 0 ? ' · crosses a hazard zone' : ' · clear'}
                </span>
              </Tooltip>
            </Polyline>
          ))}
          {route.legs.slice(0, -1).map((leg, index) => (
            <Circle
              key={`wp-${route.id}-${index}`}
              center={toLatLng(leg.to)}
              radius={12}
              pathOptions={{ color: '#22d3ee', weight: 2, fillColor: '#0b1020', fillOpacity: 1 }}
            />
          ))}
        </>
      )}

      {/* --- teams -------------------------------------------------------- */}
      {layers.teams &&
        dataset.teams
          .filter((t) => t.location)
          .map((team) => (
            <Marker
              key={team.id}
              position={toLatLng(team.location!)}
              icon={teamIcon(
                team.status,
                selectedTeamId === team.id,
                `${team.name}, ${team.status.replace(/_/g, ' ').toLowerCase()}, ${
                  team.members.length
                } members`,
              )}
              zIndexOffset={800}
              eventHandlers={{ click: () => selectTeam(team.id) }}
            >
              <Tooltip direction="top" opacity={1}>
                <div className="space-y-0.5">
                  <p className="text-[11.5px] font-semibold text-rescuer-text">{team.name}</p>
                  <p className="text-[10.5px] text-text-2">
                    {team.status.replace(/_/g, ' ').toLowerCase()} · {team.members.length} members
                  </p>
                </div>
              </Tooltip>
            </Marker>
          ))}

      {/* --- hospitals ---------------------------------------------------- */}
      {layers.hospitals &&
        dataset.hospitals.map((hospital) => (
          <Marker
            key={hospital.id}
            position={toLatLng(hospital.location)}
            icon={hospitalIcon(
              HOSPITAL_STATUS_META[hospital.status].dot,
              selectedHospitalId === hospital.id,
              `${hospital.name}, ${HOSPITAL_STATUS_META[hospital.status].label}`,
            )}
            zIndexOffset={500}
            eventHandlers={{ click: () => selectHospital(hospital.id) }}
          >
            <Tooltip direction="top" opacity={1}>
              <div className="space-y-0.5">
                <p className="text-[11.5px] font-semibold text-text">{hospital.name}</p>
                <p className="text-[10.5px]" style={{ color: HOSPITAL_STATUS_META[hospital.status].text }}>
                  {HOSPITAL_STATUS_META[hospital.status].label}
                </p>
                <p className="text-[10px] text-text-3">
                  {hospital.emergencyBeds !== undefined
                    ? `${hospital.emergencyBeds} emergency beds free`
                    : 'Capacity not reported'}
                </p>
              </div>
            </Tooltip>
          </Marker>
        ))}

      {/* --- mesh nodes --------------------------------------------------- */}
      {layers.meshNodes &&
        dataset.meshNodes
          .filter((node) => node.location)
          .map((node) => (
            <Marker
              key={node.id}
              position={toLatLng(node.location!)}
              icon={meshNodeIcon(
                node.kind,
                node.state === 'CONNECTED',
                `Mesh node ${node.id}, ${node.kind.toLowerCase()}, ${node.state
                  .replace('_', ' ')
                  .toLowerCase()}`,
              )}
              zIndexOffset={100}
            >
              <Tooltip direction="top" opacity={1}>
                <span className="num text-[10.5px]">
                  {node.id} · {node.kind.toLowerCase()} · {node.state.replace('_', ' ').toLowerCase()}
                </span>
              </Tooltip>
            </Marker>
          ))}

      {/* --- distance origin ---------------------------------------------- */}
      {derived.origin.point && (
        <Marker
          position={toLatLng(derived.origin.point)}
          icon={originIcon(
            derived.origin.kind === 'DEVICE',
            `Distance origin: ${derived.origin.label}`,
          )}
          zIndexOffset={950}
        >
          <Tooltip direction="top" opacity={1}>
            <div className="space-y-0.5">
              <p className="text-[11.5px] font-semibold text-system-text">{derived.origin.label}</p>
              <p className="text-[10px] text-text-3">All distances are measured from here</p>
            </div>
          </Tooltip>
        </Marker>
      )}
    </>
  );
}

// ---------------------------------------------------------------------------

export default function MapCanvas(): React.JSX.Element {
  const { dataset, derived } = useOps();
  const [tilesFailed, setTilesFailed] = useState(false);
  const [map, setMap] = useState<L.Map | null>(null);
  const tileErrors = useRef(0);

  const center = useMemo<[number, number]>(() => {
    const first = derived.emergencies.find((e) => e.location)?.location;
    return first ? toLatLng(first) : toLatLng(DEMO_CENTER);
  }, [derived.emergencies]);

  const onTileError = useCallback(() => {
    tileErrors.current += 1;
    if (tileErrors.current > 6) setTilesFailed(true);
  }, []);

  if (!dataset) return <></>;

  return (
    <div className="relative h-full w-full">
      <MapContainer
        center={center}
        zoom={14}
        minZoom={9}
        maxZoom={MAP_MAX_ZOOM}
        zoomControl={false}
        attributionControl
        preferCanvas
        /* Leaflet's tile fade sets an inline opacity and drives it from
           requestAnimationFrame; when the frame loop is throttled the tiles
           stay invisible. A basemap that sometimes fails to appear is not a
           trade worth making for a 200 ms fade. */
        fadeAnimation={false}
        className="h-full w-full"
      >
        <TileLayer
          url={MAP_TILE_URL}
          attribution={MAP_ATTRIBUTION}
          maxZoom={MAP_MAX_ZOOM}
          maxNativeZoom={MAP_MAX_NATIVE_ZOOM}
          className={MAP_TILES_NEED_DARKENING ? 'dm-tiles-dark' : 'dm-tiles-plain'}
          eventHandlers={{ tileerror: onTileError }}
        />
        {MAP_LABEL_URL && (
          <TileLayer
            url={MAP_LABEL_URL}
            maxZoom={MAP_MAX_ZOOM}
            maxNativeZoom={MAP_MAX_NATIVE_ZOOM}
            /* Labels sit in the tile pane, below every overlay pane, so they
               can never intercept a click meant for a marker. */
            className="dm-tiles-labels"
            opacity={0.6}
          />
        )}
        <ScaleControl position="bottomleft" imperial={false} />
        <MapInstanceBridge onReady={setMap} />
        <FitController />
        <OperationalLayers />
        <EmergencyLayer />
      </MapContainer>

      {/* Overlays sit outside the Leaflet pane stack so they always sit on top
          and keep normal DOM focus order for keyboard users. */}
      <div className="pointer-events-none absolute inset-0 z-[500] p-3">
        <div className="pointer-events-auto absolute top-3 right-3">
          <MapControls map={map} />
        </div>
        <div className="pointer-events-auto absolute bottom-3 right-3">
          <MapLegend />
        </div>
      </div>

      {tilesFailed && (
        <div
          role="status"
          className="pointer-events-none absolute top-3 left-1/2 z-[500] -translate-x-1/2 rounded-sm border border-warning/40 bg-surface-2/95 px-3 py-1.5 text-[11px] text-warning-text backdrop-blur"
        >
          Basemap tiles unavailable — incident geometry, distances and routing are unaffected.
        </div>
      )}
    </div>
  );
}
