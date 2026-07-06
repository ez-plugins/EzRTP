package com.skyblockexp.ezrtp.config.biome;

import com.skyblockexp.ezrtp.util.compat.BiomeCompat;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration settings for rare biome optimization features.
 * Includes settings for hotspot tracking, weighted searches, and chunk load management.
 */
public final class RareBiomeOptimizationSettings {
    private static final RareBiomeOptimizationSettings DISABLED_INSTANCE =
        new RareBiomeOptimizationSettings(false, Collections.emptySet(), true,
            true, false, 20, 8, true, 2, 3L, true, "none", "", "", "");

    private final boolean enabled;
    private final Set<Biome> rareBiomes;
    private final boolean useWeightedSearch;
    private final boolean enableHotspotTracking;
    private final boolean enableBackgroundScanning;
    private final int maxHotspotsPerBiome;
    private final int hotspotScanIntervalMinutes;
    private final boolean useChunkLoadQueue;
    private final int maxChunksPerTick;
    private final long chunkProcessingIntervalTicks;
    private final boolean autoEnableForFilters;
    // Persistence backend config (backend: none | mysql)
    private final String persistenceBackend;
    private final String persistenceMysqlUrl;
    private final String persistenceMysqlUser;
    private final String persistenceMysqlPassword;

    public RareBiomeOptimizationSettings(boolean enabled, Set<Biome> rareBiomes,
                                        boolean useWeightedSearch, boolean enableHotspotTracking,
                                        boolean enableBackgroundScanning, int maxHotspotsPerBiome,
                                        int hotspotScanIntervalMinutes, boolean useChunkLoadQueue,
                                        int maxChunksPerTick, long chunkProcessingIntervalTicks,
                                        boolean autoEnableForFilters,
                                        String persistenceBackend, String persistenceMysqlUrl,
                                        String persistenceMysqlUser, String persistenceMysqlPassword) {
        this.enabled = enabled;
        this.rareBiomes = rareBiomes != null ? new HashSet<>(rareBiomes) : Collections.emptySet();
        this.useWeightedSearch = useWeightedSearch;
        this.enableHotspotTracking = enableHotspotTracking;
        this.enableBackgroundScanning = enableBackgroundScanning;
        this.maxHotspotsPerBiome = Math.max(1, maxHotspotsPerBiome);
        this.hotspotScanIntervalMinutes = Math.max(1, hotspotScanIntervalMinutes);
        this.useChunkLoadQueue = useChunkLoadQueue;
        this.maxChunksPerTick = Math.max(1, maxChunksPerTick);
        this.chunkProcessingIntervalTicks = Math.max(1L, chunkProcessingIntervalTicks);
        this.autoEnableForFilters = autoEnableForFilters;
        this.persistenceBackend = persistenceBackend != null ? persistenceBackend : "none";
        this.persistenceMysqlUrl = persistenceMysqlUrl != null ? persistenceMysqlUrl : "";
        this.persistenceMysqlUser = persistenceMysqlUser != null ? persistenceMysqlUser : "";
        this.persistenceMysqlPassword = persistenceMysqlPassword != null ? persistenceMysqlPassword : "";
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public Set<Biome> getRareBiomes() {
        return Collections.unmodifiableSet(rareBiomes);
    }
    
    public boolean useWeightedSearch() {
        return useWeightedSearch;
    }
    
    public boolean isHotspotTrackingEnabled() {
        return enableHotspotTracking;
    }
    
    public boolean isBackgroundScanningEnabled() {
        return enableBackgroundScanning;
    }
    
    public int getMaxHotspotsPerBiome() {
        return maxHotspotsPerBiome;
    }
    
    public int getHotspotScanIntervalMinutes() {
        return hotspotScanIntervalMinutes;
    }
    
    public boolean useChunkLoadQueue() {
        return useChunkLoadQueue;
    }
    
    public int getMaxChunksPerTick() {
        return maxChunksPerTick;
    }

    public long getChunkProcessingIntervalTicks() {
        return chunkProcessingIntervalTicks;
    }

    public boolean isAutoEnableForFilters() {
        return autoEnableForFilters;
    }

    public String getPersistenceBackend() {
        return persistenceBackend;
    }

    public String getPersistenceMysqlUrl() {
        return persistenceMysqlUrl;
    }

    public String getPersistenceMysqlUser() {
        return persistenceMysqlUser;
    }

    public String getPersistenceMysqlPassword() {
        return persistenceMysqlPassword;
    }
    
    public static RareBiomeOptimizationSettings disabled() {
        return DISABLED_INSTANCE;
    }
    
    public static RareBiomeOptimizationSettings fromConfiguration(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        
        boolean enabled = section.getBoolean("enabled", false);
        
        // Parse rare biomes list
        Set<Biome> rareBiomes = new HashSet<>();
        List<String> biomeNames = section.getStringList("rare-biomes");
        if (biomeNames.isEmpty()) {
            // Use defaults if not specified
            rareBiomes = getDefaultRareBiomes();
        } else {
            for (String biomeName : biomeNames) {
                Biome b = BiomeCompat.safeValueOf(biomeName);
                if (b != null) rareBiomes.add(b);
            }
        }
        
        boolean useWeightedSearch = section.getBoolean("use-weighted-search", true);
        boolean enableHotspotTracking = section.getBoolean("enable-hotspot-tracking", true);
        boolean enableBackgroundScanning = section.getBoolean("enable-background-scanning", false);
        int maxHotspotsPerBiome = section.getInt("max-hotspots-per-biome", 20);
        int hotspotScanIntervalMinutes = section.getInt("hotspot-scan-interval-minutes", 8);
        boolean useChunkLoadQueue = section.getBoolean("use-chunk-load-queue", true);
        int maxChunksPerTick = section.getInt("max-chunks-per-tick", 2);
        long chunkProcessingIntervalTicks = section.getLong("chunk-load-interval-ticks", 3L);
        boolean autoEnableForFilters = section.getBoolean("auto-enable-for-filters", true);

        ConfigurationSection persist = section.getConfigurationSection("persistence");
        String persistenceBackend = persist != null ? persist.getString("backend", "none") : "none";
        String persistenceMysqlUrl = persist != null ? persist.getString("mysql-url", "") : "";
        String persistenceMysqlUser = persist != null ? persist.getString("mysql-user", "") : "";
        String persistenceMysqlPassword = persist != null ? persist.getString("mysql-password", "") : "";

        return new RareBiomeOptimizationSettings(enabled, rareBiomes, useWeightedSearch,
            enableHotspotTracking, enableBackgroundScanning, maxHotspotsPerBiome,
            hotspotScanIntervalMinutes, useChunkLoadQueue, maxChunksPerTick,
            chunkProcessingIntervalTicks, autoEnableForFilters,
            persistenceBackend, persistenceMysqlUrl, persistenceMysqlUser, persistenceMysqlPassword);
    }
    
    /**
     * Returns the default set of rare biomes.
     */
    private static Set<Biome> getDefaultRareBiomes() {
        Set<Biome> rare = new HashSet<>();
        String[] defaultRareBiomes = {
            "MUSHROOM_FIELDS",
            "JUNGLE",
            "BAMBOO_JUNGLE",
            "BADLANDS",
            "ERODED_BADLANDS",
            "WOODED_BADLANDS",
            "ICE_SPIKES",
            "SUNFLOWER_PLAINS",
            "FLOWER_FOREST",
            "MODIFIED_JUNGLE",
            "MODIFIED_JUNGLE_EDGE",
            "DEEP_DARK",
            "SULFUR_CAVES"
        };
        
        for (String biomeName : defaultRareBiomes) {
            Biome b = BiomeCompat.safeValueOf(biomeName);
            if (b != null) rare.add(b);
        }
        
        return rare;
    }
}
