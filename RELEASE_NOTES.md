## Concurrent passengers and longer journeys

- Adds server-controlled passenger reservations. Independent networks can run together, and riders following the same directed route use four-block spacing. Opposing traffic and conflicting junction paths wait at the intake.
- Raises the defaults to eight reserved journeys, 8,192 registered parts per world and 512 parts per journey. Administrators can adjust bounded limits with the server options described in the player guide.
- Prepares and temporarily retains only the registered route corridor and station landings, allowing journeys beyond the boarding player's initial view without changing view distance. Travel timeouts scale with distance.
- Protects occupied landings and stopped passengers during safe return. Queue cancellation, abort and disconnect cleanup belong to the affected passenger.
- Adds a quiet waiting cue and improves long-route lookup and station scanning. Existing tube art, item IDs, recipes, station names, ownership and saved recovery records are preserved.

## Compatibility and development status

Requires Java 25. Declares Hytale `>=0.7.3 <0.8.0`, compiled against the pinned official 0.7.3 SDK. A real single-player 512-part journey, full-size arrival, reverse-trip abort and normal reload were observed on Hytale 0.7.4 with inventory and route records preserved. Native server loading and persistence were also checked on 0.7.3.

This is an **Alpha development release**. Real simultaneous two-client travel, blocked-original-landing and active-disconnect cases, survival ingredient deduction, and final human comfort/visual/listening review remain pending. An exact 0.7.3 client run is also pending. Use a test world and keep a complete matching world/player/JAR backup.

Older small-registry builds cannot load a save expanded beyond 128 registered parts. Restore the matching backup when downgrading. Connected routes still require the same builder; other players may ride them. Travel remains within one world.

[Player guide and server limits](https://jkswoods.github.io/slipstream-transit/hytale0.7/configuration/)
