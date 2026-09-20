# Minecraft Mod Suite

A multi-project Fabric workspace for Minecraft 26.2. Each mod is independently installable; the suite shares build conventions and releases all modules at one version. The suite includes Slime Scout and Monument Scout. Slime Scout retains its mod ID, Java packages, commands, and saved-data location.

## Layout

```text
gradle.properties             Suite, platform, integration, and test versions
settings.gradle               Module registration and Loom plugin version
build.gradle                  Aggregate checks and release packaging
gradle/fabric-mod.gradle       Shared Fabric/Java/test/metadata conventions
mods/slime-scout/              Slime sightings and per-chunk visit counters
mods/monument-scout/           Ocean monument sponges and elder guardian surveys
templates/fabric-mod/          Starting point for the next mod
```

## Build and develop

Set `JAVA_HOME` to a JDK matching `java_version` (currently 25), then run commands from the repository root. Use `./gradlew` instead of `gradlew.bat` on macOS/Linux.

| Command | Result |
| --- | --- |
| `gradlew.bat build` | Build/test every mod, verify metadata, create the suite ZIP |
| `gradlew.bat :slime-scout:build` | Build/test Slime Scout only |
| `gradlew.bat :slime-scout:runClient` | Launch its development client |
| `gradlew.bat check` | Run all checks and tests |
| `gradlew.bat collectMods` | Gather current installable JARs and manifest |
| `gradlew.bat suiteZip` | Package the suite without running unit tests |
| `gradlew.bat clean` | Remove root and module build outputs |

Release outputs:

- `build/suite/`: installable JARs only, plus `suite-manifest.json`. Collection removes stale files from this dedicated output directory.
- `build/distributions/minecraft-mod-suite-<version>.zip`: all mods with installation instructions and license.
- `mods/<module>/build/libs/`: individual mod JAR and sources JAR.

Only copy regular mod JARs into Minecraft's `mods` directory; do not install source JARs or the ZIP. See [installation](INSTALL.md), [Slime Scout documentation](mods/slime-scout/README.md), and [Monument Scout documentation](mods/monument-scout/README.md).

## Continuous integration

[GitHub Actions CI](https://github.com/CursedCodeStudios/Minecraft-Mods/actions/workflows/ci.yml) runs on pushes and pull requests to `main`, and can also be started manually. It builds and tests the whole suite on Linux and Windows, using the Java version from `gradle.properties` and the Gradle wrapper. Every new module registered in the suite is included automatically.

Successful runs upload the mod JARs, release manifest, and suite ZIP as downloadable artifacts. Test and diagnostic reports are uploaded even when the build fails. Artifacts are retained for 14 days. CI uses read-only repository permissions, validates the Gradle wrapper, and caches dependencies; only pushes to `main` write dependency caches. Action revisions are pinned, with weekly Dependabot update pull requests.

For merge protection, select the `Build and test (ubuntu-latest)` and `Build and test (windows-latest)` checks in GitHub's branch rules. CI builds artifacts; it does not publish GitHub Releases or upload mods to Modrinth/CurseForge.

## Central versioning

Edit **`suite_version` in `gradle.properties`** to release every mod at the same version. The shared convention expands that version and the Minecraft, Fabric Loader, Java, and Fabric API requirements into `fabric.mod.json`. All dependency versions also live in this file. The Gradle distribution remains pinned by the standard `gradle/wrapper/gradle-wrapper.properties` file.

`verifySuite` rejects duplicate mod IDs and inconsistent platform/version metadata. It runs as part of root `check`, `build`, and packaging. The generated release manifest lists each mod, version, JAR filename, and required dependencies. Shared archive settings use stable file order and omit file timestamps.

For a release: update `suite_version`, run `gradlew.bat clean build`, then distribute the ZIP or selected JARs. This is a shared release version, so unchanged mods receive the same new version. No publishing or signing credentials are required and nothing is uploaded automatically.

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

Normal checkouts only need JDK 25 and the committed wrapper; Gradle resolves the other dependencies. Xaero is compile-only for both scout mods and is not bundled.
