package com.skyblockexp.ezrtp.teleport.biome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.skyblockexp.ezrtp.platform.PlatformRuntimeRegistry;
import com.skyblockexp.ezrtp.storage.HotspotStorage;
import com.skyblockexp.ezrtp.util.compat.BiomeCompat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking and managing rare biome hotspots.
 * Maintains a mapping of known rare biome locations to optimize RTP searches.
 * Periodically scans for new rare biome hotspots during idle server times.
 */
public final class RareBiomeRegistry {
    
    private static final int MAX_HOTSPOTS_PER_BIOME = 30;
    private static final int HOTSPOT_RADIUS = 256; // Minimum distance between hotspots
    
    private final JavaPlugin plugin;
    private final Map<String, Map<Biome, List<BiomeHotspot>>> worldHotspots;
    private final Set<Biome> rareBiomes;
    /** Tracks chunk keys (world+coords) already scanned to avoid redundant work. */
    private final Set<Long> scannedChunks;
    private RareBiomeScanListener scanListener;
    private volatile boolean enabled;
    private volatile boolean backgroundScanningEnabled;
    /** Optional persistence backend; null means memory-only. */
    private volatile HotspotStorage storage;

    public RareBiomeRegistry(JavaPlugin plugin, Set<Biome> rareBiomes) {
        this(plugin, rareBiomes, null);
    }

    public RareBiomeRegistry(JavaPlugin plugin, Set<Biome> rareBiomes, HotspotStorage storage) {
        this.plugin = plugin;
        this.worldHotspots = new ConcurrentHashMap<>();
        this.rareBiomes = rareBiomes != null ? new HashSet<>(rareBiomes) : getDefaultRareBiomes();
        this.scannedChunks = ConcurrentHashMap.newKeySet();
        this.storage = storage;
        this.enabled = true;
        this.backgroundScanningEnabled = true;
    }
    
    /**
     * Returns the default set of rare biomes if none are configured.
     */
    private static Set<Biome> getDefaultRareBiomes() {
        Set<Biome> rare = new HashSet<>();
        // Rare biomes that are typically hard to find.
        // Each entry is guarded individually so a biome absent on the running
        // server version (e.g. MODIFIED_JUNGLE removed in 1.18, DEEP_DARK added
        // in 1.19) does not prevent the remaining biomes from being registered.
        for (String name : new String[] {
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
        }) {
            Biome b = BiomeCompat.safeValueOf(name);
            if (b != null) rare.add(b);
        }
        return rare;
    }
    
    /**
     * Enables or disables the registry.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stopBackgroundScanning();
            clear();
        }
    }
    
    /**
     * Returns whether the registry is currently enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Enables or disables background scanning.
     */
    public void setBackgroundScanningEnabled(boolean enabled) {
        this.backgroundScanningEnabled = enabled;
        if (enabled) {
            startBackgroundScanning();
        } else {
            stopBackgroundScanning();
        }
    }
    
    /**
     * Returns whether background scanning is enabled.
     */
    public boolean isBackgroundScanningEnabled() {
        return backgroundScanningEnabled;
    }
    
    /**
     * Checks if a biome is considered rare.
     */
    public boolean isRareBiome(Biome biome) {
        return rareBiomes.contains(biome);
    }
    
    /**
     * Registers a rare biome hotspot location.
     */
    public void registerHotspot(Location location) {
        if (!enabled || location == null || location.getWorld() == null) {
            return;
        }
        
        Biome biome = location.getBlock().getBiome();
        if (!isRareBiome(biome)) {
            return;
        }
        
        String worldName = location.getWorld().getName();
        Map<Biome, List<BiomeHotspot>> hotspots = worldHotspots.computeIfAbsent(
            worldName,
            k -> new ConcurrentHashMap<>()
        );
        
        List<BiomeHotspot> biomeHotspots = hotspots.computeIfAbsent(
            biome,
            k -> new ArrayList<>()
        );

        synchronized (biomeHotspots) {
            // Check if this location is too close to an existing hotspot
            Location finalLocation = location;
            boolean tooClose = biomeHotspots.stream()
                .anyMatch(hs -> hs.location().distanceSquared(finalLocation) < HOTSPOT_RADIUS * HOTSPOT_RADIUS);
            
            if (tooClose) {
                return;
            }
            
            // If we've reached the maximum, remove the oldest hotspot
            if (biomeHotspots.size() >= MAX_HOTSPOTS_PER_BIOME) {
                biomeHotspots.remove(0);
            }
            
            biomeHotspots.add(new BiomeHotspot(location.clone(), System.currentTimeMillis()));

            // Persist asynchronously if a storage backend is configured.
            if (storage != null) {
                final HotspotStorage s = storage;
                final double fx = location.getX(), fy = location.getY(), fz = location.getZ();
                final String wn = worldName;
                final String biomeName = BiomeCompat.safeName(biome);
                PlatformRuntimeRegistry.get().scheduler().executeAsync(
                    () -> s.saveHotspot(wn, biomeName, fx, fy, fz));
            }
        }
    }
    
    /**
     * Retrieves all hotspot locations for a specific rare biome in the given world.
     * Returns an empty list if no hotspots are registered.
     */
    public List<Location> getHotspots(World world, Biome biome) {
        if (!enabled || world == null || !isRareBiome(biome)) {
            return Collections.emptyList();
        }
        
        Map<Biome, List<BiomeHotspot>> hotspots = worldHotspots.get(world.getName());
        if (hotspots == null) {
            return Collections.emptyList();
        }
        
        List<BiomeHotspot> biomeHotspots = hotspots.get(biome);
        if (biomeHotspots == null || biomeHotspots.isEmpty()) {
            return Collections.emptyList();
        }

        // Return a shuffled copy to provide variety; synchronize on the list to guard
        // against concurrent writes in registerHotspot().
        List<Location> shuffled;
        synchronized (biomeHotspots) {
            shuffled = new ArrayList<>(biomeHotspots.size());
            for (BiomeHotspot hs : biomeHotspots) {
                shuffled.add(hs.location());
            }
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }
    
    /**
     * Gets the total count of registered hotspots for a biome.
     */
    public int getHotspotCount(World world, Biome biome) {
        if (!enabled || world == null || !isRareBiome(biome)) {
            return 0;
        }

        Map<Biome, List<BiomeHotspot>> hotspots = worldHotspots.get(world.getName());
        if (hotspots == null) {
            return 0;
        }

        List<BiomeHotspot> biomeHotspots = hotspots.get(biome);
        if (biomeHotspots == null) {
            return 0;
        }
        synchronized (biomeHotspots) {
            return biomeHotspots.size();
        }
    }
    
    /**
     * Starts background scanning for rare biome hotspots.
     *
     * <p>Rather than a repeating timer that redundantly re-scans already-loaded chunks,
     * this registers a {@link ChunkLoadEvent} listener so each chunk is scanned exactly
     * once when it first loads. Eliminates the 5-minute poll and all redundant scans.
     */
    public void startBackgroundScanning() {
        if (!enabled || !backgroundScanningEnabled) {
            return;
        }
        stopBackgroundScanning();
        scanListener = new RareBiomeScanListener();
        Bukkit.getPluginManager().registerEvents(scanListener, plugin);
    }

    /**
     * Stops background scanning by unregistering the chunk-load listener.
     */
    public void stopBackgroundScanning() {
        if (scanListener != null) {
            HandlerList.unregisterAll(scanListener);
            scanListener = null;
        }
    }

    /**
     * Samples a freshly loaded chunk for rare biomes. Called from the main thread
     * inside {@link RareBiomeScanListener#onChunkLoad}.
     */
    private void scanChunk(World world, int chunkX, int chunkZ) {
        if (!enabled || !backgroundScanningEnabled) {
            return;
        }
        // Gate on world environment to avoid scanning Nether/End.
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx = 0; dx < 16; dx += 8) {
            for (int dz = 0; dz < 16; dz += 8) {
                Location loc = world.getHighestBlockAt(baseX + dx, baseZ + dz).getLocation();
                Biome biome = loc.getBlock().getBiome();
                if (isRareBiome(biome)) {
                    registerHotspot(loc);
                }
            }
        }
    }
    
    /**
     * Loads hotspot records from the given storage backend into the in-memory registry.
     * Should be called once at plugin startup (on a background thread is fine) before
     * {@link #startBackgroundScanning()} is invoked.
     *
     * @param hotspotStorage storage to load from; if null the call is a no-op
     */
    public void loadFromStorage(HotspotStorage hotspotStorage) {
        if (hotspotStorage == null) {
            return;
        }
        this.storage = hotspotStorage;
        List<HotspotStorage.HotspotRecord> records = hotspotStorage.loadAll();
        for (HotspotStorage.HotspotRecord rec : records) {
            Biome biome = BiomeCompat.safeValueOf(rec.biome());
            if (biome == null) continue;
            if (!isRareBiome(biome)) {
                continue;
            }
            Map<Biome, List<BiomeHotspot>> hotspots = worldHotspots.computeIfAbsent(
                rec.world(), k -> new ConcurrentHashMap<>());
            List<BiomeHotspot> biomeHotspots = hotspots.computeIfAbsent(
                biome, k -> new ArrayList<>());
            // Use a dummy Location (world may not be loaded yet); callers of getHotspots()
            // resolve locations after worlds are available.
            org.bukkit.World world = Bukkit.getWorld(rec.world());
            if (world == null) {
                continue; // world not loaded; skip rather than create an invalid Location
            }
            Location loc = new Location(world, rec.x(), rec.y(), rec.z());
            synchronized (biomeHotspots) {
                if (biomeHotspots.size() < MAX_HOTSPOTS_PER_BIOME) {
                    biomeHotspots.add(new BiomeHotspot(loc, rec.timestamp()));
                }
            }
        }
    }

    /**
     * Clears all registered hotspots and resets the scanned-chunk tracking set.
     */
    public void clear() {
        worldHotspots.clear();
        scannedChunks.clear();
    }
    
    /**
     * Clears hotspots for a specific world.
     */
    public void clear(String worldName) {
        if (worldName != null) {
            worldHotspots.remove(worldName);
        }
    }
    
    /**
     * Returns statistics about registered hotspots.
     */
    public RegistryStats getStats() {
        int totalHotspots = 0;
        int totalBiomes = 0;
        Map<Biome, Integer> biomeDistribution = new HashMap<>();
        
        for (Map<Biome, List<BiomeHotspot>> worldMap : worldHotspots.values()) {
            totalBiomes += worldMap.size();
            for (Map.Entry<Biome, List<BiomeHotspot>> entry : worldMap.entrySet()) {
                List<BiomeHotspot> list = entry.getValue();
                int count;
                synchronized (list) {
                    count = list.size();
                }
                totalHotspots += count;
                biomeDistribution.merge(entry.getKey(), count, Integer::sum);
            }
        }
        
        return new RegistryStats(
            worldHotspots.size(),
            totalBiomes,
            totalHotspots,
            rareBiomes.size(),
            biomeDistribution
        );
    }
    
    /**
     * Shuts down the registry and cancels all tasks.
     */
    public void shutdown() {
        stopBackgroundScanning();
        clear();
        enabled = false;
    }
    
    /** Represents a registered rare biome hotspot. */
    private record BiomeHotspot(Location location, long timestamp) {}

    /** Listens for chunk loads and scans each chunk exactly once for rare biomes. */
    private final class RareBiomeScanListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onChunkLoad(ChunkLoadEvent event) {
            int cx = event.getChunk().getX();
            int cz = event.getChunk().getZ();
            // Encode world name + chunk coords into a single long key.
            // We mask the world-name hash into the upper 32 bits for a fast identity check;
            // hash collisions between worlds are harmless (worst case: skip a scan in rare scenario).
            long key = ((long) event.getWorld().getName().hashCode() << 32)
                | (((long) cx << 16) & 0xFFFF0000L)
                | ((long) (cz & 0xFFFF));
            if (scannedChunks.add(key)) {
                scanChunk(event.getWorld(), cx, cz);
            }
        }
    }
    
    /**
     * Statistics about the registry state.
     */
    public record RegistryStats(
        int worldCount,
        int biomeCount,
        int hotspotCount,
        int rareBiomeCount,
        Map<Biome, Integer> biomeDistribution
    ) {
        @Override
        public String toString() {
            return String.format("RegistryStats[worlds=%d, biomes=%d, hotspots=%d, rareBiomes=%d]",
                worldCount, biomeCount, hotspotCount, rareBiomeCount);
        }
    }
}
