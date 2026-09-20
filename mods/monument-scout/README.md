# Monument Scout

A client-only Fabric mod for Minecraft 26.2. Automatically records recognizable ocean monuments and maintains **one Xaero waypoint per monument**, updating its name, symbol, and color as you survey and clear it.

## Install

Install `monument-scout-<suite-version>.jar` from the suite's `build/suite` directory with Fabric API and the matching Fabric Loader/Java versions listed in the suite installation guide.

Xaero's Minimap is optional (compiled against 26.5.1 for 26.2); add the matching World Map for full-map display. Slime Scout is not required. Select the **Monument Scout** waypoint set, or enable displaying all sets to see Slime Scout and Monument Scout together.

## Waypoint states

| Color / symbol | Meaning |
| --- | --- |
| Gray / M? | Monument discovered; a complete sponge survey is pending |
| Aqua / M | Wet or dry sponge blocks remain; the name includes their count |
| Yellow / M- | No sponge blocks remain; elders are still observed or their count is unknown |
| Green / OK | A complete sponge survey found zero sponges and a settled nearby entity survey found zero living elder guardians |

Names include `[elders: N]` or `[elders: ?]`. **No three-kill counter or death history is required.** Monuments with no sponge rooms, previously looted monuments, and monuments whose elders were already removed can qualify on their first survey. Elder counts are not capped at three, so extra spawned elders prevent a cleared result too.

Sponges or elders appearing again change the waypoint back. Both wet and dry **placed blocks** count, including sponges you place while draining the monument. Item drops, containers, and player inventories are not included.

## Surveying

Discovery checks the vanilla monument's distinctive central roof crown near the player, using loaded blocks without a world seed or server installation. This crown is symmetric across monument rotations. The method recognizes normal monuments; a matching player-built replica can also match. Heavily modified roofs and custom-generation layouts may require a manual marker.

Each sponge survey checks the entire 58-by-58 footprint from Y=39 through Y=63, with a budget of 4,096 block reads per tick. All footprint chunks must be loaded throughout the scan. An unload invalidates an unfinished scan, rather than counting missing data as air.

For a zero-elder result, stay within 16 blocks of the monument center on both horizontal axes, between Y=30 and Y=80, with the surrounding chunks loaded. The mod waits for five seconds of uninterrupted zero elder observations before accepting zero. Complete block scans repeat roughly every six seconds for a single nearby monument, so allow about eight seconds after arriving for the initial cleared result. Multiple monuments share the scanning budget.

The elder survey includes the footprint plus a 16-block margin on all sides and vertically, allowing for nearby wandering elders. Positive elder sightings revoke a cleared status immediately. Killing an elder is not required; the check uses currently living entities.

**Multiplayer limitation:** the server controls which entities and block states your client receives. “Cleared (observed)” means no sponges in the surveyed volume and no elders reported in the survey area; it is not an authoritative server-wide existence check. Reduced entity-tracking ranges, hidden/filtered block data, or elders moved outside that area can affect the result. The mod does not force-load chunks or inspect structures elsewhere in the world.

Previously completed surveys persist. `[last survey]` marks stored observations that are no longer fresh, including after reconnecting or leaving the survey area. An unknown elder count is never treated as zero.

## Commands

| Command | Action |
| --- | --- |
| `/monumentscout` | Status and command help |
| `/monumentscout here` | Show the nearest recorded monument within 128 blocks |
| `/monumentscout scan` | Queue nearby monuments for a new survey |
| `/monumentscout markers` | Hide/show this mod's waypoints for the current game session |
| `/monumentscout mark <centerX> <centerZ>` | Record a monument manually, using its footprint anchor |

For a manual marker, the footprint extends from `centerX - 29` to `centerX + 28`, and likewise for Z. A normal generated monument's anchor is on a chunk boundary; the central roof's four lanterns are at offsets (-2,-2), (-2,+1), (+1,-2), (+1,+1) from it. Use this to locate the exact anchor; an arbitrary nearby position will survey the wrong footprint. Re-marking the same anchor does not create another waypoint.

Only the Overworld is tracked. Discovery searches up to eight chunks around your current chunk; known monuments are resurveyed within 128 horizontal blocks. Unknown or ruined monuments are not discovered merely because elders or sponges are nearby.

## Data and compatibility

Records are saved under `config/monument-scout`, separated by world/save path or server address and dimension, with atomic file replacement where supported. Saves run about every five seconds and on world changes/disconnect/shutdown. Corrupt files are preserved and tracking for that context is disabled with an error in `latest.log`. An abrupt crash may lose changes since the last save.

Different logical worlds behind the same server address/dimension share a data bucket. Renaming a singleplayer save or changing a server address creates a different bucket. Xaero waypoints are temporary and regenerated from this mod's records; normal Xaero visibility settings apply.

## Build and checks

From the repository root: `gradlew.bat :monument-scout:build` (or `./gradlew :monument-scout:build`). Root `build` packages both mods at the centralized suite version.

Automated tests cover discovery signatures, negative coordinates, scan budgets, unloaded chunks, zero-elder first visits, sponge/elder reappearance, unique records, world isolation, and persistence. The code compiles against the actual 26.2 and Xaero artifacts. In-game visual behavior has not been manually verified.

Suggested game checks in a disposable creative world:

1. Visit an intact monument, load the surrounding chunks, and wait near its center. Check the waypoint and `/monumentscout here`.
2. Remove all wet/dry sponge blocks inside its footprint. Verify yellow while elders remain, then green when the current elder count reaches zero.
3. Visit an already-cleared monument without killing anything. Verify it reaches green.
4. Add a sponge or summon an elder inside the survey area; verify the status reverts.
5. Unload part of the footprint mid-scan and confirm it does not publish a false zero. Reconnect and verify saved markers carry the last-survey label until surveyed again.
