# Repository Guidelines

## Minecraft Launch Directory Access

The user's current Minecraft launch directory is:

`/Users/eric/Library/Application Support/PrismLauncher/instances/XPlus PerioTable Modpack (Fabric)`

- Treat this directory and all of its contents as read-only by default. Listing the directory and reading files do not require approval.
- Before deleting files or making any other changes in this directory, ask the user and obtain explicit authorization for the proposed changes. This includes creating, editing, overwriting, moving, or renaming files, even when the change appears non-destructive.
- This requirement also applies to changes made indirectly through scripts, build tasks, or applications. Permission to access the directory alone does not authorize changes.

## Development Data Policy

Treat this module as a clean, not-yet-released environment during development. Historical data compatibility, migration, and fallback behavior are not required. Breaking changes are allowed; prefer the best implementation over preserving old formats or behavior. This does not override the Minecraft launch directory access rules above.

## Project Structure & Module Organization

This is a Fabric client mod for Minecraft 26.3, built with Gradle and Loom. Production code is split by environment:

- `src/main/java`: environment-independent models, analysis, storage, and state-machine logic.
- `src/client/java`: Fabric/Minecraft integration, screens, rendering, keybinds, and client mixins.
- `src/main/resources`: the main mod descriptor, assets, icons, and localization files.
- `src/test/java`: JUnit unit tests for analysis, persistence, probing, deltas, and performance.
- `src/gametest`: client integration-test mod and resources; it is excluded from the release JAR.
- `docs/validation`: committed test logs, reports, and screenshots; `build/` contains generated outputs.

Keep new classes under `cn.ericcraft.villagerverdict` and place client-only code in the client source set.

## Build, Test, and Development Commands

Use the checked-in Gradle Wrapper and Java 25:

```sh
./gradlew test                 # Run JUnit tests
./gradlew runClientGameTest    # Run client integration tests
./gradlew clean build          # Clean, test, and produce the remapped JAR
./gradlew runClient            # Launch a development client
```

Set `JAVA_HOME` to a Java 25 installation when the shell default differs. The release artifact is written to `build/libs/`.

## Coding Style & Naming Conventions

Use UTF-8, four-space indentation, braces on the declaration line, and conventional Java naming: `PascalCase` types, `camelCase` methods/variables, and `UPPER_SNAKE_CASE` constants. Keep package names lowercase. Prefer small immutable model objects and deterministic analysis logic; keep Minecraft API access in client-facing classes. No repository formatter or linter is configured, so match surrounding code and keep imports organized.

## Testing Guidelines

Add focused JUnit 5 tests under `src/test/java` using `*Test.java` names. Run `./gradlew test` for fast checks and `./gradlew runClientGameTest` when changing screens, mixins, packet handling, or client lifecycle behavior. For GUI or integration changes, update validation evidence in `docs/validation` when practical.

## Commit & Pull Request Guidelines

Use short imperative subjects with the existing prefixes, such as `feat: ...` or `fix: ...`. Keep commits focused. Pull requests should explain user-visible behavior, identify affected source sets, list validation commands and results, link related issues, and include screenshots for visual changes. Call out Minecraft, Fabric, or data-format compatibility implications.
