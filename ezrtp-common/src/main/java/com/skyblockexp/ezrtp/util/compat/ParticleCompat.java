package com.skyblockexp.ezrtp.util.compat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Reflection-based particle spawner to adapt across server versions.
 */
public final class ParticleCompat {

    private ParticleCompat() {}

    public static void spawnParticle(World world, Particle particle, Location loc, int count,
                                     double offsetX, double offsetY, double offsetZ,
                                     double extra, Object data, boolean force) {
        if (world == null || particle == null || loc == null) return;
        try {
            // Try the modern signature: spawnParticle(Particle, Location, int, double, double, double, double, Object, boolean)
            Method m = world.getClass().getMethod("spawnParticle", Particle.class, Location.class, int.class,
                    double.class, double.class, double.class, double.class, Object.class, boolean.class);
            m.invoke(world, particle, loc, count, offsetX, offsetY, offsetZ, extra, data, force);
            return;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}

        try {
            // Try alternate signature: spawnParticle(Particle, Location, int, double, double, double, double)
            Method m2 = world.getClass().getMethod("spawnParticle", Particle.class, Location.class, int.class,
                    double.class, double.class, double.class, double.class);
            m2.invoke(world, particle, loc, count, offsetX, offsetY, offsetZ, extra);
            return;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}

        try {
            // Legacy: spawnParticle(Location, Particle, int, double, double, double)
            Method m3 = world.getClass().getMethod("spawnParticle", Location.class, Particle.class, int.class,
                    double.class, double.class, double.class);
            m3.invoke(world, loc, particle, count, offsetX, offsetY, offsetZ);
            return;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}

        // Give up silently if no compatible method is found.
    }
}
