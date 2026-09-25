# Bedrock Scout

Bedrock Scout is a client-only Fabric mod for Minecraft 26.2 that searches loaded Nether-ceiling chunks for places suitable for a wither-killing platform.

A candidate has all nine bedrock blocks in a horizontal 3x3 square at Y=123 through Y=127 and no bedrock anywhere beneath that footprint in the ceiling band. Air, netherrack, and other removable blocks underneath are allowed. The mod does not require the platform to be at Y=127 or require any particular blocks above it. If more than one height qualifies at the same X/Z center, it records the lowest one.

Candidates are useful leads, not a guarantee that a particular wither design will work. Inspect the surrounding blocks and test the build safely before spawning a wither.

With Xaero's Minimap installed, each result becomes a permanent purple `B3` waypoint in the shared **Minecraft Scouts** set. Its name is `Nether Roof 3x3 (Y=height)`, and the waypoint includes the exact build height. Xaero is optional; detection and saved records work without it.

| Command | Result |
| --- | --- |
| `/bedrockscout` | Show saved-pattern and scan-queue status |
| `/bedrockscout here` | Show the nearest saved center within 512 blocks |
| `/bedrockscout scan` | Rescan all currently loaded chunks within 16 chunks |
| `/bedrockscout markers` | Hide or show this mod's Xaero waypoints |

The scanner processes at most one chunk each client tick. It revisits chunk boundaries as neighboring chunks load and periodically queues loaded chunks within 16 chunks of the player, so a first scan can take about a minute at maximum view distance. Only client-loaded blocks can be inspected.

Records are stored per world/server and dimension under `config/bedrock-scout`. Unit tests cover the roof-pattern rules, chunk-boundary safety, persistence, and lowest-height replacement.
