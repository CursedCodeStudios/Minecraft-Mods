# Fry Utilities

Fry Utilities is a multi-purpose client-only Fabric mod for Minecraft 26.2. It combines villager trading tools, drowned equipment highlights, Slime Scout, Monument Scout, and End City Scout in one JAR.

Version 1.3.0 replaces the old standalone Slime Scout, Monument Scout, and End City Scout JARs. Their commands and existing data under `config/slime-scout`, `config/monument-scout`, and `config/end-city-scout` remain compatible. Remove those standalone JARs before installing this version.

When Mod Menu is installed, use **Mods > Fry Utilities > Configure** to enable or disable villager outlines, enchanted-book labels, nautilus-shell highlights, and trident highlights, or to change the villager display range from its 20-block default. Mod Menu is optional. Settings are stored in `config/fry-utilities/settings.json`.

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

All three scouts start automatically and keep their existing commands:

- [Slime Scout](docs/slime-scout.md): slime sightings, split filtering, dismissed chunks, and `/slimescout`.
- [Monument Scout](docs/monument-scout.md): sponge and elder surveys with `/monumentscout`.
- [End City Scout](docs/end-city-scout.md): city centers, ship elytra, shulker observations, and `/endcityscout`.

Their optional Xaero integration uses the shared **Minecraft Scouts** waypoint set. Xaero is not bundled or required for detection, persistence, or commands.

The fast-trading behavior is a clean in-project adaptation of the MIT-licensed Villager Trading Plus and Easier Villager Trading projects. See `THIRD_PARTY_NOTICES.md`.
