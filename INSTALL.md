# Installing the suite

Use Minecraft Java 26.2 with Java 25, Fabric Loader 0.19.5 or newer, and Fabric API 0.161.0+26.2 or a compatible newer release. The release manifest records exact requirements for this build.

Extract the suite ZIP, then copy the desired JARs from its `mods` directory into your Minecraft instance's `mods` directory. Replace older versions of the same mods; do not keep both installed. The JSON manifest is informational and need not be installed. The suite contains Slime Scout and Monument Scout. Both are client-only and independently installable; no server installation is needed.

Both mods optionally integrate with Xaero's Minimap (compiled against 26.5.1 for Minecraft 26.2). Add a compatible Xaero's World Map for full-map display, along with any dependencies those mods require. Xaero mods and Fabric API are not included in this ZIP.

Slime Scout starts automatically, creates one waypoint per recorded chunk, and adds a `[visits: N]` counter. Use `/slimescout` for status and commands. Existing Slime Scout saves remain compatible.

Monument Scout creates one waypoint per recognizable ocean monument: gray while surveying, aqua when sponges remain, yellow when no sponges remain, and green when no sponges and zero elders are observed. No recorded kills are required. Stay near the monument center with surrounding chunks loaded to survey zero elders. On multiplayer this reflects client-reported entities, not an authoritative server query. Use /monumentscout for commands; select the Monument Scout waypoint set or show all Xaero sets.
Monument progress is permanent: sponge-cleared markers can only advance to OK, and OK markers never revert, including after restarts.
Slime Scout remembers deleted waypoint chunks and filters recognized slime split children. Use /slimescout restore in a dismissed chunk only if you explicitly want its waypoint back. Deletions made before 1.2.2 must be repeated once because earlier versions did not save them.
