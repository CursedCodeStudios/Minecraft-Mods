# Minecraft Mod Suite

A multi-project Fabric workspace for Minecraft 26.2. Each produced mod is independently installable; the suite shares build conventions and releases all modules at one version. Fry Utilities contains villager trading, container JSON exports, a learned Nether Elytra course, drowned highlights, No Strip protection, Slime Scout, Monument Scout, and End City Scout. Bedrock Scout, Seed Scout, and Alt Affairs are separate mods. Version 1.9.0 introduces Alt Affairs for automatic alt-account sleeping with chat pause controls. The merged scouts retain their commands and saved-data locations.

## Layout

```text
gradle.properties             Suite, platform, integration, and test versions
settings.gradle               Module registration and Loom plugin version
build.gradle                  Aggregate checks and release packaging
gradle/fabric-mod.gradle       Shared Fabric/Java/test/metadata conventions
mods/bedrock-scout/            Exposed 3x3 Nether-roof bedrock formations
mods/seed-scout/               Local Nether-bedrock world-seed recovery
mods/watcher/                  Automatic alt-account sleeping and chat pause controls
mods/fry-utilities/             Trading, highlights, and merged structure/entity scouts
templates/fabric-mod/          Starting point for the next mod
```

Version 1.12.0 adds local sleep coordination between Fry Utilities on your main and Alt Affairs on your alts. `/alts` opens a live status dashboard; `/alts sleep pause` and `/alts sleep resume` control alts on your current server without sending server chat. The **Local alts** Mod Menu page also opens the dashboard. Separate instances running under the same operating-system user automatically share communication files; neither mod requires the other for its existing features.

Version 1.11.0 added `/elytracourse export` and `/elytracourse import` to Fry Utilities. Main and alt instances on the same machine share a course-export folder, and transfers retain endpoint labels and recorded flights in both directions.

## Build and develop

Set `JAVA_HOME` to a JDK matching `java_version` (currently 25), then run commands from the repository root. Use `./gradlew` instead of `gradlew.bat` on macOS/Linux.

| Command | Result |
| --- | --- |
| `gradlew.bat build` | Build/test every mod, verify metadata, create the suite ZIP |
| `gradlew.bat :fry-utilities:build` | Build/test Fry Utilities and its merged scouts |
| `gradlew.bat :fry-utilities:runClient` | Launch its development client |
| `gradlew.bat check` | Run all checks and tests |
| `gradlew.bat collectMods` | Gather current installable JARs and manifest |
| `gradlew.bat suiteZip` | Package the suite without running unit tests |
| `gradlew.bat clean` | Remove root and module build outputs |

Release outputs:

- `build/suite/`: installable JARs only, plus `suite-manifest.json`. Collection removes stale files from this dedicated output directory.
- `build/distributions/minecraft-mod-suite-<version>.zip`: all mods with installation instructions and license.
- `mods/<module>/build/libs/`: individual mod JAR and sources JAR.

Only copy regular mod JARs into Minecraft's `mods` directory; do not install source JARs or the ZIP. See [installation](INSTALL.md), [Fry Utilities documentation](mods/fry-utilities/README.md), its [Slime Scout](mods/fry-utilities/docs/slime-scout.md), [Monument Scout](mods/fry-utilities/docs/monument-scout.md), and [End City Scout](mods/fry-utilities/docs/end-city-scout.md) guides, plus the separate [Bedrock Scout](mods/bedrock-scout/README.md), [Seed Scout](mods/seed-scout/README.md), and [Alt Affairs](mods/watcher/README.md) guides.

The three scouts inside Fry Utilities and the separate Bedrock Scout use the shared **Minecraft Scouts** Xaero waypoint set. Each scout manages its own markers, so its marker toggle does not hide another scout's markers.

Scout waypoints are permanent Xaero records. They are saved on changes (at most once every five seconds) and on shutdown, remain after leaving a dimension, and are reused by generated name prefix and horizontal position on reconnect, including markers moved to another set. During a dimension change the scouts let Xaero handle its own world transition instead of forcing another synchronous save. Keep their generated names/positions for automatic reuse; renaming or moving their coordinates between sessions can leave a separate user marker when the scout regenerates its own. Marker hide commands change visibility without deleting records. Scout data remains the source for survey/status updates; deleting a Slime Scout marker while its scout is active still dismisses that chunk.

## Continuous integration

[GitHub Actions CI](https://github.com/CursedCodeStudios/Minecraft-Mods/actions/workflows/ci.yml) runs on pushes and pull requests to `main`, and can also be started manually. It builds and tests the whole suite on Linux and Windows, using the Java version from `gradle.properties` and the Gradle wrapper. Every new module registered in the suite is included automatically.

Successful runs upload the mod JARs, release manifest, and suite ZIP as downloadable artifacts. Test and diagnostic reports are uploaded even when the build fails. Artifacts are retained for 14 days. CI uses read-only repository permissions, validates the Gradle wrapper, and caches dependencies; only pushes to `main` write dependency caches. Action revisions are pinned, with weekly Dependabot update pull requests.

For merge protection, select the `Build and test (ubuntu-latest)` and `Build and test (windows-latest)` checks in GitHub's branch rules. CI builds artifacts; it does not publish GitHub Releases or upload mods to Modrinth/CurseForge.

## Central versioning

Edit **`suite_version` in `gradle.properties`** to release every mod at the same version. The shared convention expands that version and the Minecraft, Fabric Loader, Java, and Fabric API requirements into `fabric.mod.json`. All dependency versions also live in this file. The Gradle distribution remains pinned by the standard `gradle/wrapper/gradle-wrapper.properties` file.

`verifySuite` rejects duplicate mod IDs and inconsistent platform/version metadata. It runs as part of root `check`, `build`, and packaging. The generated release manifest lists each mod, version, JAR filename, and required dependencies. Shared archive settings use stable file order and omit file timestamps.

Every completed behavior change receives a new `suite_version`: use a patch bump for backward-compatible fixes or refinements, a minor bump for substantial backward-compatible features, and a major bump for breaking changes. Then run `gradlew.bat clean build` and distribute the ZIP or selected JARs. This is a shared release version, so unchanged mods receive the same new version. No publishing or signing credentials are required and nothing is uploaded automatically.

## Add a mod

1. Copy `templates/fabric-mod` to `mods/<module-name>`.
2. Replace the template mod ID/name/description/entrypoint in `src/main/resources/fabric.mod.json`, retaining the shared `${...}` version placeholders.
3. Add a matching initializer and its code under `src/main/java`; add tests under `src/test/java`. Choose the appropriate client/common environment and entrypoint for the new mod.
4. Register it in `settings.gradle`:

   ```groovy
   include 'my-mod'
   project(':my-mod').projectDir = file('mods/my-mod')
   ```

5. Add only mod-specific dependencies in its `build.gradle`. Put new dependency versions in root `gradle.properties`.
6. Run `gradlew.bat build`. The root tasks automatically include every registered module in checks, manifest generation, and packaging.

Modules do not depend on each other by default. Introduce a runtime shared-core mod only when code actually requires one; declare both its project dependency and Fabric metadata requirement in consumers. Build conventions already provide shared tooling without requiring players to install an extra core mod.

## Existing workspace toolchain

The initial project downloaded Java/Gradle to ignored `.tools` and used `.gradle-user-home` as its local cache. To use those existing downloads in PowerShell:

```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/java -Directory)[0].FullName
$env:GRADLE_USER_HOME = "$PWD/.gradle-user-home"
& ./.tools/gradle/gradle-9.5.1/bin/gradle.bat build
```

Normal checkouts only need JDK 25 and the committed wrapper; Gradle resolves the other dependencies. Xaero is compile-only for Fry Utilities and Bedrock Scout and is not bundled.

Fry Utilities is a full in-project rewrite of Villager Trading Plus with persistent starred trades and separate highlight markers. Middle-click stars a trade for labels; Shift-middle-click marks a starred trade for the villager outline. Only an available trade with both states makes its villager glow green. It also replaces Nautilus Scout and No Strip and contains Slime Scout, Monument Scout, and End City Scout. Version 1.8.5 compares learned Nether Elytra routes in both directions and shows the faster track's geometry, reversed when needed. The line shows the next 20 blocks along the course from the player's nearest point. Lines appear within five blocks of an endpoint and stay visible while flying. Remove all superseded standalone JARs before installing it.
