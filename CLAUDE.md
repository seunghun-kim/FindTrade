# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FindTrade** is a Minecraft Paper plugin that helps players find villagers offering specific enchantment book trades. It displays particle effects to guide players to matching librarians within defined regions.

- Plugin command: `/findtrade` (alias `/ft`)
- Current version: 0.4.0
- Target: Paper 26.1.2 (compiled against `26.1.2.build.69-stable`; `api-version: '26.1'` supports the whole 26.1.x series)
- Java target: **25** (Paper 26.1.2's runtime, class file v69). Compiled via the JDK 26 toolchain with `--release 25` — do **not** target Java 26 (class v70), the 26.1.2 server's JRE rejects it with `UnsupportedClassVersionError`.

## Build Commands

```bash
./gradlew build          # Build shadowed JAR → build/libs/FindTrade-0.4.0.jar
./gradlew runServer      # Start local Paper 26.1.2 test server with plugin loaded
./gradlew clean build    # Clean rebuild
```

No test suite exists — testing is done by running the server with `runServer`.

## Dependency Versions (Paper 26.1.x line)

- **Paper API**: `io.papermc.paper:paper-api:26.1.2.build.69-stable` (`compileOnly`). Note the new Mojang/Paper calendar versioning `<mcversion>.build.<build>-<status>`. Since 26.1, Mojang ships deobfuscated server jars, so Paper no longer remaps plugins to Spigot mappings — this plugin uses only the public Bukkit/Paper API, so no remapping is needed.
- **WorldEdit**: `com.sk89q.worldedit:worldedit-bukkit:7.4.3` (`compileOnly`) — first **stable** release with Paper 26.1.x support (resolved the earlier `7.4.3-SNAPSHOT`/beta workaround). Still an optional soft-dependency: `WorldEditHandler` is loaded via `Class.forName` only when present; `RegionSubCommand` falls back to manual coordinate input otherwise.
- **Pathetic**: core `de.bsommerfeld.pathetic:{api,engine}:5.5.1` (Maven Central) + `com.github.bsommerfeld.pathetic-bukkit:{core,paper}:5.5.1` (JitPack). The `paper` provider module is required for Paper 26.1.x. 5.5.x replaced the old `CompletionStage` return of `findPath` with the `PathfindingSearch` API — `PathfindingManager` chains `.ifPresent().orElse().exceptionally()`; keep all four Pathetic artifacts on the same version.

### Deprecation note
- `World#getName()` is deprecated since 26.1 in favor of `getKey()`, but `SQLiteDatabase` intentionally keeps `getName()` for the persisted world-name column to preserve existing `regions.db` data (deprecation warning only — not breaking).

## Architecture

### Package Structure

```
org.teamck.findtrade/
├── core/           # Main plugin class, domain models (Trade, VillagerRegion)
├── commands/       # /findtrade command + subcommands, WorldEdit integration
├── database/       # SQLite persistence (Database interface + SQLiteDatabase impl)
├── manager/        # Business logic: ParticleManager, PathfindingManager, EnchantmentManager, MessageManager
├── ui/             # Per-player TUI sessions: SearchResultTUI, RegionTUI, TUISessionManager
├── listener/       # Event listeners (ParticleListener)
└── pathfinding/    # Custom A* node processor (DoorAwareWalkableProcessor)
```

### Key Design Decisions

**Dependency flow**: `FindTradePlugin` initializes all managers and passes them as constructor arguments throughout — no service locator or static state.

**TUI Sessions**: `TUISessionManager` holds per-player state (paginated search results, in-progress region edits). Each player gets their own `SearchResultTUI` / `RegionTUI` instance.

**Pathfinding**: Uses the [Pathetic](https://github.com/patheloper/pathetic) library (A* via Pathetic Engine + Pathetic-Bukkit) for async, chunk-aware pathfinding. `DoorAwareWalkableProcessor` extends the default so doors are treated as passable. Pathfinding can be disabled in config in favor of straight-line particle paths.

**WorldEdit**: Optional soft-dependency. `WorldEditHandler` isolates all WorldEdit API calls and is only loaded via `Class.forName` when WorldEdit is present. `RegionSubCommand` falls back to coordinate input when WorldEdit is absent.

**Particle effects**: Three named effects — `pillar` (above villager), `path` (player → villager), `moving_highlight` (animated marker). All are configurable in `config.yml` (particle type, count, offset, speed, interval). Per-player BukkitTask handles scheduling and cleanup.

**Enchantment normalization**: `EnchantmentManager` maps various enchantment name formats (display names, namespaced keys) to canonical keys for consistent lookup.

**Localization**: `MessageManager` loads `localization/en.yml` or `localization/ko.yml` based on config. All user-facing strings must go through `MessageManager`.

### Data Model

- **`VillagerRegion`**: Named cubic region (min/max XYZ). Has methods to check if a location is inside and to find all librarian villagers within bounds.
- **`Trade`**: A villager's enchantment book trade offer — holds the villager entity, enchantment key, level, and cost.
- Regions are persisted to SQLite at `plugins/FindTrade/regions.db`.

### Permissions

- `findtrade.use` (default: true) — search, view regions
- `findtrade.write` (default: op) — create/edit/delete regions (inherits `findtrade.use`)
