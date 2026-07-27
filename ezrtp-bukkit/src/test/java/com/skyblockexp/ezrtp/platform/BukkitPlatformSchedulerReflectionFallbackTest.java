package com.skyblockexp.ezrtp.platform;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
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
 * Regression coverage mirroring {@code PaperPlatformSchedulerReflectionFallbackTest}, for the
 * {@link BukkitPlatformScheduler} fallback that is used when the Paper runtime module is absent.
 *
 * <p>Confirms that when {@code regionizedRuntime()} is {@code true} (the region-scheduler API is
 * present on the classpath) but the reflective {@code Method.invoke} call fails unexpectedly —
 * e.g. with {@link IllegalArgumentException} ("argument type mismatch") rather than
 * {@link ReflectiveOperationException} — the scheduler falls back to the standard Bukkit
 * scheduler and still runs the submitted task, instead of throwing or silently dropping it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BukkitPlatformSchedulerReflectionFallbackTest {

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

            BukkitPlatformScheduler scheduler =
                    new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

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

            BukkitPlatformScheduler scheduler =
                    new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

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

            BukkitPlatformScheduler scheduler =
                    new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

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

            BukkitPlatformScheduler scheduler =
                    new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PURPUR_REGIONIZED);

            assertDoesNotThrow(() -> scheduler.executeRegionDelayed(world, 0, 0, () -> {}, 20L));
        }

        verify(bukkitScheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    private void stubRunTaskToExecuteImmediately() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return mock(BukkitTask.class);
        }).when(bukkitScheduler).runTask(any(Plugin.class), any(Runnable.class));
    }
}
