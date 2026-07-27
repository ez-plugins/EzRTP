package com.skyblockexp.ezrtp.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PaperPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;
    private final PlatformRuntimeCapabilities capabilities;

    public PaperPlatformScheduler(Plugin plugin, PlatformRuntimeCapabilities capabilities) {
        this.plugin = plugin;
        this.capabilities = capabilities;
    }

    @Override
    public void executeAsync(Runnable task) {
        if (capabilities.regionizedRuntime() && invokeFoliaAsync(task)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void executeRegion(World world, int chunkX, int chunkZ, Runnable task) {
        if (capabilities.regionizedRuntime() && world != null && invokeRegionTask(world, chunkX, chunkZ, task)) {
            return;
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
        }

        org.bukkit.scheduler.BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    private boolean invokeFoliaAsync(Runnable task) {
        try {
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
            runNow.invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) scheduledTask -> task.run());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getAsyncScheduler#runNow", ex);
            return false;
        }
    }

    private boolean invokeRegionTask(World world, int chunkX, int chunkZ, Runnable task) {
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method run = regionScheduler.getClass().getMethod(
                    "run", Plugin.class, World.class, int.class, int.class,
                    java.util.function.Consumer.class);
            run.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                    (java.util.function.Consumer<Object>) scheduledTask -> task.run());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logReflectionFallback("getRegionScheduler#run", ex);
            return false;
        }
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
        }
        org.bukkit.scheduler.BukkitTask bukkit =
                plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        return bukkit::cancel;
    }

    @Override
    public void executeRegionDelayed(
            World world, int chunkX, int chunkZ, Runnable task, long delayTicks) {
        if (capabilities.regionizedRuntime()
                && world != null
                && invokeRegionDelayed(world, chunkX, chunkZ, task, delayTicks)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    @Override
    public CompletableFuture<Boolean> teleportAsync(Player player, Location destination) {
        // Paper and Folia both expose teleportAsync on Player.
        // On Folia, the synchronous Player.teleport() throws when called from a
        // region thread, so this override is required for Folia compatibility.
        return player.teleportAsync(destination);
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
                    long.class
            );
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
