package com.skyblockexp.ezrtp.util.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

/**
 * Compatibility helpers for {@link Biome} API differences across server versions.
 *
 * <p>In Paper 1.21+ the {@code Biome} type changed from an enum to an interface.
 * Any direct {@code biome.name()} call compiled against the interface variant emits
 * an {@code INVOKEINTERFACE} bytecode instruction that throws
 * {@code IncompatibleClassChangeError} at runtime on older Bukkit/Spigot where
 * {@code Biome} is still an enum.  Routing all name lookups through
 * {@link #safeName(Biome)} avoids the incompatible instruction by using reflection.
 */
public final class BiomeCompat {

    private BiomeCompat() {}

    /**
     * Returns the name of the given biome in a server-version-safe way.
     *
     * <p>Works on both enum (pre-1.21) and interface (1.21+) representations of
     * {@link Biome}.  Falls back to {@link Object#toString()} if reflection fails.
     *
     * @param biome the biome; must not be null
     * @return the uppercase constant name, e.g. {@code "PLAINS"}
     */
    public static String safeName(Biome biome) {
        try {
            Object result = biome.getClass().getMethod("name").invoke(biome);
            if (result instanceof String s) {
                return s;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // fall through
        }
        return biome.toString();
    }

    /**
     * Parses a biome name string into a {@link Biome} constant, returning
     * {@code null} if the name is not recognised on the running server version.
     *
     * @param name the biome name (case-insensitive)
     * @return the matching {@link Biome}, or {@code null} if not found
     */
    public static Biome safeValueOf(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Registry.BIOME.get(NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
    }
}