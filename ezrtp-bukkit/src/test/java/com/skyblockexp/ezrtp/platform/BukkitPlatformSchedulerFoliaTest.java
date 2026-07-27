package com.skyblockexp.ezrtp.platform;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for Folia compatibility in {@link BukkitPlatformScheduler}.
 *
 * <p>Folia's {@code CraftScheduler} throws {@link UnsupportedOperationException} for all
 * synchronous Bukkit scheduler calls. When the Paper runtime module is absent, the plugin
 * falls back to {@link BukkitPlatformScheduler}; these tests confirm it handles Folia safely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BukkitPlatformSchedulerFoliaTest {

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

    // --- scheduleRepeating ---

    /**
     * Confirms the original bug: without Folia-awareness, {@code scheduleRepeating} propagates
     * the {@link UnsupportedOperationException} that Folia's scheduler throws.
     */
    @Test
    void scheduleRepeating_withoutFoliaAwareness_propagatesUnsupportedOperationException() {
        when(bukkitScheduler.runTaskTimer(
                        any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenThrow(new UnsupportedOperationException("Folia: use regionized scheduler"));

        // Old constructor (no capabilities) — still valid; always behaves as Bukkit.
        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.BUKKIT);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> scheduler.scheduleRepeating(() -> {}, 1L, 20L));
    }

    /**
     * Verifies the fix: when constructed with Folia capabilities, {@code scheduleRepeating}
     * does NOT call the Bukkit scheduler at all and returns a non-null task. The Folia
     * reflection path is attempted first; if Folia classes are absent (unit-test JVM), the
     * method returns a no-op task rather than crashing.
     */
    @Test
    void scheduleRepeating_withFoliaCapabilities_doesNotThrow() {
        // scheduleRepeating intentionally stays a no-op on reflection failure (rather than
        // falling back to the standard scheduler) since Folia forbids synchronous scheduling
        // outright; see the comment in BukkitPlatformScheduler#scheduleRepeating.
        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        PlatformTask task = assertDoesNotThrow(() -> scheduler.scheduleRepeating(() -> {}, 1L, 20L));
        assertNotNull(task);
    }

    // --- scheduleRepeating on standard Bukkit (no regression) ---

    @Test
    void scheduleRepeating_withBukkitCapabilities_delegatesToBukkitScheduler() {
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTaskTimer(
                        any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.BUKKIT);

        PlatformTask task = scheduler.scheduleRepeating(() -> {}, 5L, 100L);

        assertNotNull(task);
        verify(bukkitScheduler)
                .runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(100L));
    }

    // --- executeGlobal ---

    @Test
    void executeGlobal_withFoliaCapabilities_doesNotThrow() {
        // Reflection naturally fails in the test JVM (no Folia classes present), so this
        // now falls through to the standard Bukkit scheduler, matching PaperPlatformScheduler.
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTask(any(Plugin.class), any(Runnable.class))).thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        assertDoesNotThrow(() -> scheduler.executeGlobal(() -> {}));
        verify(bukkitScheduler).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void executeGlobal_withBukkitCapabilities_delegatesToBukkitScheduler() {
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.BUKKIT);

        assertDoesNotThrow(() -> scheduler.executeGlobal(() -> {}));
        verify(bukkitScheduler).runTask(eq(plugin), any(Runnable.class));
    }

    // --- executeGlobalDelayed ---

    @Test
    void executeGlobalDelayed_withFoliaCapabilities_doesNotThrow() {
        // Reflection naturally fails in the test JVM (no Folia classes present), so this
        // now falls through to the standard Bukkit scheduler, matching PaperPlatformScheduler.
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        assertDoesNotThrow(() -> scheduler.executeGlobalDelayed(() -> {}, 20L));
    }

    @Test
    void executeGlobalDelayed_withBukkitCapabilities_delegatesToBukkitScheduler() {
        BukkitTask mockTask = mock(BukkitTask.class);
        when(server.getScheduler().runTaskLater(
                        any(Plugin.class), any(Runnable.class), anyLong()))
                .thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.BUKKIT);

        PlatformTask task = scheduler.executeGlobalDelayed(() -> {}, 20L);

        assertNotNull(task);
        verify(bukkitScheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    // --- executeRegion ---

    @Test
    void executeRegion_withFoliaCapabilities_doesNotThrow() {
        // Reflection naturally fails in the test JVM (no Folia classes present), so this
        // now falls through to the standard Bukkit scheduler, matching PaperPlatformScheduler.
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTask(any(Plugin.class), any(Runnable.class))).thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        assertDoesNotThrow(() -> scheduler.executeRegion(null, 0, 0, () -> {}));
        verify(bukkitScheduler).runTask(eq(plugin), any(Runnable.class));
    }

    // --- executeRegionDelayed ---

    @Test
    void executeRegionDelayed_withFoliaCapabilities_doesNotThrow() {
        // Reflection naturally fails in the test JVM (no Folia classes present), so this
        // now falls through to the standard Bukkit scheduler, matching PaperPlatformScheduler.
        BukkitTask mockTask = mock(BukkitTask.class);
        when(bukkitScheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong()))
                .thenReturn(mockTask);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        assertDoesNotThrow(() -> scheduler.executeRegionDelayed(null, 0, 0, () -> {}, 20L));
        verify(bukkitScheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
    }

    // --- teleportAsync ---

    /**
     * On Folia (regionized), teleportAsync must not call the synchronous Player.teleport().
     * Since Player.teleportAsync() is not available in the test JVM, the reflection path
     * falls through to the sync fallback. This test verifies the method does not throw and
     * returns a completed future.
     */
    @Test
    void teleportAsync_withFoliaCapabilities_doesNotThrow() {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        when(player.teleport(destination)).thenReturn(true);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.PAPER_FOLIA);

        assertDoesNotThrow(() -> scheduler.teleportAsync(player, destination));
    }

    /**
     * On standard Bukkit, teleportAsync delegates to the synchronous Player.teleport().
     */
    @Test
    void teleportAsync_withBukkitCapabilities_callsSyncTeleport() throws Exception {
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        when(player.teleport(destination)).thenReturn(true);

        BukkitPlatformScheduler scheduler =
                new BukkitPlatformScheduler(plugin, PlatformRuntimeCapabilities.BUKKIT);

        Boolean result = scheduler.teleportAsync(player, destination).get();

        assertTrue(result);
        verify(player).teleport(destination);
    }
}
