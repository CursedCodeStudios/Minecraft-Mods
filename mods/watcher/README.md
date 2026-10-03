# Alt Affairs

Alt Affairs is a standalone client-only Fabric mod for Minecraft 26.2 for managing alt accounts. Version 1.10.1 immediately attempts to sleep when chat permission is restored at night. Version 1.10.0 added automatic connection to the first saved server. Introduced as Watcher in suite version 1.9.0, it was renamed in 1.9.1. It also provides automatic sleeping with chat pause controls. It requires Fabric Loader and Fabric API and works independently of Fry Utilities. Install `alt-affairs-<version>.jar` in each alt's instance and replace the older `watcher` JAR.

Automatic reconnection is enabled by default. Put the intended server first in Minecraft's saved multiplayer list. From the title screen, multiplayer list, or disconnect screen, an offline alt waits ten seconds and attempts to join that first server. Failed connections are retried after another ten-second wait. Leaving a server manually also triggers reconnection. An empty server list is left alone, and active connections, singleplayer sessions, loading screens, and other menus are not interrupted. Each retry reloads the saved list, so reordering its first entry changes the target.

Use `/watcher reconnect off` to disable this feature or `/watcher reconnect on` to enable it. This global choice survives restarts and is independent of sleeping and the chat pause. To disable it while offline, close Minecraft and set `autoReconnect=false` in `config/watcher/state.properties`.

Park the alt beside an available Overworld bed. Alt Affairs uses the nearest unoccupied bed it can reach and see as soon as Minecraft's actual bed rule permits sleeping, approximately 18:32 in clear weather. The game rule also accounts for weather. Alt Affairs does not walk to distant beds. The server still decides whether the bed is usable, including nearby monsters and obstructions. Failed beds are retried after five seconds; another reachable bed can be tried in the meantime. Bed interaction is never attempted in the Nether or End.

Send either of these messages in normal server chat from any account:

- `Please don't sleep` pauses sleeping and wakes a watcher that is already in bed. It responds with `Okay bestie! We won't sleep! Say "pickles yummy yummy" when we can sleep again`.
- `pickles yummy yummy` clears the pause and responds with `Ugh finally! I'm so tired!`. With automatic sleeping enabled, it immediately tries a reachable bed if sleeping is currently allowed, clearing any previous bed retry cooldown. During daytime it waits until sleeping becomes allowed.

Commands match the whole chat message, ignoring capitalization and outer spaces. Curly apostrophes are accepted. Signed player chat and common server-formatted chat such as `<Player> message` or `Player: message` are supported. Alt Affairs replies cannot trigger other watchers. Each instance acknowledges each recognized request, so several installed alts can each respond.

The sleep pause survives reconnects and game restarts and stays active until the resume phrase or a new local resume request is received. Pause and manual enable settings are stored per server or singleplayer world in `config/watcher/state.properties`. Messages sent while an alt is disconnected cannot be observed by that alt.

Use `/watcher` for local status, `/watcher off` to disable automatic bed use, and `/watcher on` to enable it. These commands do not send messages to other players. Turning automation back on does not clear a chat pause. Automation is enabled by default for new worlds and servers.

## Local account coordination

Version 1.12.0 adds local status reporting and sleep coordination. Install Fry Utilities 1.12.0 on your main and Alt Affairs 1.12.0 on each alt. Under the same operating-system user, separate Minecraft instances automatically communicate through `<user home>/.fry-utilities/local-accounts` (normally `%USERPROFILE%\.fry-utilities\local-accounts` on Windows). Neither mod depends on the other. Both may also be installed in the same instance.

On your main, `/alts` opens the live dashboard, also available from Mod Menu's Fry Utilities **Local alts** page. It shows account names, server addresses, sleeping/paused/waiting/bed status, and connection state. Crashed instances are marked offline after six seconds without a heartbeat; old records disappear after a day. The dashboard refreshes about once per second. Long rows are clipped to the screen width; `/alts status` prints full rows in local chat.

Use the dashboard's **Pause sleeping** or **Allow sleeping** buttons, or `/alts sleep pause` and `/alts sleep resume`. Requests affect only alts using your main's current server address or singleplayer world path, across dimensions. Use the same saved server address in each instance. No coordination messages or replies are sent to Minecraft server chat. Pending requests are shown until each connected alt acknowledges them, normally within a few seconds.

Pause wakes an already sleeping alt. Allowing sleep clears its bed cooldown and attempts a reachable Overworld bed at night; it does not enable automation if `/watcher off` was used. Daytime alts wait for night. The latest local request remains available for disconnected and newly started alts and persists until changed. Each alt remembers which request it applied, so reconnecting does not undo a later chat pause. Existing server chat phrases continue to work; a fresh local request can also change that pause. Multiple main instances can send requests; the last completed write wins.

Communication file access runs on a background worker. An alt waits for its initial server-specific local permission check before automatic sleeping. Local communication errors appear in the main dashboard and `/watcher` status.
