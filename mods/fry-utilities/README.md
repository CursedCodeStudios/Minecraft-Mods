# Fry Utilities

Fry Utilities is an expandable client-only Fabric mod for Minecraft 26.2. Its first feature fully replaces Villager Trading Plus inside this project and adds persistent favorite-trade highlighting.

## Villager trading

- Click a trade to perform it directly from your inventory.
- Hold Shift while clicking to repeat the trade until stock, supplied items, or inventory space runs out.
- Hold Ctrl while clicking for untouched vanilla behavior.
- Middle-click any visible trade row to favorite or unfavorite it. A gold star appears beside favorited rows.
- A villager with at least one favorited available trade glows green while it is within 20 blocks.

Favorites are saved per villager UUID, world/server, and dimension under `config/fry-utilities`. A client cannot request a closed villager's live offers from a vanilla server, so an open trading screen remains authoritative. While the screen is closed, Fry Utilities recognizes the profession-specific work sound at the villager's position and estimates a restock using vanilla's 2,400-tick separation and two-restocks-per-day limit. The next open trading screen confirms and corrects that estimate. `/fryutilities` reports how many current highlights rely on an estimate.

Use `/fryutilities` for favorite counts and a controls reminder. Remove the original Villager Trading Plus JAR before installing this replacement; Fabric reports the two mods as incompatible to prevent both from rewriting the same screen.

The fast-trading behavior is a clean in-project adaptation of the MIT-licensed Villager Trading Plus and Easier Villager Trading projects. See `THIRD_PARTY_NOTICES.md`.
