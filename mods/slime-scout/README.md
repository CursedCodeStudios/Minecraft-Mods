# Slime Scout

A client-only Fabric mod for **Minecraft Java 26.2**. Walk, explore, or AFK and accumulate slime sightings without knowing the world seed. No server installation is needed.

## Install

1. Use Minecraft **26.2**, Java **25**, and Fabric Loader **0.19.5 or newer**.
2. Put `slime-scout-<suite-version>.jar` from the repository's `build/suite` or `mods/slime-scout/build/libs` in your instance's `mods` folder. Do not install the `-sources.jar`. The version is defined in root `gradle.properties`.
3. Install [Fabric API for 26.2](https://modrinth.com/mod/fabric-api).
4. For map markers, install [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) for 26.2 (integration compiled against **26.5.1**) and its required dependencies. Add [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) for full-map display. World Map alone does not provide the waypoint API this mod uses.

Tracking starts automatically. The mod only sees entities loaded on your client, including slimes behind walls; it does not inspect unloaded chunks or query the server seed. Magma cubes are excluded.

## Markers

- **Green `S?`: potential slime chunk.** A slime was first observed below Y=40 in the Overworld.
- **Yellow `S?`: slime sighting.** Other sightings, including surface/swamp sightings and movement into a neighboring chunk.

There is exactly one owned waypoint per chunk, at its center. The waypoint name includes `[visits: N]`: the number of chunk loads during which at least one slime was observed. More slimes, splitting, entity reloads, or movement while that chunk remains loaded do not increment the counter. After the chunk unloads and reloads, finding a slime increments the same waypoint. Counts persist across reconnects and restarts. They appear in the **Minecraft Scouts** Xaero waypoint set shared by all three scouts. The set is selected when first created. If markers are missing, select that set in Xaero's waypoint menu, or enable displaying all sets. Normal Xaero waypoint visibility settings still apply.

These are waypoint markers, not filled 16×16 chunk overlays. Their data is owned by Slime Scout and restored from its own save files; they are permanent Xaero waypoints, and their names/counts may refresh, but deleting an owned waypoint permanently dismisses its chunk. Dismissed chunks are not recreated by new sightings, marker toggles, world changes, or restarts. Moving a marker to another Xaero set is not a deletion. The integration adds no markers to your other waypoint sets. Saved Xaero markers remain when you leave a dimension or disconnect. Visiting a dimension updates its saved markers without duplicating them. Map browsing to another dimension does not proactively load new scout sightings.

## Commands

| Command | Action |
| --- | --- |
| `/slimescout` | Show counts and tracking/integration status |
| `/slimescout here` | Show evidence for the chunk you occupy |
| `/slimescout toggle` | Pause/resume detection |
| `/slimescout markers` | Hide/show owned Xaero markers |
| `/slimescout dismiss` | Dismiss the recorded chunk you occupy |
| `/slimescout restore` | Explicitly restore a dismissed chunk you occupy |
| `/slimescout save` | Save immediately |

The two toggles last until Minecraft closes. Recorded sightings persist across restarts.

## Using it to search for slime chunks

Build or explore spawning space below Y=40 in the Overworld, allow slimes to spawn, and inspect the green markers. Use F3+G to inspect chunk boundaries. Repeat observations in an isolated chunk give better evidence than a single sighting in connected caves. The mod does not modify spawn rates, load extra chunks, or automatically excavate terrain.

**A sighting is not proof of a slime chunk.** Slimes can move, be transported, be summoned, or spawn through mechanics unrelated to the usual slime-chunk rule. The client receives an entity when it becomes visible to the network; that is not necessarily its spawn location. Entity observations are kept as internal evidence, but the displayed visit count increments only once per chunk load with a sighting; it does not represent unique natural spawns. A known slime crossing a chunk boundary creates a yellow sighting and never upgrades that destination from movement alone. No sightings does not prove a chunk is unsuitable.

## Deleted markers and slime splits

Deleted Xaero waypoints are detected while Slime Scout is running and saved as per-chunk dismissal flags. Deleting the shared Minecraft Scouts waypoint set dismisses the Slime Scout markers in it too. Hiding markers, changing dimensions, or moving a waypoint to another set does not dismiss it. A dismissed chunk remains suppressed until you explicitly run `/slimescout restore` while standing in that chunk. `/slimescout dismiss` also works without Xaero.

Versions before 1.2.2 did not save deletion history. If an old deleted marker has already been recreated, delete it once more after upgrading, or use `/slimescout dismiss` in that chunk. Existing sightings and visit counts migrate unchanged.

New slime observations wait five client ticks (about a quarter second). The split filter matches half-size children to nearby dying parents within a short time window, including children that appear across a chunk boundary. It checks parents before children and tolerates briefly reordered death/spawn packets. Recognized children are ignored entirely: they do not create candidates, yellow sightings, or visit increments. Their UUIDs are saved per world/dimension so unloading or reconnecting does not turn a known child into new evidence. Their own offspring are filtered when their later deaths are observed too.

Minecraft's client entity packets do not identify a slime's parent. This filter therefore relies on an observed death and matching size/position/timing. It cannot identify splits that happened entirely outside your client's observation, and an unrelated natural half-size slime appearing immediately beside a death can be conservatively excluded. Ordinary small and medium slimes elsewhere still count.

## Saved data

JSON files live in your instance's `config/slime-scout` directory. Data is separated by singleplayer save path or multiplayer server address, plus dimension. File names are SHA-256 hashes of that identity. Records contain chunk coordinates, first/last observation timestamps, minimum observed Y, encounter count, slime-positive chunk-load count, and the candidate flag. Version 1.0.0 records migrate automatically: each previously recorded chunk starts with one historical visit because old per-entity counts cannot reconstruct past chunk loads. New visits then accumulate normally.

Data saves every 100 client ticks (about five seconds), on world changes/disconnect, and on client shutdown. Writes replace files atomically where supported. A corrupt or unsupported file disables recording for that context and is retained for recovery; errors appear in `logs/latest.log`. An abrupt crash can lose observations since the last save.

Servers with several logical worlds behind the same address and dimension are treated as one context. A server reset at the same address also reuses that history. Renaming/moving a singleplayer save or changing a server address starts a different history. Back up and remove obsolete JSON files while the game is closed to reset records.

## Build

From the repository root, with a JDK 25 installed and `JAVA_HOME` set:

```powershell
.\gradlew.bat :slime-scout:build
```

On macOS/Linux: `./gradlew :slime-scout:build`. Use the root `build` task to build and package the entire suite. Gradle downloads the pinned Minecraft, Fabric, and compile-only Xaero dependencies. Xaero is not bundled into the output JAR. See the [suite guide](../../README.md) for versioning and new modules.

On this workspace, a local JDK and Gradle were downloaded to ignored `.tools` for the initial build. They are not needed in a normal JDK-equipped checkout.

## Validation

The project compiles against the actual Minecraft 26.2 and Xaero Minimap 26.5.1 artifacts. Automated tests cover persistence, evidence upgrades, context isolation, corrupt-file preservation, repeated observations, movement, and entity unload/reset. In-game visual behavior has not been manually verified.

Suggested in-game check in a disposable creative world:

1. Run without Xaero and summon a slime below Y=40. Check `/slimescout here` and reconnect to verify persistence. Summoning tests detection, not real slime-chunk identification.
2. Repeat with Xaero Minimap 26.5.1 and matching World Map/dependencies. Check the green `S?` marker on both maps.
3. Summon a slime above Y=40 in a different chunk; check the yellow marker. Move a tracked slime across a boundary below Y=40 and check the destination stays yellow.
4. Summon a magma cube and confirm it is ignored. Change dimensions and worlds to check separation. Toggle markers and tracking independently.

## References

- [Fabric's Minecraft 26.2 development notes](https://www.fabricmc.net/2026/06/15/262.html)
- [Fabric's 26.2 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- Xaero integration uses the published mod's waypoint/session classes. Compatibility changes are caught and logged so tracking can continue when an optional integration fails.
