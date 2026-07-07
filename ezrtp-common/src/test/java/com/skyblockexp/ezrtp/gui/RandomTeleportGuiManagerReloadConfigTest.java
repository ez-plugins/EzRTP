package com.skyblockexp.ezrtp.gui;

import com.skyblockexp.ezrtp.config.EzRtpConfiguration;
import com.skyblockexp.ezrtp.message.MessageProvider;
import com.skyblockexp.ezrtp.network.NetworkService;
import com.skyblockexp.ezrtp.platform.PlatformGuiBridge;
import com.skyblockexp.ezrtp.platform.PlatformGuiBridgeRegistry;
import com.skyblockexp.ezrtp.storage.RtpUsageStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RandomTeleportGuiManagerReloadConfigTest {

    @AfterEach
    void resetGuiBridgeRegistry() {
        PlatformGuiBridgeRegistry.unregister();
    }

    @Test
    void openSelectionUsesLatestGuiSettingsAfterConfigurationReplacement() {
        RecordingGuiBridge bridge = new RecordingGuiBridge();
        PlatformGuiBridgeRegistry.register(bridge);

        AtomicReference<EzRtpConfiguration> configurationRef =
                new AtomicReference<>(createConfiguration("First GUI", 1));

        RtpUsageStorage usageStorage = mock(RtpUsageStorage.class);
        MessageProvider messageProvider = mock(MessageProvider.class);
        NetworkService networkService = mock(NetworkService.class);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        when(player.openInventory(inventory)).thenReturn(null);

        RandomTeleportGuiManager manager = new RandomTeleportGuiManager(
                null,
                () -> null,
                configurationRef::get,
                () -> networkService,
                () -> messageProvider,
                usageStorage);

        boolean firstOpened = manager.openSelection(player);

        configurationRef.set(createConfiguration("Second GUI", 2));

        boolean secondOpened = manager.openSelection(player);

        assertTrue(firstOpened);
        assertTrue(secondOpened);
        assertEquals(List.of(9, 18), bridge.recordedSizes());
        assertEquals(List.of("First GUI", "Second GUI"), bridge.recordedTitles());
    }

    private static EzRtpConfiguration createConfiguration(String title, int rows) {
        YamlConfiguration base = new YamlConfiguration();
        base.set("world", "world");

        YamlConfiguration gui = new YamlConfiguration();
        gui.set("enabled", true);
        gui.set("title", title);
        gui.set("rows", rows);
        gui.set("worlds.overworld.slot", 0);
        gui.set("worlds.overworld.settings.world", "world");

        return EzRtpConfiguration.fromConfigurations(
                base,
                null,
                gui,
                null,
                null,
                Logger.getLogger(RandomTeleportGuiManagerReloadConfigTest.class.getSimpleName()));
    }

    private static final class RecordingGuiBridge implements PlatformGuiBridge {
        private final List<String> recordedTitles = new ArrayList<>();
        private final List<Integer> recordedSizes = new ArrayList<>();

        @Override
        public Inventory createInventory(InventoryHolder holder, int size, Component title) {
            recordedSizes.add(size);
            recordedTitles.add(PlainTextComponentSerializer.plainText().serialize(title));
            return mock(Inventory.class);
        }

        @Override
        public void setDisplayName(ItemMeta meta, Component displayName) {
        }

        @Override
        public void setLore(ItemMeta meta, List<Component> lore) {
        }

        @Override
        public void applyItemMeta(ItemStack icon, ItemMeta meta) {
        }

        private List<String> recordedTitles() {
            return recordedTitles;
        }

        private List<Integer> recordedSizes() {
            return recordedSizes;
        }
    }
}
