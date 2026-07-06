package com.skyblockexp.ezrtp.command.subcommands;

import com.skyblockexp.ezrtp.EzRtpPlugin;
import com.skyblockexp.ezrtp.config.EzRtpConfiguration;
import com.skyblockexp.ezrtp.config.RandomTeleportSettings;
import com.skyblockexp.ezrtp.config.teleport.SearchPattern;
import com.skyblockexp.ezrtp.teleport.RandomTeleportService;
import com.skyblockexp.ezrtp.teleport.biome.BiomeLocationCache;
import com.skyblockexp.ezrtp.teleport.search.BiomeSearchStrategy;
import com.skyblockexp.ezrtp.teleport.search.CircularSearchStrategy;
import com.skyblockexp.ezrtp.teleport.search.SquareSearchStrategy;
import com.skyblockexp.ezrtp.teleport.search.UniformSearchStrategy;
import com.skyblockexp.ezrtp.teleport.heatmap.HeatmapGenerator;
import com.skyblockexp.ezrtp.teleport.heatmap.HeatmapMapService;
import com.skyblockexp.ezrtp.teleport.heatmap.HeatmapSimulationStore;
import com.skyblockexp.ezrtp.teleport.heatmap.ClaimChunkOverlay;
import com.skyblockexp.ezrtp.teleport.heatmap.ClaimOverlaySettings;
import com.skyblockexp.ezrtp.util.compat.BiomeCompat;
import com.skyblockexp.teamsapi.api.TeamsAPI;
import com.skyblockexp.teamsapi.api.TeamsClaimService;
import com.skyblockexp.teamsapi.api.TeamsService;
import com.skyblockexp.teamsapi.model.Team;
import com.skyblockexp.teamsapi.model.TeamClaim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles the /rtp heatmap subcommand and its variants.
 */
public class HeatmapSubcommand extends Subcommand {

    private static final String SAVE_SUBCOMMAND = "save";
    private static final String CLAIMS_OVERLAY_FLAG = "claims-overlay";
    private static final int DEFAULT_GRID_SIZE = 512;

    private final EzRtpPlugin plugin;
    private final Supplier<RandomTeleportService> teleportServiceSupplier;
    private final Supplier<EzRtpConfiguration> configurationSupplier;
    private final HeatmapSimulationStore heatmapSimulationStore;

    public HeatmapSubcommand(EzRtpPlugin plugin,
                            Supplier<RandomTeleportService> teleportServiceSupplier,
                            Supplier<EzRtpConfiguration> configurationSupplier,
                            HeatmapSimulationStore heatmapSimulationStore) {
        super("heatmap", "ezrtp.heatmap");
        this.plugin = plugin;
        this.teleportServiceSupplier = teleportServiceSupplier;
        this.configurationSupplier = configurationSupplier;
        this.heatmapSimulationStore = heatmapSimulationStore;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command may only be used by players.");
            return true;
        }

        // Block early when heatmap feature is disabled in config
        EzRtpConfiguration configuration = configurationSupplier.get();
        RandomTeleportSettings rtpSettings = configuration != null
                ? configuration.getSettingsForWorld(player.getWorld().getName())
                : null;
        if (rtpSettings != null && !rtpSettings.isHeatmapEnabled()) {
            plugin.getLogger().warning(
                    "EzRTP: Heatmap command invoked by " + player.getName()
                    + " but heatmap.enabled is false in rtp.yml. Enable it to use heatmap features.");
            com.skyblockexp.ezrtp.util.MessageUtil.send(player,
                    plugin.getMessageProvider().format(com.skyblockexp.ezrtp.message.MessageKey.HEATMAP_DISABLED, player));
            return true;
        }

        boolean overlayRequested = false;
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (CLAIMS_OVERLAY_FLAG.equalsIgnoreCase(arg)) {
                overlayRequested = true;
            } else {
                filteredArgs.add(arg);
            }
        }
        args = filteredArgs.toArray(new String[0]);

        // Check for "save" subcommand
        if (args.length > 0 && SAVE_SUBCOMMAND.equalsIgnoreCase(args[0])) {
            handleHeatmapSave(player, overlayRequested);
            return true;
        }

        // Check for biome parameter
        Biome targetBiome = null;
        if (args.length > 0) {
            targetBiome = BiomeCompat.safeValueOf(args[0]);
            if (targetBiome == null) {
                sender.sendMessage("§cInvalid biome: " + args[0]);
                return true;
            }
        }

        handleHeatmapGeneration(player, targetBiome, overlayRequested);
        return true;
    }

    @Override
    @NotNull
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add(SAVE_SUBCOMMAND);
            suggestions.add(CLAIMS_OVERLAY_FLAG);
            // Add biome names
            for (Biome biome : org.bukkit.Registry.BIOME) {
                suggestions.add(BiomeCompat.safeName(biome).toLowerCase());
            }
            return suggestions;
        }

        return Collections.emptyList();
    }

    /**
     * Handles heatmap generation and provides the player with a map item.
     */
    private void handleHeatmapGeneration(Player player, Biome targetBiome, boolean overlayRequested) {
        RandomTeleportService service = teleportServiceSupplier.get();
        if (service == null) {
            com.skyblockexp.ezrtp.util.MessageUtil.send(player, plugin.getMessageProvider().format(com.skyblockexp.ezrtp.message.MessageKey.COMMAND_SERVICE_NOT_INITIALIZED, player));
            return;
        }

        BiomeLocationCache cache = service.getBiomeCache();
        if (targetBiome != null && (cache == null || !cache.isEnabled())) {
            com.skyblockexp.ezrtp.util.MessageUtil.send(player,
                    plugin.getMessageProvider().format(com.skyblockexp.ezrtp.message.MessageKey.HEATMAP_BIOME_CACHING_DISABLED, player));
            return;
        }

        String worldName = player.getWorld().getName();

        HeatmapSampleSet sampleSet = collectHeatmapSamples(cache, worldName, targetBiome);
        List<Location> locations = sampleSet.samples();

        // Generate heatmap data
        HeatmapGenerator generator = new HeatmapGenerator(16);
        HeatmapGenerator.HeatmapData heatmap = generator.generate(locations);
        // Get RTP settings for center and radius
        EzRtpConfiguration configuration = configurationSupplier.get();
        RandomTeleportSettings settings = configuration != null ? configuration.getSettingsForWorld(player.getWorld().getName()) : null;
        int centerX = settings != null ? settings.getCenterX() : 0;
        int centerZ = settings != null ? settings.getCenterZ() : 0;
        int radius = settings != null ? settings.getMaximumRadius() : 1000;
        // Check if we have enough data
        HeatmapMapService mapService = new HeatmapMapService(plugin.getLogger());
        if (!mapService.hasEnoughData(heatmap)) {
            player.sendMessage("§cNot enough RTP data to generate a meaningful heatmap.");
            player.sendMessage("§7Try using /rtp fake to add simulated data for testing.");
            return;
        }
        // Generate and give the map to the player
        ClaimOverlaySettings overlaySettings = resolveOverlaySettings(configuration);
        List<ClaimChunkOverlay> overlayClaims = resolveOverlayClaims(player, worldName, overlayRequested, overlaySettings);
        ItemStack mapItem = mapService.createHeatmapMap(heatmap, player, centerX, centerZ, radius, overlayClaims, overlaySettings);
        if (mapItem == null) {
            player.sendMessage("§cFailed to generate heatmap map. Check console for errors.");
            return;
        }

        // Add to player's inventory
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cYour inventory is full! Clear a slot and try again.");
            return;
        } else {
            player.getInventory().addItem(mapItem);
        }

        // Display statistics in chat
        player.sendMessage("§6§l═══════════════════════════════════════");
        player.sendMessage("§a§lHeatmap successfully generated and added to your inventory!");
        player.sendMessage("§6§l═══════════════════════════════════════");
        player.sendMessage("");

        if (targetBiome != null) {
            player.sendMessage("§6Biome Filter: §f" + formatBiomeName(targetBiome));
            player.sendMessage("§6Locations: §f" + heatmap.getTotalLocations());
        } else {
            player.sendMessage("§6World: §f" + worldName);
            player.sendMessage("§6Total RTP Locations: §f" + heatmap.getTotalLocations());
        }

        if (sampleSet.simulatedCount() > 0) {
            player.sendMessage("§6Simulated Samples: §f" + sampleSet.simulatedCount());
        }
        if (!overlayClaims.isEmpty()) {
            player.sendMessage("§6Claim Overlay Chunks: §f" + overlayClaims.size());
        }

        player.sendMessage("§6Grid Size: §f" + heatmap.getGridSize() + " blocks");
        player.sendMessage("");
        player.sendMessage("§6§l═══════════════════════════════════════");
    }

    /**
     * Handles saving a heatmap as a PNG file.
     */
    private void handleHeatmapSave(Player player, boolean overlayRequested) {
        RandomTeleportService service = teleportServiceSupplier.get();
        if (service == null) {
            com.skyblockexp.ezrtp.util.MessageUtil.send(player, plugin.getMessageProvider().format(com.skyblockexp.ezrtp.message.MessageKey.COMMAND_SERVICE_NOT_INITIALIZED, player));
            return;
        }

        BiomeLocationCache cache = service.getBiomeCache();

        String worldName = player.getWorld().getName();
        HeatmapSampleSet sampleSet = collectHeatmapSamples(cache, worldName, null);
        List<Location> locations = sampleSet.samples();

        // Generate heatmap data
        HeatmapGenerator generator = new HeatmapGenerator(DEFAULT_GRID_SIZE);
        HeatmapGenerator.HeatmapData heatmap = generator.generate(locations);

        // Check if we have enough data
        HeatmapMapService mapService = new HeatmapMapService(plugin.getLogger());
        if (!mapService.hasEnoughData(heatmap)) {
            player.sendMessage("§cNot enough RTP data to generate a meaningful heatmap.");
            player.sendMessage("§7Try using /rtp fake to add simulated data for testing.");
            return;
        }

        // Save the heatmap as PNG
        String timestamp = String.format("%tY%<tm%<td_%<tH%<tM%<tS", System.currentTimeMillis());
        String fileName = "heatmap_" + worldName + "_" + timestamp + ".png";
        File outputFile = new File(plugin.getDataFolder(), "heatmaps/" + fileName);
        // Get RTP settings for center and radius
        EzRtpConfiguration configuration = configurationSupplier.get();
        RandomTeleportSettings settings = configuration != null ? configuration.getSettingsForWorld(worldName) : null;
        int centerX = settings != null ? settings.getCenterX() : 0;
        int centerZ = settings != null ? settings.getCenterZ() : 0;
        int radius = settings != null ? settings.getMaximumRadius() : 1000;
        ClaimOverlaySettings overlaySettings = resolveOverlaySettings(configuration);
        List<ClaimChunkOverlay> overlayClaims = resolveOverlayClaims(player, worldName, overlayRequested, overlaySettings);
        if (mapService.saveHeatmapAsPng(heatmap, outputFile, centerX, centerZ, radius, overlayClaims, overlaySettings)) {
            player.sendMessage("§aHeatmap saved successfully!");
            player.sendMessage("§7File: §f" + fileName);
            player.sendMessage("§7Location: §fplugins/EzRTP/heatmaps/");
        } else {
            player.sendMessage("§cFailed to save heatmap. Check console for errors.");
        }
    }

    /**
     * Formats a biome name for display.
     */
    private String formatBiomeName(Biome biome) {
        String name = BiomeCompat.safeName(biome).replace("_", " ");
        String[] words = name.toLowerCase().split(" ");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) formatted.append(" ");
            formatted.append(words[i].substring(0, 1).toUpperCase())
                    .append(words[i].substring(1));
        }

        return formatted.toString();
    }

    private HeatmapSampleSet collectHeatmapSamples(BiomeLocationCache cache, String worldName, Biome biome) {
        if (worldName == null) {
            return new HeatmapSampleSet(Collections.emptyList(), 0, 0);
        }
        List<Location> cached = Collections.emptyList();
        if (cache != null && cache.isEnabled()) {
            cached = biome != null
                    ? cache.getLocations(worldName, biome)
                    : cache.getAllLocations(worldName);
        }
        List<Location> combined = cached != null
            ? new ArrayList<>(cached)
            : new ArrayList<>();
        int simulatedCount = 0;
        if (biome == null && heatmapSimulationStore != null) {
            List<Location> simulated = heatmapSimulationStore.getSamples(worldName);
            if (simulated != null) {
                combined.addAll(simulated);
                simulatedCount = simulated.size();
            }
        }
        int cachedCount = cached != null ? cached.size() : 0;
        return new HeatmapSampleSet(combined, cachedCount, simulatedCount);
    }

    private record HeatmapSampleSet(List<Location> samples, int cachedCount, int simulatedCount) {
        private HeatmapSampleSet {
            samples = samples != null ? samples : Collections.emptyList();
        }
    }

    private ClaimOverlaySettings resolveOverlaySettings(EzRtpConfiguration configuration) {
        if (configuration == null || configuration.getDefaultSettings() == null
                || configuration.getDefaultSettings().getConfigSection() == null
                || configuration.getDefaultSettings().getConfigSection().getRoot() == null) {
            return ClaimOverlaySettings.defaults();
        }
        return ClaimOverlaySettings.fromConfiguration(configuration.getDefaultSettings().getConfigSection().getRoot());
    }

    private List<ClaimChunkOverlay> resolveOverlayClaims(Player player,
                                                         String worldName,
                                                         boolean overlayRequested,
                                                         ClaimOverlaySettings settings) {
        if (!(overlayRequested || settings.enabled())) {
            return Collections.emptyList();
        }
        boolean hasAdminPermission = player.hasPermission("ezrtp.heatmap.claims") || player.isOp();
        if (!hasAdminPermission) {
            return Collections.emptyList();
        }
        if (!TeamsAPI.isAvailable() || !TeamsAPI.isClaimAvailable()) {
            return Collections.emptyList();
        }
        TeamsService teamsService = TeamsAPI.getService();
        TeamsClaimService claimService = TeamsAPI.getClaimService();
        if (teamsService == null || claimService == null) {
            return Collections.emptyList();
        }
        List<ClaimChunkOverlay> result = new ArrayList<>();
        for (Team team : teamsService.getAllTeams()) {
            for (TeamClaim claim : claimService.getTeamClaims(team.getId())) {
                if (worldName.equals(claim.getWorldName())) {
                    result.add(new ClaimChunkOverlay(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ()));
                }
            }
        }
        return result;
    }
}
