# Changelog

All notable changes to EzRTP are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Release tags use the `v` prefix (e.g. `v3.0.2`).

---

## [Unreleased]

### Added

### Changed

### Fixed

### Removed

---

## [3.4.2] - 2026-07-07

### Fixed

- `/rtp reload` now immediately applies GUI configuration updates from `gui.yml` by rebuilding GUI rendering from the current in-memory configuration instead of startup-cached values.
- Reload now closes both standard RTP GUI sessions and faction-claim GUI sessions so open inventories cannot retain stale pre-reload settings.

### Added

- Focused regression test coverage for GUI reload behavior:
  - `RandomTeleportGuiManagerReloadConfigTest` verifies that opening the GUI after configuration replacement uses the updated title and row count immediately.

---

## [3.4.1] - 2026-07-06

### Added

- Minecraft `26.2` compatibility updates:
  - added `SULFUR_CAVES` to rare-biome defaults/search paths,
  - updated biome compatibility handling for modern registry-based lookup paths.

### Changed

- CI server download resolution migrated to PaperMC Fill API (`fill.papermc.io`) and now sends an explicit non-generic `User-Agent` header as required by the service.
- Smoke testing workflow expanded from a single Folia target to a matrix that validates:
  - latest Folia build, and
  - Paper `1.13.x` for earliest supported compatibility coverage.
- Dependency and tooling updates merged via Dependabot:
  - `com.github.ez-plugins:teams-api` `1.8.0` -> `2.4.0`.
  - GitHub Actions `actions/checkout` `v6` -> `v7`.
  - GitHub Actions `DavidAnson/markdownlint-cli2-action` `v23` -> `v24`.
- Kyori Adventure BOM and related versions moved to `5.2.0` for current Paper API compatibility.

### Fixed

- GitHub Actions workflow resolver for smoke-test target versions:
  - fixed YAML parsing issues in embedded resolver scripting,
  - fixed selector propagation into Python resolver invocation,
  - fixed mixed-type version sort key handling that could fail on Paper version token comparisons.
- Removed a duplicate stale block in `messages/en.yml` that produced duplicate-key warnings at startup.
- Replaced deprecated `Biome.valueOf`/`Biome.values` usage with compatibility-safe registry-backed resolution paths used across biome-related logic.

---

## [3.4.0] - 2026-05-24

### Added

- **EzCountdown integration** (optional soft-dependency):
  - RTP countdown display can now be delegated to [EzCountdown](https://modrinth.com/plugin/ezcountdown) for richer, configurable display channels.
  - Configurable display types per RTP world: `ACTION_BAR`, `BOSS_BAR`, `TITLE`, `CHAT`, `SCOREBOARD`, `DIALOG`.
  - Configurable MiniMessage format string with `{formatted}` placeholder for remaining time.
  - Each teleporting player receives an ephemeral, per-player countdown with permission-scoped visibility.
  - Falls back to the built-in bossbar/chat countdown automatically when EzCountdown is absent or `ezcountdown.enabled: false`.
  - New `countdown.ezcountdown.*` config section in `rtp.yml` (disabled by default).
- `EzCountdown` added as optional soft-dependency in `plugin.yml`.
- **Factions RTP (TeamsAPI)**:
  - Added `/rtp faction` to open a claim-selection GUI from all TeamsAPI claims available to the player's team.
  - Added TeamsAPI subcommand integration for `/f rtp` via the TeamsAPI subcommand API (when provided by the installed TeamsAPI version).
  - Claim selection now sets the selected claim chunk as RTP center and then applies normal per-world RTP behavior/settings.
- **New `faction-gui.yml` file** for full claim-GUI configuration.
- **Heatmap claim overlays** (admin insight):
  - Added claim border overlay mode for `/rtp heatmap` and `/rtp heatmap save` via `claims-overlay` flag.
  - Added `rtp.yml` options under `heatmap.claims-overlay.*`:
    - `enabled`
    - `style` (`border`)
    - `color`
    - `line-width`
- **Claim-constrained fake RTP simulation**:
  - Added `/rtp fake <amount> claims [world]` to generate simulated points on faction claims owned by the executor’s team.

### Changed

- **TeamsAPI** dependency bumped from `1.4.1` to `1.8.0`.
- TeamsAPI `/f rtp` subcommand integration rewritten: replaced the reflection-based `Proxy` approach with a proper `AbstractTeamsSubcommand` subclass (requires TeamsAPI ≥ 1.8.0).
- TeamsAPI subcommand registration now uses an explicit `isPluginEnabled("TeamsAPI")` guard instead of relying on `NoClassDefFoundError` suppression.
- Faction claim GUI icons now attempt to use player skulls when claimant/owner identity is available, with configurable fallback material when unavailable.
- Added configurable title, size, claim item format/lore, skull toggle, and navigation slot/name settings for faction GUI pages.
- Update checker refactored to use `mc-plugin-update-notifier` with Modrinth as primary source and GitHub Releases as fallback source.

### Fixed

- Heatmap enablement now resolves correctly for inherited/fallback world settings (`heatmap.enabled`) and no longer reads as disabled unexpectedly in world/GUI override paths.
- `/rtp heatmap` no longer hard-requires biome cache for non-biome heatmaps. Biome cache is only required for biome-filtered heatmap requests.
- Spigot startup compatibility fixed in update checking (`JavaPlugin#getDescription().getVersion()` used instead of Paper-only metadata calls).
- Message loading now supports both top-level keys and nested `messages.*` language-file layouts.
- Language file handling is now non-destructive: startup repair only targets clearly corrupted message files, with automatic backfill of missing message keys while preserving existing translations/customizations.

---

## [3.3.0] - 2026-05-16

### Added

- **PvP tag integration**: EzRTP now detects when a player enters combat and cancels their
  pending teleport or active countdown. Three plugins are supported out of the box:
  - **[CombatLogX](https://www.spigotmc.org/resources/combatlogx.31689/)** — auto-detected at startup; soft dependency.
  - **[PvPManager](https://modrinth.com/plugin/pvpmanager)** — auto-detected at startup; soft dependency.
  - **[Simple Combat Log](https://modrinth.com/plugin/simple-combatlog)** (NikeyV1/CombatLog) — auto-detected at startup; soft dependency.
  - All three are added to `softdepend` in `plugin.yml` so they load before EzRTP when
    present. No configuration change is required on servers that do not use any of them.
  - New `pvp-tag-integration` section in `rtp.yml`:
    - `cancel-countdown-on-pvp-tag` (default `true`): cancel an active countdown the moment
      the player is tagged.
    - `cancel-queued-on-pvp-tag` (default `true`): skip a queued teleport if the player is
      already in combat when their slot is dispatched.
  - New message keys `countdown-pvp-cancel` and `queue-pvp-tag-cancel` in `messages/*.yml`
    for the cancellation notifications.
  - Modrinth release metadata now declares `pvpmanager` and `simple-combatlog` as optional
    dependencies.

---

## [3.2.3] - 2026-05-16

### Fixed

- **Folia teleport crash** (`UnsupportedOperationException: Must use teleportAsync while in
  region threading`): `BukkitPlatformScheduler` now overrides `teleportAsync` and, when running
  on Folia (`regionizedRuntime` capability), calls `player.teleportAsync(Location)` via
  reflection instead of the forbidden synchronous `player.teleport()`. The Bukkit module was
  the only scheduler that still used the synchronous fallback from the interface default;
  `PaperPlatformScheduler` already called `teleportAsync` directly.

---

## [3.2.2] - 2026-05-12

### Fixed

- `BukkitPlatformScheduler` now handles Folia servers correctly. Previously, running EzRTP on
  Folia without the Paper runtime module caused an `UnsupportedOperationException` during plugin
  enable because the Bukkit `CraftScheduler` rejects all synchronous task scheduling on Folia.
  The scheduler now detects `regionizedRuntime` capabilities and routes all scheduling calls
  through Folia's `GlobalRegionScheduler` / `RegionScheduler` via reflection, matching the
  behaviour already present in `PaperPlatformScheduler`.

---

## [3.2.1] - 2026-05-11

### Fixed

- `PerformanceSnapshot#fmt` now passes `Locale.US` to `String#format`, ensuring decimal separators are always locale-independent (dots, not commas).

### Changed

- Dependency scope for `ezrtp-common` in the `ezrtp-paper` and `ezrtp-purpur` modules changed to `provided`; the common module is bundled by the main plugin JAR and must not be re-included by platform modules.

---

## [3.2.0] - 2026-05-11

### Added

- **TeamsAPI claim avoidance**: EzRTP now integrates with [TeamsAPI](https://modrinth.com/plugin/teams-api) to skip chunk-claimed areas during RTP destination search.
  - New protection provider id: `teamsapi`. Works alongside the existing `worldguard` and `griefprevention` providers.
  - Enabled automatically when TeamsAPI (with a compatible claim provider) is installed. Silently skipped when absent, no errors, no configuration changes required.
  - `teamsapi` added to the default `protection.providers` list in `rtp.yml`.
  - `TeamsAPI` added to `softdepend` in `plugin.yml`.
  - Claim availability is evaluated dynamically per check, so a claim plugin that loads after EzRTP is picked up without a reload.

---

## [3.1.0] - 2026-05-09

### Added

- **Message suppression via config** (`config.yml`):
  - `messages.suppress-player`: when `true`, silences all teleport-related messages to players globally (searching, countdown, queue position, success, failure, cost).
  - `messages.suppress-console`: when `true`, silences the executor notification that `/forcertp` sends to the command sender globally.
- **`--skip-message` command flag**: can be appended to `/rtp`, `/rtp <center|region>`, `/forcertp <player> [world]`, and `/rtp forcertp <player> [world]` to suppress both player-facing and executor messages for that single invocation. Tab-completion suggests the flag.
- **`BiomeCompat` utility** (`ezrtp-common`): reflection-based `safeName(Biome)` and `safeValueOf(String)` helpers that work correctly whether `org.bukkit.block.Biome` is an enum (Spigot/Bukkit ≤ Paper 25) or an interface (Paper 26+), preventing `IncompatibleClassChangeError` at runtime.
- **Movement-cancel during countdown**: if a player moves too far from their starting position while a countdown is running, the teleport is cancelled.
  - `countdown.cancel-on-move` (default `true`) — enable or disable the feature.
  - `countdown.cancel-distance` (default `2.0`) — distance in blocks that triggers cancellation.
  - `countdown.warn-distance` (default `1.0`) — distance in blocks that sends a one-time warning before cancellation. Set to `0` to disable the warning.
  - Two new message keys: `countdown-move-warn` and `countdown-move-cancel` (configurable in `messages/en.yml`).

### Changed

- **Minecraft version support expanded to 1.13+**: `api-version` in `plugin.yml` lowered from `1.21` to `1.13`; plugin will now load on any server from MC 1.13 onwards.
- **Java 17 output bytecode**: `maven.compiler.release` changed from `25` to `17` so the built JARs run on Java 17+ hosts. The build toolchain still requires JDK 25 to compile against `paper-api`.
- **Modrinth `game-versions` broadened to `>=1.13`** in release and nightly workflows (was `>=26.1`).
- **`RareBiomeRegistry.getDefaultRareBiomes()`**: replaced a single try/catch wrapping all `Biome.valueOf()` calls with per-biome `BiomeCompat.safeValueOf()` guards, so a biome absent on the running server version (e.g. `MODIFIED_JUNGLE` removed in 1.18, `DEEP_DARK` added in 1.19) no longer silently prevents the remaining biomes from being registered.
- All `biome.name()` call sites replaced with `BiomeCompat.safeName(biome)` across `RareBiomeRegistry`, `GuiSettings`, `BiomeLocationCache`, `StatsSubcommand`, and `HeatmapSubcommand`.

### Fixed

- Stale `<fork>`, `<executable>`, and `<jvm>` references to deleted `java25.javac` / `java25.java` properties removed from root `pom.xml` compiler and Surefire plugin configuration.

---

## [3.0.2] - 2026-05-09

### Added

- Initial changelog entry. See repository history for prior changes.

---
