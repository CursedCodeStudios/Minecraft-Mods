# Monument Scout in Fry Utilities

A client-side feature in Fry Utilities for Minecraft 26.2. It automatically records recognizable ocean monuments and maintains **one Xaero waypoint per monument**, updating its name, symbol, and color as you survey and clear it.

## Install

Install `fry-utilities-<suite-version>.jar` from the suite's `build/suite` directory with Fabric API and the matching Fabric Loader/Java versions listed in the suite installation guide. Remove the old standalone Monument Scout JAR when upgrading.

Xaero's Minimap is optional (compiled against 26.5.1 for 26.2); add the matching World Map for full-map display. Slime Scout is not required. Select the shared **Minecraft Scouts** waypoint set to see all installed scout mods together in the current dimension.

## Waypoint states

| Color / symbol | Meaning |
| --- | --- |
| Gray / M? | Monument discovered; a complete sponge survey is pending |
| Aqua / M | Wet or dry sponge blocks remain; the name includes their count |
| Yellow / M- | Sponge-cleared milestone reached permanently; waiting for a zero-elder survey |
| Green / OK | Permanent completion milestone: sponges cleared, followed by zero living elders observed |

Before completion, names include `[elders: N]` or `[elders: ?]`. **No three-kill counter or death history is required.** Monuments with no sponge rooms, previously looted monuments, and monuments whose elders were already removed can qualify on their first survey. Elder counts are not capped at three, so extra spawned elders prevent a cleared result too.

**Milestones never go backwards.** Once yellow, new sponges cannot return the waypoint to aqua. It can only advance to green when a settled survey finds zero elders. Green stays OK permanently, even if sponges or elders appear again, chunks unload, or the game restarts. These markers represent completed progress, not a live claim that the monument is still empty. Both wet and dry **placed blocks** count, including sponges you place while draining the monument. Item drops, containers, and player inventories are not included.

## Surveying

Discovery checks the vanilla monument's distinctive central roof crown near the player, using loaded blocks without a world seed or server installation. This crown is symmetric across monument rotations. The method recognizes normal monuments; a matching player-built replica can also match. Heavily modified roofs and custom-generation layouts may require a manual marker.

Each sponge survey checks the entire 58-by-58 footprint from Y=39 through Y=63, with a budget of 4,096 block reads per tick. All footprint chunks must be loaded throughout the scan. An unload invalidates an unfinished scan, rather than counting missing data as air.

For a zero-elder result, stay within 16 blocks of the monument center on both horizontal axes, between Y=30 and Y=80, with the surrounding chunks loaded. The mod waits for five seconds of uninterrupted zero elder observations before accepting zero. Complete block scans repeat roughly every six seconds for a single nearby monument, so allow about eight seconds after arriving for the initial cleared result. Multiple monuments share the scanning budget.

The elder survey includes the footprint plus a 16-block margin on all sides and vertically, allowing for nearby wandering elders. Positive elder sightings update observations but never revoke an earned milestone. Killing an elder is not required; the check uses currently living entities.

Unloading chunks or leaving survey range never lowers the saved elder or sponge counts. Partial elder sightings can only raise the saved count; a lower count (including zero) needs five seconds of the same count with complete footprint/margin coverage while near the center, then a completed block survey. Unknown elder results retain the saved count. Sponge counts change only after a complete, uninterrupted loaded-footprint scan. Unloads in the elder margin also reset the settling period. Genuine reductions still update when these checks pass.

**Multiplayer limitation:** the server controls which entities and block states your client receives. Earning a milestone relies on the surveyed block volume and client-reported elders; it is not an authoritative server-wide existence check. Reduced entity-tracking ranges, hidden/filtered block data, or elders moved outside that area can affect the result. The mod does not force-load chunks or inspect structures elsewhere in the world.

Progress milestones persist without a stale-survey suffix. Before any milestone is earned, `[last survey]` marks observations that are no longer fresh. Unknown elder counts never advance progress. Existing version 1.2.0 saves migrate their currently saved yellow/green statuses into permanent milestones; states that already regressed before upgrading cannot be reconstructed.

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

Different logical worlds behind the same server address/dimension share a data bucket. Renaming a singleplayer save or changing a server address creates a different bucket. Xaero waypoints are permanent, saved through Xaero, and reused when you reconnect. Leaving a dimension does not delete them. Normal Xaero visibility settings apply.

## Build and checks

From the repository root: `gradlew.bat :fry-utilities:build` (or `./gradlew :fry-utilities:build`). Root `build` packages all suite mods at the centralized suite version.

Automated tests cover discovery signatures, negative coordinates, scan budgets, unloaded chunks, zero-elder first visits, sponge/elder reappearance, unique records, world isolation, and persistence. The code compiles against the actual 26.2 and Xaero artifacts. In-game visual behavior has not been manually verified.

Suggested game checks in a disposable creative world:

1. Visit an intact monument, load the surrounding chunks, and wait near its center. Check the waypoint and `/monumentscout here`.
2. Remove all wet/dry sponge blocks inside its footprint. Verify yellow while elders remain, then green when the current elder count reaches zero.
3. Visit an already-cleared monument without killing anything. Verify it reaches green.
4. After earning yellow or green, add a sponge or summon an elder; verify the marker never regresses. Reconnect and check that the earned milestone persists.
5. Unload part of the footprint mid-scan and confirm it does not publish a false zero. Reconnect and verify saved markers carry the last-survey label until surveyed again; completed milestones stay permanent.
