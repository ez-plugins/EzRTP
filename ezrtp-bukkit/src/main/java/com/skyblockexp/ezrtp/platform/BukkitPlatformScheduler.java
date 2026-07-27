package com.skyblockexp.ezrtp.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;
    private final PlatformRuntimeCapabilities capabilities;

    /**
     * Creates a scheduler with explicit capability information.
     *
     * <p>When {@code capabilities.regionizedRuntime()} is {@code true} (Folia), sync Bukkit
     * scheduler calls are replaced with reflection-based Folia scheduler calls. This prevents
     * the {@link UnsupportedOperationException} that Folia's {@code CraftScheduler} throws for
     * all synchronous tasks.
     */
    public BukkitPlatformScheduler(Plugin plugin, PlatformRuntimeCapabilities capabilities) {
        this.plugin = plugin;
        this.capabilities = capabilities;
    }

    @Override
    public void executeAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void executeRegion(World world, int chunkX, int chunkZ, Runnable task) {
        if (capabilities.regionizedRuntime()) {
            boolean handled = world != null
                    ? invokeRegionTask(world, chunkX, chunkZ, task)
                    : invokeGlobalRun(task);
            if (handled) {
                return;
            }
            // Regionized runtime was detected but the reflective call failed unexpectedly
            // (e.g. an API signature mismatch). Falling through to the standard scheduler
            // is safer than silently dropping the task.
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public PlatformTask scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (capabilities.regionizedRuntime()) {
            PlatformTask foliaTask = scheduleGlobalRepeating(task, delayTicks, periodTicks);
            if (foliaTask != null) {
                return foliaTask;
            }
            // Folia detected but reflection unavailable — return a no-op rather than calling the
            // Bukkit scheduler which Folia forbids.
            return () -> {};
        }
        org.bukkit.scheduler.BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    @Override
    public void executeGlobal(Runnable task) {
        if (capabilities.regionizedRuntime() && invokeGlobalRun(task)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public PlatformTask executeGlobalDelayed(Runnable task, long delayTicks) {
        if (capabilities.regionizedRuntime()) {
            PlatformTask foliaTask = invokeGlobalRunDelayed(task, delayTicks);
            if (foliaTask != null) {
                return foliaTask;
            }
            // Reflective call failed unexpectedly; fall through to the standard scheduler
            // rather than returning a no-op that would silently drop the task.
        }
        org.bukkit.scheduler.BukkitTask bukkit =
                plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        return bukkit::cancel;
    }

    @Override
    public void executeRegionDelayed(
            World world, int chunkX, int chunkZ, Runnable task, long delayTicks) {
        if (capabilities.regionizedRuntime()) {
            boolean handled = world != null
                    ? invokeRegionDelayed(world, chunkX, chunkZ, task, delayTicks)
                    : invokeGlobalRunDelayed(task, delayTicks) != null;
            if (handled) {
                return;
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Player player, Location destination) {
        if (capabilities.regionizedRuntime()) {
            // Folia forbids synchronous teleport from a region thread. Use teleportAsync
            // via reflection so that the bukkit module works on Folia without a hard
            // Paper/Folia compile-time dependency.
            return invokeTeleportAsync(player, destination);
        }
        return CompletableFuture.completedFuture(player.teleport(destination));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Boolean> invokeTeleportAsync(Player player, Location destination) {
        try {
            Method method = player.getClass().getMethod("teleportAsync", Location.class);
            return (CompletableFuture<Boolean>) method.invoke(player, destination);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // teleportAsync not available, or the reflective call failed due to an API
            // signature change on a newer/forked server; fall back to sync teleport rather
            // than propagating the failure into the async RTP search chain.
            logReflectionFallback("teleportAsync", ex);
            return CompletableFuture.completedFuture(player.teleport(destination));
        }
    }

    private boolean invokeGlobalRun(Runnable task) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method run = globalScheduler.getClass().getMethod(
                    "run", Plugin.class, java.util.function.Consumer.class);
            run.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<Object>) ignored -> task.run());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getGlobalRegionScheduler#run", ex);
            return false;
        }
    }

    private PlatformTask invokeGlobalRunDelayed(Runnable task, long delayTicks) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method runDelayed = globalScheduler.getClass().getMethod(
                    "runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            Object scheduledTask = runDelayed.invoke(globalScheduler, plugin,
                    (java.util.function.Consumer<Object>) ignored -> task.run(), delayTicks);
            Method cancel = scheduledTask.getClass().getMethod("cancel");
            return () -> {
                try {
                    cancel.invoke(scheduledTask);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            };
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getGlobalRegionScheduler#runDelayed", ex);
            return null;
        }
    }

    private boolean invokeRegionTask(World world, int chunkX, int chunkZ, Runnable task) {
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method run = regionScheduler.getClass().getMethod(
                    "run", Plugin.class, World.class, int.class, int.class,
                    java.util.function.Consumer.class);
            run.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                    (java.util.function.Consumer<Object>) ignored -> task.run());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getRegionScheduler#run", ex);
            return false;
        }
    }

    private boolean invokeRegionDelayed(
            World world, int chunkX, int chunkZ, Runnable task, long delayTicks) {
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method runDelayed = regionScheduler.getClass().getMethod(
                    "runDelayed", Plugin.class, World.class, int.class, int.class,
                    java.util.function.Consumer.class, long.class);
            runDelayed.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                    (java.util.function.Consumer<Object>) ignored -> task.run(), delayTicks);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getRegionScheduler#runDelayed", ex);
            return false;
        }
    }

    private PlatformTask scheduleGlobalRepeating(Runnable task, long delayTicks, long periodTicks) {
        try {
            Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method runAtFixedRate = globalRegionScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class);
            Object scheduledTask = runAtFixedRate.invoke(globalRegionScheduler, plugin,
                    (java.util.function.Consumer<Object>) ignored -> task.run(),
                    delayTicks,
                    periodTicks);
            Method cancel = scheduledTask.getClass().getMethod("cancel");
            return () -> {
                try {
                    cancel.invoke(scheduledTask);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            };
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getGlobalRegionScheduler#runAtFixedRate", ex);
            return null;
        }
    }

    /**
     * Logs a fine-level diagnostic when a Folia/region-scheduler reflection call fails.
     *
     * <p>Reflective calls into region-scheduler APIs can fail with more than just
     * {@link ReflectiveOperationException} — a signature change on a newer or forked
     * server (e.g. Purpur) can surface as {@link IllegalArgumentException} ("argument
     * type mismatch") from {@link Method#invoke}. These are caught broadly so a single
     * unexpected API drift falls back to the standard scheduler instead of breaking
     * the caller (notably the async RTP location search).
     */
    private void logReflectionFallback(String operation, Throwable ex) {
        plugin.getLogger().log(Level.FINE, "EzRTP: region-scheduler reflection call " + operation
                + " failed, falling back to standard scheduler", ex);
    }
}
