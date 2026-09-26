# End City Scout in Fry Utilities

A client-side feature in Fry Utilities for Minecraft 26.2. It records End cities and ships, checks the **ship's elytra item frame**, and reports **living shulkers observed** nearby. It integrates optionally with Xaero's Minimap and World Map.

## Install and use

Install `fry-utilities-<suite-version>.jar` from `build/suite` with Fabric API. Remove the old standalone End City Scout JAR when upgrading. Use the Minecraft, Java, and Fabric versions in the root installation guide. Select the shared **Minecraft Scouts** Xaero waypoint set to see the merged scouts together in the current dimension.

Tracking runs automatically in the End. Visit the city's ground-floor entrance to recognize its base. Ships can be recognized independently, including ships whose chests, brewing stand, dragon head, item frame, and shulkers have been removed.

Only **one center waypoint per city** is displayed. Nearby branch-house detections within 192 blocks on each horizontal axis are grouped under the lowest entrance (the main base), with manual anchors taking priority. Equal-height candidates favor the most central detection. Grouping is recalculated as entrances are discovered, including records saved by older versions, and obsolete tower markers are removed. Grouping uses proximity rather than server structure metadata, so unusually close separate cities can share a marker.

| Name example | Symbol | Color |
| --- | --- | --- |
| `End City (Elytra) (6 Shulkers)` | `EC-E6` | Blue |
| `End City (6 Shulkers)` | `EC-6` | Yellow |
| `End City (? Shulkers)` | `EC-?` | Yellow |

Names never mention ships. `(Elytra)` and the `E` in the symbol appear only when a framed elytra has been observed and has not subsequently been observed missing. Unknown shulker counts use `?`; confirmed zero uses `0`. Names, symbols, and colors use the latest saved observations even when the city is unloaded or after reconnecting, without historical-status suffixes. These are observations, not guaranteed current totals or permanent completion milestones. Existing permanent markers with the older name format are reused and renamed automatically.

## Ships and elytra

Recognition uses the vanilla ship's obsidian keel rows and purpur blocks in all four rotations. Finding a ship does **not** imply it still has elytra. The mod then searches the precise item-frame display cell from the ship template and checks `ItemFrame.getItem()` for `Items.ELYTRA`. Dropped items, inventories, chests, and adjacent frames do not count.

A framed elytra adds `(Elytra)` and switches the marker to blue as soon as the client receives it. To establish that the elytra is missing, stay within 24 blocks horizontally and vertically of the display spot for five uninterrupted seconds, with surrounding chunks loaded and no elytra in that frame. This handles both empty frames and removed frames. From farther away, missing frame data stays unknown or retains the last observation. A settled missing result removes `(Elytra)` and switches the marker to yellow. `/endcityscout here` gives the ship's elytra-room coordinates.

Ships are associated with the nearest city center within 192 blocks on each horizontal axis. Ships never create a separate waypoint. A ship found before its city is still recorded and checked for elytra; `/endcityscout here` can show its coordinates until its information is incorporated into the center marker. Nearby overlapping cities can make association ambiguous; this is a proximity survey, not server structure metadata.

The waypoint never includes ship status. Without a positive elytra observation it reads, for example, `End City (6 Shulkers)`. Absence of `(Elytra)` does not prove the city has no ship; an unseen or damaged ship may remain undetected.

## Shulkers and survey coverage

Only currently living shulker entities received by the client count. No kill history is needed. Positive counts are **observed counts**, not guaranteed totals; entities outside the server's tracking range may not be visible. Shulkers are assigned to the nearest recorded city within its 192-block square search radius, including nearby ship shulkers. Moving a shulker into that area counts too.

Zero is deliberately conservative. It requires a completed block sweep, all search-area chunks loaded, and every discovered purpur-containing section in that area (plus a 16-block margin) to be within 64 blocks of the player on all three axes. These conditions must hold with no living shulkers observed for five uninterrupted seconds. Unloads, leaving coverage, or a positive sighting reset that timer. Large or tall cities often cannot fit inside that observation range and **will remain unknown/last survey rather than claiming zero**. Fly through them to find remaining shulkers; this client-only mod cannot conclusively certify an arbitrary large city empty.

Keep approximately 15 chunks of terrain around the entrance loaded for a complete ship-search sweep; server view-distance limits can prevent this even with a higher client setting. Discovery and positive sightings still work with partial coverage. Each tick scans at most one candidate 16×16×16 section, plus its signature checks, and skips up to 256 irrelevant section/chunk entries. It never requests unloaded chunks. Sweep duration depends on loaded terrain and the number of buildings. Completed coverage expires after one minute or a chunk unload and is refreshed by repeated sweeps.

**Multiplayer:** all results depend on blocks and entities supplied by the server. Reduced entity-tracking ranges or filtered data can affect even settled observations. A count of zero means zero observed under the checks above, not an authoritative server-wide guarantee. Recognition uses vanilla blocks and can also match a player-built replica. Heavily modified entrances can be marked manually.

## Commands

| Command | Action |
| --- | --- |
| `/endcityscout` | Record counts, Xaero status, and command help |
| `/endcityscout here` | Nearest city's results and ship display coordinates; nearby ship if no city is known |
| `/endcityscout scan` | Start a new discovery/coverage sweep |
| `/endcityscout markers` | Hide/show this mod's markers for the session |
| `/endcityscout mark <centerX> <centerZ>` | Record a damaged/custom city's entrance manually |

Use the middle of the ground-floor entrance building for a manual anchor. The survey extends 192 blocks in each horizontal direction and covers the world's height. Re-marking within 16 blocks of an existing anchor reuses its record. Manual marking does not itself prove a ship or zero shulkers.

## Persistence and checks

Records are stored under `config/end-city-scout`, separated by singleplayer save path or server address and dimension. They save approximately every five seconds and on context changes/shutdown using atomic replacement where supported. Corrupt files are retained and that context's tracking is disabled with an error in `latest.log`. Logical worlds sharing the same server address/dimension share records. A crash can lose changes since the last save.

Ships remain recorded after looting; saved elytra and shulker observations remain in the compact label until refreshed. Xaero markers are permanent, saved through Xaero, and reused across reconnects. Leaving a dimension does not delete them; obsolete branch-center markers are still removed when the main center is recognized. Without Xaero, commands and discovery chat messages remain available.

Build from the repository root with `gradlew.bat :fry-utilities:build`, or build the complete suite with `gradlew.bat build`. Tests cover signatures against block fixtures extracted from the actual 26.2 templates, all rotations, negative coordinates, exact frame positions, interrupted absence timers, missing/unloaded coverage, large-city zero prevention, nearest-city association, persistence, and corrupt data.

In-game visual behavior has not been manually verified. Suggested checks in a disposable creative world:

1. Approach an intact city and ship from each rotation; check markers and `/endcityscout here`.
2. Enter the ship room and remove its elytra, then its frame. After five seconds, verify `(Elytra)` and `E` disappear and the marker turns yellow. Replace a framed elytra and verify the blue marker with `(Elytra)` and `E` returns.
3. Place an elytra frame beside the display cell or drop an elytra nearby; verify neither is treated as the ship's display elytra.
4. Kill a shulker and verify the observed count decreases. Summon another and verify it increases.
5. Leave/unload the city or reconnect; verify the latest saved results retain their compact label and symbol. A partially loaded or large city must not falsely report zero shulkers.
6. Approach branch towers before the main base, then load the base. Verify only the base's center marker remains, including after a restart with old tower records. Ships must never add a separate waypoint.
