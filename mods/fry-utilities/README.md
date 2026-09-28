# Fry Utilities

Fry Utilities is a multi-purpose client-only Fabric mod for Minecraft 26.2. It combines villager trading tools, storage-container JSON exports, drowned equipment highlights, No Strip protection, Slime Scout, Monument Scout, and End City Scout in one JAR.

Version 1.7.1 makes No Strip aware of active Litematica placements and applies shovel path gating only inside those placements. It also includes master switches for Slime Scout, Monument Scout, and End City Scout, storage-container JSON exports, independent scout chat controls, and the Xaero dimension-change fix. Version 1.3.0 replaced the old standalone scout JARs. Existing scout data remains compatible. Remove the standalone No Strip and scout JARs before installing this version.

When Mod Menu is installed, use **Mods > Fry Utilities > Configure** to enable or disable villager outlines, enchanted-book labels, nautilus-shell highlights, trident highlights, and the container-export button, or to change the villager display range from its 20-block default. The **No Strip** page controls tool-transformation protection and its action-bar feedback. The **Scouts** page has a master switch for each merged scout; disabling one stops its scanning and observations and hides its Xaero markers while retaining saved records. The **Scout chat** page independently controls automatic messages. Scout command replies remain visible when an automatic-message switch is off. Mod Menu is optional. Settings are stored in `config/fry-utilities/settings.json`.

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
