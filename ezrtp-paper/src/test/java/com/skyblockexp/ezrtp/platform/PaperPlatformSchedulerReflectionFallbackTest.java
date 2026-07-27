package com.skyblockexp.ezrtp.platform;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a real-world "/rtp" failure reported on Purpur.
 *
 * <p>{@link PlatformRuntimeCapabilitiesDetector} marks a server as {@code regionizedRuntime}
 * purely by the presence of a Folia marker class on the classpath. As Paper continues merging
 * Folia's regionized-threading model upstream, that marker class can be present on a Purpur (or
 * Paper) server jar even when the server is not actually running region-threaded. When that
 * happens, {@link PaperPlatformScheduler} still routes {@code executeRegion}/{@code executeGlobal}
 * /etc. through reflective calls into {@code Bukkit.getRegionScheduler()} /
 * {@code Bukkit.getGlobalRegionScheduler()}.
 *
 * <p>Previously, if those reflective {@code Method.invoke} calls failed with anything other than
 * {@link ReflectiveOperationException} — for example {@link IllegalArgumentException} ("argument
 * type mismatch") from an API signature drift — the exception propagated uncaught out of the
 * scheduler and into the async RTP location-search {@link java.util.concurrent.CompletableFuture}
 * chain, surfacing to players as "/rtp" always failing (logged as
 * {@code CompletionException - java.lang.IllegalArgumentException: argument type mismatch}).
 *
 * <p>These tests simulate that exact failure (the reflective method lookup succeeds, but invoking
 * it throws) and confirm the scheduler falls back to the standard Bukkit scheduler — without
 * throwing, and without silently dropping the submitted task.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaperPlatformSchedulerReflectionFallbackTest {

    @Mock
    private Plugin plugin;

    @Mock
    private Server server;

    @Mock
    private BukkitScheduler bukkitScheduler;

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(bukkitScheduler);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
    }

    @Test
    void executeRegion_fallsBackAndRunsTaskWhenRegionSchedulerReflectionThrows() {
        World world = mock(World.class);
        RegionScheduler brokenRegionScheduler = mock(RegionScheduler.class);
        when(brokenRegionScheduler.run(any(Plugin.class), any(World.class), anyInt(), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        AtomicBoolean taskRan = new AtomicBoolean(false);
        stubRunTaskToExecuteImmediately();

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getRegionScheduler).thenReturn(brokenRegionScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            assertDoesNotThrow(() -> scheduler.executeRegion(world, 0, 0, () -> taskRan.set(true)));
        }

        assertTrue(taskRan.get(), "Task must still run via the Bukkit-scheduler fallback");
        verify(bukkitScheduler).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void executeGlobal_fallsBackAndRunsTaskWhenGlobalRegionSchedulerReflectionThrows() {
        GlobalRegionScheduler brokenGlobalScheduler = mock(GlobalRegionScheduler.class);
        when(brokenGlobalScheduler.run(any(Plugin.class), any()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        AtomicBoolean taskRan = new AtomicBoolean(false);
        stubRunTaskToExecuteImmediately();

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(brokenGlobalScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            assertDoesNotThrow(() -> scheduler.executeGlobal(() -> taskRan.set(true)));
        }

        assertTrue(taskRan.get(), "Task must still run via the Bukkit-scheduler fallback");
        verify(bukkitScheduler).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void executeGlobalDelayed_fallsBackWhenGlobalRegionSchedulerReflectionThrows() {
        GlobalRegionScheduler brokenGlobalScheduler = mock(GlobalRegionScheduler.class);
        when(brokenGlobalScheduler.runDelayed(any(Plugin.class), any(), anyLong()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenReturn(bukkitTask);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(brokenGlobalScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            PlatformTask task = assertDoesNotThrow(() -> scheduler.executeGlobalDelayed(() -> {}, 20L));
            assertNotNull(task);
        }

        verify(bukkitScheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    @Test
    void executeRegionDelayed_fallsBackWhenRegionSchedulerReflectionThrows() {
        World world = mock(World.class);
        RegionScheduler brokenRegionScheduler = mock(RegionScheduler.class);
        when(brokenRegionScheduler.runDelayed(any(Plugin.class), any(World.class), anyInt(), anyInt(), any(), anyLong()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getRegionScheduler).thenReturn(brokenRegionScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            assertDoesNotThrow(() -> scheduler.executeRegionDelayed(world, 0, 0, () -> {}, 20L));
        }

        verify(bukkitScheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    @Test
    void scheduleRepeating_fallsBackWhenGlobalRegionSchedulerReflectionThrows() {
        GlobalRegionScheduler brokenGlobalScheduler = mock(GlobalRegionScheduler.class);
        when(brokenGlobalScheduler.runAtFixedRate(any(Plugin.class), any(), anyLong(), anyLong()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(bukkitTask);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(brokenGlobalScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            PlatformTask task = assertDoesNotThrow(() -> scheduler.scheduleRepeating(() -> {}, 1L, 20L));
            assertNotNull(task);
        }

        verify(bukkitScheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(20L));
    }

    /**
     * Feature-level check: a full teleport-style hop (region-thread candidate resolution
     * followed by a global-thread completion callback) must still complete end-to-end when
     * the region-scheduler reflection is broken, mirroring the "/rtp" search-and-teleport flow.
     */
    @Test
    void regionThenGlobalSequence_completesEndToEndWhenReflectionIsBroken() {
        World world = mock(World.class);
        RegionScheduler brokenRegionScheduler = mock(RegionScheduler.class);
        when(brokenRegionScheduler.run(any(Plugin.class), any(World.class), anyInt(), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));
        GlobalRegionScheduler brokenGlobalScheduler = mock(GlobalRegionScheduler.class);
        when(brokenGlobalScheduler.run(any(Plugin.class), any()))
                .thenThrow(new IllegalArgumentException("argument type mismatch"));

        stubRunTaskToExecuteImmediately();

        AtomicBoolean candidateResolved = new AtomicBoolean(false);
        AtomicBoolean teleportCompleted = new AtomicBoolean(false);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getRegionScheduler).thenReturn(brokenRegionScheduler);
            mockedBukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(brokenGlobalScheduler);

            PaperPlatformScheduler scheduler =
                    new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            assertDoesNotThrow(() -> {
                scheduler.executeRegion(world, 0, 0, () -> {
                    candidateResolved.set(true);
                    scheduler.executeGlobal(() -> teleportCompleted.set(true));
                });
            });
        }

        assertTrue(candidateResolved.get(), "Candidate resolution must still run on the region fallback");
        assertTrue(teleportCompleted.get(), "Teleport completion must still run on the global fallback");
    }

    @Test
    void teleportAsync_doesNotDependOnRegionSchedulerReflection() {
        Player player = mock(Player.class);
        org.bukkit.Location destination = mock(org.bukkit.Location.class);
        when(player.teleportAsync(destination))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));

        PaperPlatformScheduler scheduler =
                new PaperPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

        assertDoesNotThrow(() -> scheduler.teleportAsync(player, destination).join());
    }

    private void stubRunTaskToExecuteImmediately() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(bukkitScheduler).runTask(any(Plugin.class), any(Runnable.class));
    }
}
