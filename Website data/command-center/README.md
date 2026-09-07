# DisasterMesh — Rescue Command Center

The web console at the end of the DisasterMesh pipeline. It receives what
rescuer devices collected from the civilian mesh and turns it into ranked,
assignable, routable rescue work.

```
civilian phones ── mesh relays ── rescuer device ── offline store
                                                        │
                                            internet, when available
                                                        │
                                          RESCUE COMMAND CENTER  ← this app
                                                        │
                          map · prioritisation · assignment · routing
```

Only the last two stages need a network. Everything before them works with no
internet at all, which is the whole point of the system — and the reason data
here can legitimately be several minutes old.

---

## Run it

```bash
npm install
npm run dev
```

Then open <http://localhost:3000>. No API keys, no accounts, no `.env` file
required — the basemap is key-less and the console boots into a labelled
demonstration scenario.

```bash
npm run build && npm start   # production
npm run lint                 # eslint, zero warnings expected
npx tsc --noEmit             # type check
```

Requires Node 20 or newer (built and tested on Node 26).

---

## The two data modes

The **DEMO / LIVE** switch sits in the command bar and is never ambiguous:
every page also carries a `DEMO DATA` or `LIVE DATA` badge, so a screenshot of
any single screen still declares what it is showing.

| | DEMO | LIVE |
|---|---|---|
| Source | Authored scenario, `src/lib/data/seed.ts` | Whatever rescuer gateways have POSTed to this server |
| Determinism | Identical every run; only the clock moves | Real data, real gaps |
| Teams / hospitals / hazards | Present | Empty until an operator adds them |
| On failure | n/a | Says "connection unavailable" and shows **nothing** |

LIVE never falls back to demo data. A commander seeing invented incidents
while believing the console is live is the worst failure this system could
have, so an empty live dataset renders as "waiting for gateway connection".

---

## Connecting the real rescuer app

The ingest endpoint already accepts, byte for byte, the JSON body that
`uplink/ExternalUplink.kt` in the Android app builds today. Point a handset's
uplink endpoint at this server and it works with no adapter on either side:

```
POST /api/emergency
Content-Type: application/json

{
  "reportId":     "…",          // mesh message id — the duplicate authority
  "senderNodeId": "NODE-4821",
  "category":     "CRITICAL",   // CRITICAL | MEDICAL | WARNING | SUPPLY | SAFE
  "priority":     "P0",
  "description":  "…",
  "timestamp":    1788799152500,
  "latitude":     12.9716,      // optional
  "longitude":    77.5946,      // optional
  "peopleAffected": 2,          // optional
  "hops":         3             // optional
}
```

Also accepts a **JSON array**, so a gateway that has been offline can flush its
whole queue in one request. An optional `x-gateway-id` header attributes the
upload to a gateway in the synchronisation log.

Status codes match what the handset's retry queue expects:

| Code | Meaning to the device |
|---|---|
| `202` | Delivered. Stop retrying. |
| `400` | Permanently rejected — nothing in the payload parsed. Do not retry. |
| `5xx` | Retryable. |

Try it against a running dev server:

```bash
curl -X POST http://localhost:3000/api/emergency \
  -H 'Content-Type: application/json' \
  -H 'x-gateway-id: GW-01' \
  -d '{"reportId":"test-1","senderNodeId":"NODE-4821","category":"CRITICAL","priority":"P0","description":"Trapped, two people","timestamp":'"$(($(date +%s)*1000))"',"latitude":12.9716,"longitude":77.5946,"peopleAffected":2,"hops":3}'
```

Switch the console to **LIVE** and the report is there.

Other routes: `GET /api/status` (cheap health probe for a device),
`GET /api/emergency` (everything received), `GET /api/dataset` (the full
operating picture, exactly as LIVE mode reads it).

**Storage is in-memory and intentionally empty at boot.** A deployment swaps
`src/lib/data/store.ts` for a database; nothing above it changes.

---

## What the console computes, and how

Three things are calculated rather than reported, and all three show their work.

**Rescue priority score** (`src/lib/priority.ts`) — a weighted sum of six
stated factors: severity, vulnerability, people affected (log-scaled), waiting
time, current status, and whether a position exists. Every score ships the
factor table that produced it, one click away in the UI. No machine learning
is involved and the interface never implies otherwise. Two identical inputs
always score identically.

**Hazard-avoiding corridor routing** (`src/lib/routing.ts`) — Dijkstra over a
visibility graph built from hazard-zone boundaries. If it returns a path, every
leg genuinely clears every active hazard. If it cannot, it says so and names
the hazards in the way; if an endpoint is *inside* a zone it says that
specifically, because the remedy is different. There is no road network behind
this and the UI never calls the result a driving route.

**Geographic clustering** (`src/lib/clustering.ts`) — single-link agglomerative
clustering at a 400 m linkage distance, so a chain of nearby reports reads as
one incident area. Reports without coordinates are excluded rather than placed
somewhere plausible.

The **decision-support engine** (`src/lib/recommendations.ts`) runs eight
deterministic rules over that output. Every recommendation must supply a
`because[]` list — a rule that cannot state its evidence does not get to make
a recommendation. Replacing the rules with a model later means implementing the
same function signature; the UI renders whatever justification it is handed.

---

## Honesty rules the code actually enforces

These are the constraints that shaped the build, not aspirations:

- **A missing value renders as a stated absence**, never as `0`, `—`, or a
  last-known reading dressed up as current. Team Echo shows "battery not
  reported"; Eastfield District Hospital shows "capacity unknown, not zero".
- **`0, 0` is rejected as a coordinate** — it is an uninitialised variable, not
  a position in the Gulf of Guinea. Same rule as `GeoPoint.of` on the handset.
- **Hop count is shown; the node path is not.** The Android relay copies the
  message envelope without appending a route, so the console says "relayed
  through 3 hops" and refuses to draw `PHONE A → PHONE B → PHONE C`.
- **Mesh nodes are laid out by role, not geography**, because the mesh does not
  report node positions and a map-like layout would be fiction.
- **Distance is never faked.** If either endpoint lacks a fix, the readout says
  which one is missing.
- **An invariant in the seed** downgrades any report claiming `ASSIGNED` or
  `IN_PROGRESS` without a team actually bound to it.

---

## Layout

```
src/
  app/                  11 pages + 3 API routes
  components/
    shell/              command bar, nav, alerts, demo controls
    ui/                 badges, buttons, panels, dialog, states
    map/                Leaflet canvas, SVG markers, legend, controls
    emergency/          queue card, filters, detail panel, provenance, media
    ops/                recommendations, routing, teams, hospitals, pipeline
    charts/             shared chart frame + dark tooltip
  lib/
    types.ts            the domain model
    geo.ts              haversine, formatting, segment/circle geometry
    priority.ts         the scoring function
    routing.ts          the corridor router
    clustering.ts       geographic clustering
    recommendations.ts  the eight rules
    constants.ts        every colour, label and status in one place
    data/               DataProvider, DemoDataProvider, ApiDataProvider, seed, live store
  state/ops-store.tsx   one reducer; every mutation writes a timeline event
```

No component constructs domain data. Screens consume whatever a `DataProvider`
hands them, which is what makes the DEMO → LIVE swap a one-line change.

---

## Design notes

Dark operations console. Surfaces `#020617 → #1a2135`; text ramp `#f8fafc /
#cbd5e1 / #94a3b8`, and `#64748b` is **never** used for text (3.8:1 — borders
only). Inter for the interface, JetBrains Mono with tabular numerals for every
figure, so numbers do not reflow as they tick.

Each emergency category carries two tokens: a **mark** colour for map pins and
chart series (3:1 target) and a lighter **text** tint for labels (4.5:1). The
proposed `#8B5CF6` measured 4.39:1 on the panel surface and is used as a mark
only, with `#a78bfa` for its text. Category is also encoded in the marker
**shape** — bar-and-dot, cross, triangle, carton, tick — so the map does not
depend on hue alone.

Motion is restrained on purpose: a console that flickers is a console nobody
trusts. Exactly one thing pulses — an unassigned P0.

---

## Limitations

- **Not a road router.** Corridor routing avoids hazard geometry; it does not
  know about streets, one-ways or bridges. The UI says this wherever a route
  appears.
- **ETAs assume a flat 26 km/h ground speed.** Labelled as an estimate
  everywhere, with no traffic model behind it.
- **Live storage is in-memory.** A server restart clears it. Swap
  `lib/data/store.ts` for a database before any real deployment.
- **No authentication.** The ingest endpoint is open. Put it behind a gateway,
  mTLS or a shared secret before exposing it beyond localhost.
- **Demo media is not bundled.** Audio and photo reports render a labelled
  placeholder that says so, rather than a broken frame.
- **Basemap tiles need internet.** If they fail, the console says so and keeps
  working — all operational geometry is drawn by this app, not the tile server.
- **Teams, hospitals and hazards are command-centre records**, not mesh
  traffic. In LIVE mode they are empty until an operator adds them.

---

## Independence from the Android app

This is a standalone Next.js application under `Website data/command-center`.
It shares no build, no dependency and no source with the DisasterMesh handset
app. Nothing in the Android project was modified. The only coupling is the
wire format of `POST /api/emergency`, which was written to match the existing
`InternetUplink` payload rather than asking the handset to change.
