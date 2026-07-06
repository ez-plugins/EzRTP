package org.bukkit.block;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Test shim for org.bukkit.block.Biome.
 * Replaces the registry-backed Paper 26.1 interface to avoid requiring a running
 * server (RegistryAccess) in unit tests. Must be an interface (not an enum) so that
 * bytecode compiled against the real Paper Biome interface resolves correctly.
 */
public interface Biome extends Keyed {

  Biome BADLANDS = BiomeShim.of("BADLANDS");
  Biome BAMBOO_JUNGLE = BiomeShim.of("BAMBOO_JUNGLE");
  Biome BASALT_DELTAS = BiomeShim.of("BASALT_DELTAS");
  Biome BEACH = BiomeShim.of("BEACH");
  Biome BIRCH_FOREST = BiomeShim.of("BIRCH_FOREST");
  Biome CHERRY_GROVE = BiomeShim.of("CHERRY_GROVE");
  Biome COLD_OCEAN = BiomeShim.of("COLD_OCEAN");
  Biome CRIMSON_FOREST = BiomeShim.of("CRIMSON_FOREST");
  Biome DARK_FOREST = BiomeShim.of("DARK_FOREST");
  Biome DEEP_COLD_OCEAN = BiomeShim.of("DEEP_COLD_OCEAN");
  Biome DEEP_DARK = BiomeShim.of("DEEP_DARK");
  Biome DEEP_FROZEN_OCEAN = BiomeShim.of("DEEP_FROZEN_OCEAN");
  Biome DEEP_LUKEWARM_OCEAN = BiomeShim.of("DEEP_LUKEWARM_OCEAN");
  Biome DEEP_OCEAN = BiomeShim.of("DEEP_OCEAN");
  Biome DESERT = BiomeShim.of("DESERT");
  Biome DRIPSTONE_CAVES = BiomeShim.of("DRIPSTONE_CAVES");
  Biome END_BARRENS = BiomeShim.of("END_BARRENS");
  Biome END_HIGHLANDS = BiomeShim.of("END_HIGHLANDS");
  Biome END_MIDLANDS = BiomeShim.of("END_MIDLANDS");
  Biome ERODED_BADLANDS = BiomeShim.of("ERODED_BADLANDS");
  Biome FLOWER_FOREST = BiomeShim.of("FLOWER_FOREST");
  Biome FOREST = BiomeShim.of("FOREST");
  Biome FROZEN_OCEAN = BiomeShim.of("FROZEN_OCEAN");
  Biome FROZEN_PEAKS = BiomeShim.of("FROZEN_PEAKS");
  Biome FROZEN_RIVER = BiomeShim.of("FROZEN_RIVER");
  Biome GROVE = BiomeShim.of("GROVE");
  Biome ICE_SPIKES = BiomeShim.of("ICE_SPIKES");
  Biome JAGGED_PEAKS = BiomeShim.of("JAGGED_PEAKS");
  Biome JUNGLE = BiomeShim.of("JUNGLE");
  Biome LUKEWARM_OCEAN = BiomeShim.of("LUKEWARM_OCEAN");
  Biome LUSH_CAVES = BiomeShim.of("LUSH_CAVES");
  Biome MANGROVE_SWAMP = BiomeShim.of("MANGROVE_SWAMP");
  Biome MEADOW = BiomeShim.of("MEADOW");
  Biome MUSHROOM_FIELDS = BiomeShim.of("MUSHROOM_FIELDS");
  Biome NETHER_WASTES = BiomeShim.of("NETHER_WASTES");
  Biome OCEAN = BiomeShim.of("OCEAN");
  Biome OLD_GROWTH_BIRCH_FOREST = BiomeShim.of("OLD_GROWTH_BIRCH_FOREST");
  Biome OLD_GROWTH_PINE_TAIGA = BiomeShim.of("OLD_GROWTH_PINE_TAIGA");
  Biome OLD_GROWTH_SPRUCE_TAIGA = BiomeShim.of("OLD_GROWTH_SPRUCE_TAIGA");
  Biome PALE_GARDEN = BiomeShim.of("PALE_GARDEN");
  Biome PLAINS = BiomeShim.of("PLAINS");
  Biome RIVER = BiomeShim.of("RIVER");
  Biome SAVANNA = BiomeShim.of("SAVANNA");
  Biome SAVANNA_PLATEAU = BiomeShim.of("SAVANNA_PLATEAU");
  Biome SMALL_END_ISLANDS = BiomeShim.of("SMALL_END_ISLANDS");
  Biome SNOWY_BEACH = BiomeShim.of("SNOWY_BEACH");
  Biome SNOWY_PLAINS = BiomeShim.of("SNOWY_PLAINS");
  Biome SNOWY_SLOPES = BiomeShim.of("SNOWY_SLOPES");
  Biome SNOWY_TAIGA = BiomeShim.of("SNOWY_TAIGA");
  Biome SOUL_SAND_VALLEY = BiomeShim.of("SOUL_SAND_VALLEY");
  Biome SPARSE_JUNGLE = BiomeShim.of("SPARSE_JUNGLE");
  Biome STONY_PEAKS = BiomeShim.of("STONY_PEAKS");
  Biome STONY_SHORE = BiomeShim.of("STONY_SHORE");
  Biome SULFUR_CAVES = BiomeShim.of("SULFUR_CAVES");
  Biome SUNFLOWER_PLAINS = BiomeShim.of("SUNFLOWER_PLAINS");
  Biome SWAMP = BiomeShim.of("SWAMP");
  Biome TAIGA = BiomeShim.of("TAIGA");
  Biome THE_END = BiomeShim.of("THE_END");
  Biome THE_VOID = BiomeShim.of("THE_VOID");
  Biome WARM_OCEAN = BiomeShim.of("WARM_OCEAN");
  Biome WARPED_FOREST = BiomeShim.of("WARPED_FOREST");
  Biome WINDSWEPT_FOREST = BiomeShim.of("WINDSWEPT_FOREST");
  Biome WINDSWEPT_GRAVELLY_HILLS = BiomeShim.of("WINDSWEPT_GRAVELLY_HILLS");
  Biome WINDSWEPT_HILLS = BiomeShim.of("WINDSWEPT_HILLS");
  Biome WINDSWEPT_SAVANNA = BiomeShim.of("WINDSWEPT_SAVANNA");
  Biome WOODED_BADLANDS = BiomeShim.of("WOODED_BADLANDS");
  Biome CUSTOM = BiomeShim.of("CUSTOM");

  /** Returns the constant name (mirrors Enum.name() / OldEnum behaviour). */
  String name();

  static Biome valueOf(String name) {
    if (name == null) {
      return null;
    }
    Biome biome = BiomeShim.REGISTRY.get(name.toUpperCase(Locale.ROOT));
    if (biome == null) {
      throw new IllegalArgumentException("No Biome constant: " + name);
    }
    return biome;
  }

  static Biome[] values() {
    return BiomeShim.REGISTRY.values().toArray(new Biome[0]);
  }

  /** Concrete backing class for test Biome constants. */
  final class BiomeShim implements Biome {
    static final Map<String, Biome> REGISTRY = new HashMap<>();

    private final String name;

    private BiomeShim(String name) {
      this.name = name;
    }

    static BiomeShim of(String n) {
      BiomeShim b = new BiomeShim(n.toUpperCase(Locale.ROOT));
      REGISTRY.put(b.name, b);
      return b;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public NamespacedKey getKey() {
      return NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o instanceof BiomeShim) return name.equals(((BiomeShim) o).name);
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }
}
