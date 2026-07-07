package com.skyblockexp.ezrtp.gui;

import com.skyblockexp.ezrtp.message.MessageProvider;
import com.skyblockexp.ezrtp.message.MessageKey;
import com.skyblockexp.ezrtp.config.RandomTeleportSettings;
import com.skyblockexp.ezrtp.config.EzRtpConfiguration;
import com.skyblockexp.ezrtp.config.gui.GuiServerOption;
import com.skyblockexp.ezrtp.config.gui.GuiSettings;
import com.skyblockexp.ezrtp.config.gui.GuiWorldOption;
import com.skyblockexp.ezrtp.config.teleport.RtpLimitSettings;
import com.skyblockexp.ezrtp.config.network.NetworkConfiguration;
import com.skyblockexp.ezrtp.network.NetworkService;
import com.skyblockexp.ezrtp.platform.PlatformRuntimeRegistry;
import com.skyblockexp.ezrtp.storage.RtpUsageStorage;
import com.skyblockexp.ezrtp.teleport.RandomTeleportService;
import com.skyblockexp.ezrtp.teleport.TeleportReason;
import com.skyblockexp.ezrtp.util.MessageUtil;
import com.skyblockexp.ezrtp.util.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Handles the interactive inventory GUI that allows players to choose which world to randomly teleport within.
 * This class coordinates GUI building, session management, and event handling.
 */
public final class RandomTeleportGuiManager implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<RandomTeleportService> teleportServiceSupplier;
    private final Supplier<EzRtpConfiguration> configurationSupplier;
    private final Supplier<NetworkService> networkServiceSupplier;
    private final RtpUsageStorage usageStorage;
    private final Supplier<MessageProvider> messageSupplier;

    private final GuiSessionManager sessionManager;

    // Use TextRenderer for runtime-compatible parsing

    public RandomTeleportGuiManager(JavaPlugin plugin,
                                    Supplier<RandomTeleportService> teleportServiceSupplier,
                                    Supplier<EzRtpConfiguration> configurationSupplier,
                                    Supplier<NetworkService> networkServiceSupplier,
                                    Supplier<MessageProvider> messageSupplier,
                                    RtpUsageStorage usageStorage) {
        this.plugin = plugin;
        this.teleportServiceSupplier = teleportServiceSupplier;
        this.configurationSupplier = configurationSupplier;
        this.networkServiceSupplier = networkServiceSupplier;
        this.messageSupplier = messageSupplier;
        this.usageStorage = usageStorage;

        // Initialize helper classes
        this.sessionManager = new GuiSessionManager();
    }

    /**
     * Opens the world selection GUI for the specified player if it is enabled.
     *
     * @return {@code true} if the GUI was opened, {@code false} otherwise.
     */
    public boolean openSelection(Player player) {
        EzRtpConfiguration configuration = configurationSupplier.get();
        if (configuration == null) {
            return false;
        }

        GuiSettings guiSettings = configuration.getGuiSettings();
        if (!guiSettings.isEnabled()) {
            return false;
        }

        if (guiSettings.getWorldOptions().isEmpty() && guiSettings.getServerOptions().isEmpty()) {
            return false;
        }

        // Check if GUI is allowed during cooldown
        if (!configuration.isAllowGuiDuringCooldown()) {
            boolean onCooldown = checkGlobalCooldown(player, configuration);
            if (onCooldown) {
                MessageUtil.send(player, messageSupplier.get().format(MessageKey.GUI_COOLDOWN, player));
                return false;
            }
        }

        // Build the GUI from current suppliers so /ezrtp reload immediately reflects gui.yml changes.
        GuiBuilder guiBuilder = createGuiBuilder(configuration);
        GuiBuilder.GuiBuildResult result = guiBuilder.buildGui(player);

        // Check if we have any options to display
        if (result.getOptionMap().isEmpty()) {
            MessageUtil.send(player, guiSettings.getNoDestinations());
            return true;
        }

        // Send informational message if some options were filtered due to cache
        sendCacheFilterMessage(player, guiSettings);

        // Start the session and open inventory
        sessionManager.startSession(player, result.getInventory(), result.getOptionMap(), result.getSettings());

        player.openInventory(result.getInventory());

        // Force inventory update to ensure client displays item lore/metadata properly on Spigot
        refreshInventoryForSpigot(player);

        return true;
    }

    private boolean checkGlobalCooldown(Player player, EzRtpConfiguration configuration) {
        RtpLimitSettings limit = configuration.getLimitSettings(player.getWorld().getName(), null);
        int cooldownSeconds = limit != null ? limit.getCooldownSeconds() : 0;
        if (cooldownSeconds <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lastRtp = usageStorage.getLastRtpTime(player.getUniqueId(), null);
        long cooldownMs = cooldownSeconds * 1000L;
        return lastRtp > 0 && (now - lastRtp) < cooldownMs;
    }

    private void sendCacheFilterMessage(Player player, GuiSettings guiSettings) {
        // This method would send a message if some options were filtered due to cache
        // For now, it's empty as the logic is handled in GuiOptionsBuilder
    }

    private void refreshInventoryForSpigot(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
            // Some server implementations may not support forcing an update here; ignore safely.
        }
    }

    private GuiBuilder createGuiBuilder(EzRtpConfiguration configuration) {
        java.util.logging.Logger fallbackLogger = java.util.logging.Logger.getLogger(RandomTeleportGuiManager.class.getSimpleName());
        java.util.logging.Logger effectiveLogger = plugin != null ? plugin.getLogger() : fallbackLogger;
        RandomTeleportService teleportService = teleportServiceSupplier.get();
        return new GuiBuilder(
                configuration,
                usageStorage,
                teleportService != null ? teleportService.getBiomeCache() : null,
                networkServiceSupplier.get(),
                effectiveLogger
        );
    }

    public void closeAll() {
        Map<UUID, GuiSessionManager.GuiSession> sessions = sessionManager.getAllSessions();
        if (sessions.isEmpty() || plugin == null) {
            sessionManager.clearAllSessions();
            return;
        }

        for (UUID playerId : sessions.keySet()) {
            Player active = plugin.getServer().getPlayer(playerId);
            if (active != null && active.isOnline()) {
                active.closeInventory();
            }
        }
        sessionManager.clearAllSessions();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSessionManager.GuiSession session = sessionManager.getSession(player);
        if (session == null || !Objects.equals(event.getView().getTopInventory(), session.inventory())) {
            return;
        }

        // Disable shift-clicking entirely - show no permission message
        if (event.isShiftClick()) {
            session.settings().noPermissionMessage().ifPresent(msg -> MessageUtil.send(player, msg));
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            return;
        }

        // Cancel all other clicks in the GUI to prevent item movement
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        // Only process clicks within the GUI inventory (not player's inventory)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= session.inventory().getSize()) {
            return;
        }

        GuiOption option = session.options().get(event.getRawSlot());
        if (option == null) {
            return;
        }

        if (option.isWorldOption()) {
            GuiWorldOption worldOption = option.worldOption();
            if (!worldOption.getPermission().isEmpty() && !player.hasPermission(worldOption.getPermission())) {
                session.settings().noPermissionMessage().ifPresent(msg -> MessageUtil.send(player, msg));
                return;
            }
            RandomTeleportService service = teleportServiceSupplier.get();
            if (service == null) {
                return;
            }

            RandomTeleportSettings settings = worldOption.getSettings();
            EzRtpConfiguration configuration = configurationSupplier.get();
            MessageProvider messages = messageSupplier.get();
            if (configuration == null || messages == null) {
                return;
            }

            // Resolve "auto" to the player's actual current world for limit checking and usage tracking
            String worldName = settings.isAutoWorld() ? player.getWorld().getName() : settings.getWorldName();
            boolean bypass = player.isOp();
            if (!bypass) {
                for (String perm : configuration.getBypassPermissions()) {
                    if (player.hasPermission(perm)) {
                        bypass = true;
                        break;
                    }
                }
            }

            if (!bypass) {
                String group = configuration.resolveGroup(player, worldName);
                RtpLimitSettings limit = configuration.getLimitSettings(worldName, group);
                long now = System.currentTimeMillis();
                long lastRtp = usageStorage.getLastRtpTime(player.getUniqueId(), worldName);
                int daily = usageStorage.getUsageCount(player.getUniqueId(), worldName, "daily");
                int weekly = usageStorage.getUsageCount(player.getUniqueId(), worldName, "weekly");

                // Cooldown check
                if (limit.getCooldownSeconds() > 0 && lastRtp > 0 && (now - lastRtp) < limit.getCooldownSeconds() * 1000L) {
                    long wait = (limit.getCooldownSeconds() * 1000L - (now - lastRtp)) / 1000L;
                    String msg = messages.getMessage(MessageKey.COOLDOWN);
                    if (configuration.isHumanReadableCooldown()) {
                        String formattedTime = PlaceholderUtil.formatTime((int) wait);
                        String[] parts = formattedTime.split(" ");
                        String hours = parts.length > 0 && parts[0].endsWith("h") ? parts[0].substring(0, parts[0].length() - 1) : "0";
                        String minutes = parts.length > 1 && parts[1].endsWith("m") ? parts[1].substring(0, parts[1].length() - 1) : "0";
                        String seconds = parts.length > 2 && parts[2].endsWith("s") ? parts[2].substring(0, parts[2].length() - 1) : String.valueOf(wait);
                        msg = msg.replace("<hours>", hours).replace("<minutes>", minutes).replace("<seconds>", seconds);
                    } else {
                        msg = msg.replace("<seconds>", String.valueOf(wait));
                    }
                    // Resolve PlaceholderAPI placeholders for the player and send
                    String resolvedCooldown = PlaceholderUtil.resolvePlaceholders(player, msg, plugin.getLogger());
                    MessageUtil.send(player, com.skyblockexp.ezrtp.util.MessageUtil.parseMiniMessage(resolvedCooldown));
                    return;
                }

                // Daily limit check
                if (!limit.isDisableDailyLimit() && limit.getDailyLimit() > 0 && daily >= limit.getDailyLimit()) {
                    String msg = messages.getMessage(MessageKey.LIMIT_DAILY);
                    MessageUtil.send(player, com.skyblockexp.ezrtp.util.MessageUtil.parseMiniMessage(PlaceholderUtil.resolvePlaceholders(player, msg, plugin.getLogger())));
                    return;
                }

                // Weekly limit check
                if (!limit.isDisableDailyLimit() && limit.getWeeklyLimit() > 0 && weekly >= limit.getWeeklyLimit()) {
                    String msg = messages.getMessage(MessageKey.LIMIT_WEEKLY);
                    MessageUtil.send(player, com.skyblockexp.ezrtp.util.MessageUtil.parseMiniMessage(PlaceholderUtil.resolvePlaceholders(player, msg, plugin.getLogger())));
                    return;
                }
            }

            player.closeInventory();
            
            // Track usage and cooldown after successful teleport
            service.teleportPlayer(player, settings, TeleportReason.COMMAND, success -> {
                if (success && usageStorage != null) {
                    usageStorage.setLastRtpTime(player.getUniqueId(), worldName, System.currentTimeMillis());
                    usageStorage.incrementUsage(player.getUniqueId(), worldName, "daily");
                    usageStorage.incrementUsage(player.getUniqueId(), worldName, "weekly");
                    // Save asynchronously to avoid blocking main thread
                    PlatformRuntimeRegistry.get().scheduler().executeAsync(() -> usageStorage.save());
                }
            });
            return;
        }

        GuiServerOption serverOption = option.serverOption();
        if (serverOption == null) {
            return;
        }

        NetworkService networkService = networkServiceSupplier != null ? networkServiceSupplier.get() : null;
        if (networkService == null) {
            MessageUtil.send(player, messageSupplier.get().getMessage(MessageKey.NETWORK_SERVICE_UNAVAILABLE));
            return;
        }

        NetworkConfiguration.NetworkServer server = serverOption.getServer();
        if (!server.getPermission().isEmpty() && !player.hasPermission(server.getPermission())) {
            session.settings().noPermissionMessage().ifPresent(msg -> MessageUtil.send(player, msg));
            return;
        }

        NetworkConfiguration.ServerStatusSnapshot status = networkService.getStatus(server);
        if (status.isOffline() && !server.allowWhenOffline()) {
            Component message = server.offlineMessage();
            if (message != null && !message.equals(Component.empty())) {
                MessageUtil.send(player, message);
            } else {
                String fallback = null;
                try {
                    MessageProvider provider = messageSupplier.get();
                    if (provider != null) {
                        fallback = provider.getMessage(MessageKey.NETWORK_SERVER_OFFLINE);
                    }
                } catch (Throwable ignored) {
                }
                if (fallback == null) {
                    // default fallback text
                    fallback = "§cThat server is currently offline.";
                }
                // Send legacy string directly to the player to avoid serialization fallbacks
                try {
                    player.sendMessage(fallback);
                } catch (Throwable t) {
                    MessageUtil.send(player, fallback);
                }
            }
            return;
        }

        player.closeInventory();
        networkService.transferPlayer(player, server);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSessionManager.GuiSession session = sessionManager.getSession(player);
        if (session == null || !Objects.equals(event.getView().getTopInventory(), session.inventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        GuiSessionManager.GuiSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        if (Objects.equals(session.inventory(), event.getInventory())) {
            sessionManager.endSession(player);
        }
    }

    private void fillInventory(Inventory inventory, ItemStack fillerTemplate) {
        ItemStack filler = fillerTemplate.clone();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }
}

