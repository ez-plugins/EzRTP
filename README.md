# EzRTP

[![Coverage](https://img.shields.io/codecov/c/github/ez-plugins/EzRTP?label=test%20coverage)](https://codecov.io/gh/ez-plugins/EzRTP)
[![License](https://img.shields.io/github/license/ez-plugins/EzRTP)](LICENSE)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ezplugins-ezrtp?label=modrinth%20downloads)](https://modrinth.com/plugin/ezplugins-ezrtp)
[![GitHub Issues](https://img.shields.io/github/issues/ez-plugins/EzRTP)](https://github.com/ez-plugins/EzRTP/issues)

EzRTP is a production-focused random teleport plugin for Minecraft networks.
It is designed for **safety-first teleportation**, **cross-platform compatibility**, and **configuration-driven control** for server owners.

## Download EzRTP on Modrinth

- Modrinth: https://modrinth.com/plugin/ezplugins-ezrtp

## Other resource pages

- SpigotMC: https://www.spigotmc.org/resources/1-19-1-21-%E2%AC%85%EF%B8%8F-ezrtp-%E2%9E%A1%EF%B8%8F-highly-configurable-rtp-plugin-for-minecraft-servers.129828/
- Hangar: https://hangar.papermc.io/EzPlugins/EzRTP
 - GitHub (releases & docs): https://github.com/ez-plugins/EzRTP

---

## Documentation at a Glance

- [Download & Project Pages](#download--project-pages)
- [Features](#key-features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration Files](#configuration-files)
- [Build from Source](#build-from-source)
- [Project Modules](#project-modules)
- [Integrations](#integrations)
- [Additional Documentation Links](#additional-documentation-links)
- [Contributing](#contributing)

---

## Key Features

- **Safe random teleportation** with configurable unsafe blocks and recovery behavior.
- **Per-world and per-group limits** (cooldowns, daily and weekly usage limits).
- **Optional GUI teleport menu** with configurable worlds and biome-targeted destinations.
- **Queue system** to smooth heavy RTP usage on busy servers.
- **Biome-aware and cache-assisted searching** with optional rare-biome optimization.
- **Heatmap/statistics tooling** for operators to inspect RTP distribution and performance.
- **Release update notifier** using free APIs with Modrinth primary and GitHub Releases fallback.
- **WorldGuard region command mode** for region-scoped RTP entry points.
- **TeamsAPI integration** to skip chunk-claimed areas and power faction-claim RTP selection (works with any TeamsAPI-compatible team plugin).
- **Optional first-join/on-join teleport flow**.
- **Optional proxy/network destination menu support** for multi-server setups.
- **Chunky integration** for pre-generation workflows.
- **Graceful fallback behavior** when specific integrations/providers are unavailable.

---

## Compatibility

- **Java:** 17+
- **Server software:** Bukkit, Paper, Spigot, Purpur
- **Plugin API baseline:** 1.13+

### Optional integrations

- Vault (economy hooks)
- [EzEconomy](https://modrinth.com/plugin/ezeconomy)
- [WorldGuard](https://modrinth.com/plugin/worldguard)
- [GriefPrevention](https://modrinth.com/plugin/griefprevention)
- [TeamsAPI](https://github.com/ez-plugins/teams-api) (chunk-claim avoidance via any compatible team plugin)
- [PlaceholderAPI](https://modrinth.com/mod/placeholderapi)
- [Chunky](https://modrinth.com/plugin/chunky/)

> EzRTP is built to run safely even when optional integrations are missing.

---

## Installation

1. Download the EzRTP release jar.
2. Place it in your server's `plugins/` directory.
3. (Recommended) Install optional dependencies you plan to use (Vault, WorldGuard, PlaceholderAPI, etc.).
4. Start or restart the server.
5. Review generated config files in `plugins/EzRTP/` and adjust for your network.

### First-start checklist

- Set your default world and radius in `rtp.yml`.
- Configure cooldown/usage policy in `limits.yml`.
- Enable/disable GUI destinations in `gui.yml`.
- If running a proxy network, configure destinations in `network.yml`.

---

## Commands

### Player commands

- `/rtp` - Random teleport (or opens GUI when enabled).
- `/rtp faction` - Open faction/team claim selection GUI (TeamsAPI), then RTP around the selected claim center.
- `/f rtp` - TeamsAPI faction subcommand route to the same claim selection GUI.

### Admin / utility subcommands

- `/rtp reload` - Reload plugin configuration.
- `/rtp stats` - Show RTP statistics and performance details.
- `/rtp heatmap` - View heatmap information.
- `/rtp fake <amount|clear> [world]` - Inject/clear simulated heatmap points.
- `/rtp heatmap claims-overlay` - Render heatmap with TeamsAPI claim chunk borders (admin-scoped).
- `/rtp fake <amount> claims [world]` - Inject simulated heatmap points constrained to your faction claims.
- `/rtp setcenter <x> <z>` or `/rtp setcenter <world> <x> <z>` - Update RTP center.
- `/rtp pregenerate [world] [radius]` - Trigger Chunky-assisted pre-generation workflow.
- `/forcertp <player> [world]` - Force teleport a target player.

---

## Permissions

### Core

- `ezrtp.use` - Use `/rtp`.
- `ezrtp.forcertp` - Use `/forcertp`.
- `ezrtp.reload` - Use `/rtp reload`.
- `ezrtp.stats` - Access `/rtp stats`.
- `ezrtp.heatmap` - Access `/rtp heatmap`.
- `ezrtp.heatmap.fake` - Access `/rtp fake`.
- `ezrtp.heatmap.claims` - Access claim overlay rendering on heatmaps.
- `ezrtp.queue.bypass` - Bypass queue restrictions.

### Permissions Reference

- `ezrtp.use`: Execute `/rtp` (default: all players)
- `ezrtp.reload`: Reload configuration (default: ops)
- `ezrtp.stats`: View RTP statistics and metrics (default: ops)
- `ezrtp.heatmap`: Generate heatmaps (default: ops)
- `ezrtp.forcertp`: Force RTP for a player (default: ops)
- `ezrtp.heatmap.fake`: Simulate teleports for testing (default: ops)
- `ezrtp.queue.bypass`: Bypass teleport queue (default: false)
- `ezrtp.bypass.cooldown`: Bypass cooldowns (default: false)
- `ezrtp.bypass.limit`: Bypass daily/weekly limits (default: false)
- `ezrtp.gui.*`: Per-option GUI access (configure in `gui.yml`)
- `ezrtp.admin`: Administrative UI/cache visibility and advanced actions (default: ops)

Tip: Add bypass nodes to `rtp-limits.bypass-permissions` in `limits.yml` (or `config.yml` in some deployments) to grant global bypass behavior.

### Configuration-defined/bypass examples

Some permission nodes are configurable in YAML files (for example GUI destination permissions and cooldown/limit bypass permissions in `limits.yml`).

---

## Configuration Files

EzRTP splits configuration into focused files for maintainability:

- `config.yml` - Global/core options and language selection.
- `rtp.yml` - Teleport behavior, safety, biome settings, Chunky integration.
- `limits.yml` - Cooldown and usage limits by world/group.
- `storage.yml` - Usage/cooldown backend (YAML/MySQL).
- `queue.yml` - Queue throttling behavior.
- `gui.yml` - GUI menu layout, world entries, and icons.
- `faction-gui.yml` - Faction claim GUI layout/icons/navigation for `/rtp faction` and `/f rtp`.
- `network.yml` - Proxy/server destination entries.
- `force-rtp.yml` - `/forcertp` command behavior.
- `messages/*.yml` - Localized messages.
  - Supports both top-level keys and `messages.*` nested layout.
  - Missing keys are backfilled automatically on startup without overwriting existing translations.

---

## Build from Source

```bash
# Compile
mvn -q -DskipTests compile

# Run tests
mvn -q test
```

### Build requirements

- Java 17
- Maven 3.8+

---

## Project Modules

EzRTP uses a multi-module Maven layout:

- `ezrtp-common/` - Shared logic and abstractions.
- `ezrtp-bukkit/` - Bukkit runtime packaging.
- `ezrtp-paper/` - Paper-specific adapters/services.
- `ezrtp-spigot/` - Spigot-specific module.
- `ezrtp-purpur/` - Purpur module built with Paper compatibility behavior.

---

## Integrations

EzRTP is designed with integration-friendly architecture:

- Economy hooks for RTP costs.
- Protection-provider checks (claims/regions).
- Placeholder-aware messaging.
- Optional network destination presentation.
- Optional Chunky pre-generation orchestration.

If an integration is absent, EzRTP prefers safe fallback behavior rather than hard failure.

---

## Additional Documentation Links

Configuration and message references in this repository:

- Core config: [`config.yml`](src/main/resources/config.yml)
- RTP behavior: [`rtp.yml`](src/main/resources/rtp.yml)
- Limits and cooldowns: [`limits.yml`](src/main/resources/limits.yml)
- Queue settings: [`queue.yml`](src/main/resources/queue.yml)
- GUI settings: [`gui.yml`](src/main/resources/gui.yml)
- Faction GUI settings: [`faction-gui.yml`](src/main/resources/faction-gui.yml)
- Network/proxy settings: [`network.yml`](src/main/resources/network.yml)
- Storage backend config: [`storage.yml`](src/main/resources/storage.yml)
- Force RTP behavior: [`force-rtp.yml`](src/main/resources/force-rtp.yml)
- Default messages: [`messages/en.yml`](src/main/resources/messages/en.yml)
- Bukkit plugin descriptor: [`plugin.yml`](src/main/resources/plugin.yml)

Configuration documentation files:

- Main config documentation: [`docs/config/config.md`](docs/config/config.md)
- Core configuration reference (`config.yml`, `rtp.yml`, `limits.yml`, `storage.yml`, `force-rtp.yml`): [`docs/config-core-reference.md`](docs/config-core-reference.md)
- Faction GUI config reference: [`docs/config/faction-gui.md`](docs/config/faction-gui.md)
- GUI/queue/network reference (`gui.yml`, `queue.yml`, `network.yml`): [`docs/config-gui-queue-network-reference.md`](docs/config-gui-queue-network-reference.md)

- Integration docs:
  - [`docs/integrations/vault-economy.md`](docs/integrations/vault-economy.md)
  - [`docs/integrations/protection-worldguard-griefprevention.md`](docs/integrations/protection-worldguard-griefprevention.md)
  - [`docs/integrations/placeholderapi.md`](docs/integrations/placeholderapi.md)
  - [`docs/integrations/chunky.md`](docs/integrations/chunky.md)
  - [`docs/integrations/network-proxy.md`](docs/integrations/network-proxy.md)

Module-level documentation and build descriptors:

- Parent build: [`pom.xml`](pom.xml)
- Common module: [`ezrtp-common/pom.xml`](ezrtp-common/pom.xml)
- Bukkit module: [`ezrtp-bukkit/pom.xml`](ezrtp-bukkit/pom.xml)
- Paper module: [`ezrtp-paper/pom.xml`](ezrtp-paper/pom.xml)
- Spigot module: [`ezrtp-spigot/pom.xml`](ezrtp-spigot/pom.xml)
- Purpur module: [`ezrtp-purpur/pom.xml`](ezrtp-purpur/pom.xml)

---

## Contributing

Contributions are welcome.

Suggested local verification flow:

1. `mvn -q -DskipTests compile`
2. `mvn -q test`
3. Include tests for non-trivial behavior changes when practical.
4. Keep changes focused and document any config/message migration impacts.

---

## Support

If you run into issues, please open a GitHub issue with:

- Server platform and version (Paper/Spigot/Purpur/Bukkit)
- Java version
- EzRTP version
- Relevant config snippets and logs

This helps reproduce problems quickly and keeps support requests actionable.

---

## License

EzRTP is licensed under the **MIT License**. See [`LICENSE`](LICENSE) for the full text.
