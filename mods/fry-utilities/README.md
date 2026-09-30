# Fry Utilities

Fry Utilities is a multi-purpose client-only Fabric mod for Minecraft 26.2. It combines villager trading tools, storage-container JSON exports, Nether Elytra course learning, drowned equipment highlights, No Strip protection, Slime Scout, Monument Scout, and End City Scout in one JAR.

Version 1.8.5 compares both learned Elytra course directions and displays the quicker track's geometry in either direction. Version 1.8.4 limits the visible line to the next 20 blocks along the route. Version 1.8.3 improves course visibility and saves flights that land near the destination. Version 1.8.2 lets a course guide you in reverse before a return flight has been recorded. Version 1.8.1 added multiple named courses and endpoint labels. Existing 1.8.0 course data is migrated automatically into a course named **Default**, including its recorded flights. Version 1.7.1 made No Strip aware of active Litematica placements. Version 1.3.0 replaced the old standalone scout JARs. Remove the standalone No Strip and scout JARs before installing this version.

When Mod Menu is installed, use **Mods > Fry Utilities > Configure** to enable or disable villager outlines, enchanted-book labels, nautilus-shell highlights, trident highlights, and the container-export button, or to change the villager display range from its 20-block default. The **No Strip** page controls tool-transformation protection and its action-bar feedback. The **Scouts** page has a master switch for each merged scout; disabling one stops its scanning and observations and hides its Xaero markers while retaining saved records. The **Scout chat** page independently controls automatic messages. Scout command replies remain visible when an automatic-message switch is off. Mod Menu is optional. Settings are stored in `config/fry-utilities/settings.json`.

## Nether Elytra course

In the Nether, use `/elytracourse create "Hub to Farm"` to make and select a course. Stand at its first endpoint and run `/elytracourse a`, then stand at the second and run `/elytracourse b`. You can also specify exact coordinates with `/elytracourse a <x> <y> <z>` and `/elytracourse b <x> <y> <z>`. The endpoints must be at least 32 blocks apart. `/elytracourse label a "Nether Hub"` and `/elytracourse label b "Gold Farm"` give the endpoints meaningful names. Names must be unique among courses in the same world regardless of capitalization; use quotes around names with spaces.

While wearing and using an Elytra, visit within **five blocks** of one endpoint to arm recording, then glide toward the other. A flight is saved when you reach within **14 blocks** of the destination while gliding or when you land there. The opposite direction is learned separately. Within five blocks of any endpoint, its marker and name appear even before a flight is learned; after a completed flight, the floating line appears there too. When you start gliding, the selected course takes priority at shared endpoints and its line remains visible throughout the trip. Away from endpoints, lines hide when you are not gliding. Use `/elytracourse list` and `/elytracourse select "Hub to Farm"` to manage the selected course.

After recording A-to-B once, approaching B also shows that same path in reverse so you can follow it back to A. This is a geometry guide; the return time remains unmeasured until you record a return flight. When both directions have measured routes, the guide uses the route with the lower estimated travel time, reversing its geometry for the other direction. Equal times keep each direction's own route. The recorded times remain separate because following a track backward does not prove an equally quick return. You can begin gliding just beyond the five-block activation radius: after visiting an endpoint, the course remains ready for up to 100 ticks and 24 blocks during takeoff.

The mod keeps up to eight completed trips per direction for each course and estimates the quickest course from their recorded travel times. It can combine faster stretches of separate trips where the recorded flight centers pass through nearly the same block and travel in the same direction. It never invents a long shortcut between tracks. A cyan line shows the first-to-second direction and an amber line shows the return. Starting from the player's nearest point on the route, only the next 20 blocks of line are drawn through terrain. The distance follows each bend rather than cutting a straight-line radius. The first completed trip gives a usable line; repeated trips can improve it. The estimate is based on your observed flying, and cannot certify that an unflown alternative is safe or faster.

`/elytracourse` reports the selected course's endpoints, run counts, and estimated times, including whether a line has been learned. `/elytracourse rename <name>` renames it, `/elytracourse clear` removes its flights while keeping endpoints, and `/elytracourse delete` removes the selected course (at least one course is retained). `/elytracourse off` and `/elytracourse on` pause or resume recording and the lines; the **Elytra course** Mod Menu page has the same switch. Changing an endpoint clears that course's old flights. Each server or singleplayer world has its own local course file under `config/fry-utilities`; route data is never sent to the server. Landing outside the destination radius or making a position jump discards the current recording with a chat explanation.

## Container JSON exports

Chest, barrel, Ender Chest, shulker-box, hopper, dispenser, and dropper screens show a compact **J** button beside the player-inventory label, clear of Inventory Profiles Next's controls. Clicking it writes the container slots to `config/fry-utilities/container-exports/<timestamp>-<container>.json` and adds a clickable **Open JSON** shortcut to local chat. The message is never sent to the server.

Each occupied slot records its zero-based slot number, item ID, display and base names, count, component count, applied or stored enchantment IDs/names/levels, and durability as remaining, maximum, damage, and percentage. Player-inventory slots shown below the container are excluded.

## No Strip

No Strip protection is enabled by default. It prevents accidental log stripping, copper scraping and unwaxing without blocking ordinary axe or shovel use such as extinguishing campfires. When Litematica is installed and an enabled placement covers the clicked block, shovel path creation is allowed only where that schematic expects a dirt path. Shovel path gating is inactive without Litematica and outside enabled schematic placements. Press the **Toggle No Strip Protection** keybind (`Y` by default) to allow or block these protections. The key can be reassigned under **Options > Controls > Key Binds > Fry Utilities - No Strip**, and the choice is saved immediately.

## Villager trading

- Click a trade to perform it directly from your inventory.
- Hold Shift while clicking to repeat the trade until stock, supplied items, or inventory space runs out.
- Hold Ctrl while clicking for untouched vanilla behavior.
- Middle-click any visible trade row to star or unstar it. A gold star appears beside starred rows, and starred enchanted books supply the floating book-name label.
- Shift-middle-click a starred row to mark or unmark it for villager highlighting. A green diamond means that marked trade is available; a red diamond means it is sold out.
- A villager glows green only when a trade is both starred and marked for highlighting, and that trade is currently available.
- A villager with a favorited enchanted-book trade shows an aqua `Book: Mending` style label within 20 blocks. Multiple favorited books are listed together, and the label remains visible while a trade is sold out.

Favorites are saved per villager UUID, world/server, and dimension under `config/fry-utilities`. A client cannot request a closed villager's live offers from a vanilla server, so an open trading screen remains authoritative. While the screen is closed, Fry Utilities recognizes the profession-specific work sound at the villager's position and estimates a restock using vanilla's 2,400-tick separation and two-restocks-per-day limit. The next open trading screen confirms and corrects that estimate. `/fryutilities` reports how many current highlights rely on an estimate.

Use `/fryutilities` for star and highlight counts plus a controls reminder. Remove the original Villager Trading Plus JAR before installing this replacement; Fabric reports the two mods as incompatible to prevent both from rewriting the same screen.

## Drowned equipment

Fry Utilities includes the former Nautilus Scout feature and expands it to tridents. A living drowned holding either item in either hand receives a through-block outline:

- Nautilus shell: coral orange sampled from the shell texture.
- Trident: teal sampled from the trident texture.

When both hands contain highlighted items, the main-hand item determines the outline color. The outline disappears when the item is removed or the drowned dies. This is client-side rendering only; it does not apply the Glowing effect, change the entity, or add a waypoint. Remove the standalone Nautilus Scout JAR after upgrading because its functionality is now included here.

## Merged scouts

All three scouts are enabled by default and keep their existing commands:

- [Slime Scout](docs/slime-scout.md): slime sightings, split filtering, dismissed chunks, and `/slimescout`.
- [Monument Scout](docs/monument-scout.md): sponge and elder surveys with `/monumentscout`.
- [End City Scout](docs/end-city-scout.md): city centers, ship elytra, shulker observations, and `/endcityscout`.

Their optional Xaero integration uses the shared **Minecraft Scouts** waypoint set. Xaero is not bundled or required for detection, persistence, or commands.

The fast-trading behavior is a clean in-project adaptation of the MIT-licensed Villager Trading Plus and Easier Villager Trading projects. See `THIRD_PARTY_NOTICES.md`.
