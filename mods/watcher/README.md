# Alt Affairs

Alt Affairs is a standalone client-only Fabric mod for Minecraft 26.2 for managing alt accounts. Version 1.10.0 adds automatic connection to the first saved server. Introduced as Watcher in suite version 1.9.0, it was renamed in 1.9.1. It also provides automatic sleeping with chat pause controls. It requires Fabric Loader and Fabric API and works independently of Fry Utilities. Install `alt-affairs-<version>.jar` in each alt's instance and replace the older `watcher` JAR.

Automatic reconnection is enabled by default. Put the intended server first in Minecraft's saved multiplayer list. From the title screen, multiplayer list, or disconnect screen, an offline alt waits ten seconds and attempts to join that first server. Failed connections are retried after another ten-second wait. Leaving a server manually also triggers reconnection. An empty server list is left alone, and active connections, singleplayer sessions, loading screens, and other menus are not interrupted. Each retry reloads the saved list, so reordering its first entry changes the target.

Use `/watcher reconnect off` to disable this feature or `/watcher reconnect on` to enable it. This global choice survives restarts and is independent of sleeping and the chat pause. To disable it while offline, close Minecraft and set `autoReconnect=false` in `config/watcher/state.properties`.

Park the alt beside an available Overworld bed. Alt Affairs uses the nearest unoccupied bed it can reach and see as soon as Minecraft's actual bed rule permits sleeping, approximately 18:32 in clear weather. The game rule also accounts for weather. Alt Affairs does not walk to distant beds. The server still decides whether the bed is usable, including nearby monsters and obstructions. Failed beds are retried after five seconds; another reachable bed can be tried in the meantime. Bed interaction is never attempted in the Nether or End.

Send either of these messages in normal server chat from any account:

- `Please don't sleep` pauses sleeping and wakes a watcher that is already in bed. It responds with `Okay bestie! We won't sleep! Say "pickles yummy yummy" when we can sleep again`.
- `pickles yummy yummy` clears the pause. It responds with `Ugh finally! I'm so tired!` and can immediately use a bed if sleeping is currently allowed.

Commands match the whole chat message, ignoring capitalization and outer spaces. Curly apostrophes are accepted. Signed player chat and common server-formatted chat such as `<Player> message` or `Player: message` are supported. Alt Affairs replies cannot trigger other watchers. Each instance acknowledges each recognized request, so several installed alts can each respond.

The chat pause survives reconnects and game restarts and stays active until the resume phrase is received. Pause and manual enable settings are stored per server or singleplayer world in `config/watcher/state.properties`. Messages sent while an alt is disconnected cannot be observed by that alt.

Use `/watcher` for local status, `/watcher off` to disable automatic bed use, and `/watcher on` to enable it. These commands do not send messages to other players. Turning automation back on does not clear a chat pause. Automation is enabled by default for new worlds and servers.
