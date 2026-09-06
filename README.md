# DisasterMesh — Milestone 1

Offline phone-to-phone messaging with **Google Nearby Connections**.
No internet, no server, no Firebase.

**Milestone 1 scope:** Phone A connects to Phone B and sends the text `HELLO`.
Multi-hop mesh (A → B → C → D) comes later.

## Requirements

- Two **physical** Android phones (Android 7.0 / API 24 or newer).
- Google Play Services installed on both (Nearby Connections is part of it).
- Bluetooth **and** Wi-Fi switched on. Neither phone needs to be on a network —
  Wi-Fi just needs to be *on*.
- Location switched on (Android 12 and older use it for scanning).

## Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Structure

```
MainActivity          asks for permissions, hosts the screen
 └─ MeshScreen        Compose UI, knows nothing about Nearby
     └─ MeshViewModel survives rotation, exposes actions
         └─ NearbyConnectionManager   ← all Nearby Connections code
```

Strategy is `P2P_CLUSTER`: each phone can hold several connections at once,
which is what the mesh will need later.
