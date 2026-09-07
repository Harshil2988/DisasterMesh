# DisasterMesh

Offline phone-to-phone messaging over **Google Nearby Connections**.
No internet, no server, no cloud, no Firebase.

## The model: every device is an equal node

There is no Phone A, Phone B or Phone C. There is no master, server, client or
fixed relay. Every installation runs identical code and behaves as one
**mesh node** with a persistent id such as `NODE-4821`.

Any node can advertise, discover, accept connections, open connections, send,
receive, relay, raise an SOS, join, and leave.

```
        NODE-4821
        /       \
  NODE-9137 -- NODE-2846
        \       /
        NODE-7712
```

A message from any node reaches nodes it cannot see directly, because every node
in between forwards it. Supports multiple nearby mesh nodes, subject to device
and radio limitations.

## How a message travels

1. The sender wraps text in a `MeshMessage`: unique `messageId`, `senderId`,
   payload, `ttl`, `hops`, and — for an SOS only — the sender's coordinates.
2. It goes to every directly connected node.
3. Each receiver drops it if it is its own, or if `SeenMessages` has the id
   already. Otherwise it displays it, decrements TTL, and forwards it to every
   connected node **except** the one it came from.
4. At TTL 0 the message stops.

Relaying never rewrites the envelope: `relayed()` is a `copy()` that changes only
`ttl` and `hops`, so the original sender's id and location survive every hop.

## Requirements

- Android 7.0 (API 24) or newer, with Google Play Services.
- Bluetooth **and** Wi-Fi on. Neither needs to join a network — Wi-Fi just needs
  to be *on*.
- Location on, and granted as **Precise** (Nearby refuses to scan otherwise).

## Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Structure

```
MainActivity              permissions, hosts the UI
 └─ DisasterMeshApp       shell + bottom nav (Home / Messages / Mesh / Info)
     └─ MeshViewModel     UI state and actions
         └─ MeshNodeHolder          one shared mesh node per process
             └─ NearbyConnectionManager   all Nearby + relay logic
                 ├─ MeshMessage / SeenMessages   envelope, duplicate suppression
                 ├─ MeshNotifier                 local notifications
                 └─ SosLocationProvider          one-shot GPS for SOS only
MeshForegroundService     keeps the node alive when the UI is closed
```

Strategy is `P2P_CLUSTER` — the only one allowing a device to advertise and
discover simultaneously while holding several connections at once.

## Testing multi-hop

To prove relaying rather than direct delivery, keep two nodes out of radio range
of each other with a third between them. The distant node should still receive
the message, with **hops ≥ 1** shown on the card. Which physical handset plays
which part is irrelevant — the roles are positional, not built into the app.
