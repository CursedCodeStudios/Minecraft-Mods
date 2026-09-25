# Nautilus Scout

A client-only Fabric mod for Minecraft 26.2 that gives living drowned a glowing outline while they hold a **nautilus shell in either hand**. It runs automatically, with no commands or configuration needed.

Install `nautilus-scout-<suite-version>.jar` from `build/suite` alongside the Fabric Loader, Fabric API, and Java versions in the suite installation guide. Xaero and the other scouts are not required. This mod adds outlines, not waypoints.

The outline uses Minecraft's normal glowing renderer, including through-block visibility and vanilla team colors (normally white). It checks the drowned's current equipment whenever the client evaluates glowing, so the added outline disappears when the shell is removed or the drowned dies. Drowned carrying only a trident do not qualify; drowned carrying a trident and an offhand shell do. Other entities and dropped shells are unaffected. Existing vanilla or other-mod glowing behavior is preserved.

This does not apply the Glowing status effect, change server entity flags, or send commands. It only works on drowned and equipment the server has sent to your client, within normal entity rendering/tracking limits. Renderer/shader mods that replace vanilla outlines can affect its appearance.

Build from the repository root with `gradlew.bat :nautilus-scout:build`, or use `gradlew.bat build` to package the whole suite. The client mixin targets `Minecraft.shouldEntityAppearGlowing` and requires its injection to succeed.

In-game visual behavior has not been manually verified. Suggested checks in a disposable creative world:

1. Give a drowned a nautilus shell in its offhand, then in its main hand; verify an outline in both cases.
2. Give it a trident without a shell; verify no added outline. Add an offhand shell and verify the outline returns.
3. Remove the shell or kill the drowned; verify the added outline disappears.
4. Verify a different mob holding a shell does not receive an added outline.
5. Apply normal Glowing to a shell-free mob; verify its existing outline remains.
