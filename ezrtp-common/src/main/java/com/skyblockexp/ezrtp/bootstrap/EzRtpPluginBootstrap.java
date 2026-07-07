package com.skyblockexp.ezrtp.bootstrap;

import com.skyblockexp.ezrtp.EzRtpPlugin;
import com.skyblockexp.ezrtp.bootstrap.component.ConfigurationService;
import com.skyblockexp.ezrtp.bootstrap.component.EconomyCoordinator;
import com.skyblockexp.ezrtp.bootstrap.component.ListenerRegistrar;
import com.skyblockexp.ezrtp.bootstrap.component.NetworkCoordinator;
import com.skyblockexp.ezrtp.bootstrap.component.PlatformAdapterRegistrar;
import com.skyblockexp.ezrtp.bootstrap.component.UsageResetScheduler;
import com.skyblockexp.ezrtp.command.RandomTeleportCommand;
import com.skyblockexp.ezrtp.command.ForceRtpCommand;
import com.skyblockexp.ezrtp.config.EzRtpConfiguration;
import com.skyblockexp.ezrtp.config.ForceRtpConfiguration;
import com.skyblockexp.ezrtp.config.performance.PerformanceSettings;
import com.skyblockexp.ezrtp.config.safety.UnsafeLocationSettings;
import com.skyblockexp.ezrtp.config.RandomTeleportSettings;
import com.skyblockexp.ezrtp.economy.EconomyService;
import com.skyblockexp.ezrtp.performance.PerformanceMonitor;
import com.skyblockexp.ezrtp.unsafe.UnsafeLocationMonitor;
import com.skyblockexp.ezrtp.unsafe.UnsafeLocationStatistics;
import com.skyblockexp.ezrtp.gui.FactionClaimSelectionGuiManager;
import com.skyblockexp.ezrtp.gui.RandomTeleportGuiManager;
import com.skyblockexp.ezrtp.integration.EzCountdownDisplayBridge;
import com.skyblockexp.ezrtp.integration.EzCountdownDisplayBridgeImpl;
import com.skyblockexp.ezrtp.integration.TeamsApiSubcommandBridge;
import com.skyblockexp.ezrtp.message.MessageProvider;
import com.skyblockexp.ezrtp.metrics.EzRtpMetricsRegistrar;
import com.skyblockexp.ezrtp.platform.ChunkLoadStrategyRegistry;
import com.skyblockexp.ezrtp.platform.PlatformGuiBridgeRegistry;
import com.skyblockexp.ezrtp.platform.PlatformRuntimeCapabilitiesDetector;
import com.skyblockexp.ezrtp.platform.PlatformRuntimeRegistry;
import com.skyblockexp.ezrtp.protection.ProtectionRegistry;
import com.skyblockexp.ezrtp.storage.MySqlRtpUsageStorage;
import com.skyblockexp.ezrtp.storage.RtpUsageStorage;
import com.skyblockexp.ezrtp.storage.YamlRtpUsageStorage;
import com.skyblockexp.ezrtp.teleport.RandomTeleportService;
import com.skyblockexp.ezrtp.api.TeleportService;
import com.skyblockexp.ezrtp.api.EzRtpAPI;
import com.skyblockexp.ezrtp.teleport.heatmap.HeatmapSimulationStore;
import com.skyblockexp.ezrtp.update.ReleaseUpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import com.skyblockexp.ezrtp.teleport.ChunkyProvider;
import com.skyblockexp.ezrtp.teleport.ChunkyRuntimeProvider;
import com.skyblockexp.ezrtp.pvptag.PvpTagService;
// Chunky API is optional; use runtime loader via ChunkyRuntimeProvider

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates EzRTP plugin bootstrap, configuration reloads, and lifecycle tasks
 * so {@link EzRtpPlugin} can remain focused on the Bukkit entry points.
 */
public final class EzRtpPluginBootstrap {

    private static final String CORE_PLUGIN_NAME = "EzRTP";
    private static final List<String> RUNTIME_MODULE_PLUGIN_NAMES = List.of(
            "EzRTPPaperModule",
            "EzRTPPurpurModule",
            "EzRTPPaper",
            "EzRTP-Paper",
            "EzRTPPaperRuntimeModule"
    );

    private final EzRtpPlugin plugin;

    private final ConfigurationService configurationService;
    private final EconomyCoordinator economyCoordinator;
    private final NetworkCoordinator networkCoordinator;
    private final ListenerRegistrar listenerRegistrar;
    private EzRtpConfiguration configuration;
    private ForceRtpConfiguration forceRtpConfiguration;
    private RandomTeleportService teleportService;
    private PerformanceSettings performanceSettings;
    private PerformanceMonitor performanceMonitor;
    private UnsafeLocationSettings unsafeLocationSettings;
    private UnsafeLocationMonitor unsafeLocationMonitor;
    private EconomyService economyService = EconomyService.disabled();
    private RtpUsageStorage usageStorage;
    private ProtectionRegistry protectionRegistry;
    private MessageProvider messageProvider;
    private ChunkyProvider chunkyAPI;
    private EzRtpMetricsRegistrar metricsRegistrar;
    private UsageResetScheduler usageResetScheduler;
    private final HeatmapSimulationStore heatmapSimulationStore = new HeatmapSimulationStore();
    private com.skyblockexp.ezrtp.teleport.ChunkyWarmupCoordinator chunkyWarmupCoordinator;
    private PvpTagService pvpTagService;
    private TeamsApiSubcommandBridge teamsApiSubcommandBridge;
    private EzCountdownDisplayBridge ezCountdownBridge;

    public EzRtpPluginBootstrap(EzRtpPlugin plugin) {
        this.plugin = plugin;
        this.configurationService = new ConfigurationService(plugin);
        this.economyCoordinator = new EconomyCoordinator(plugin);
        this.networkCoordinator = new NetworkCoordinator(plugin);
        this.listenerRegistrar = new ListenerRegistrar(plugin);
        this.chunkyWarmupCoordinator = new com.skyblockexp.ezrtp.teleport.ChunkyWarmupCoordinator();
    }

    public void enable() {
        plugin.saveDefaultConfig();
        configurationService.ensureAdditionalConfigFiles();

        RuntimeArtifactStatus runtimeArtifactStatus = inspectRuntimeArtifacts();
        if (!runtimeArtifactStatus.unsupportedModules().isEmpty()) {
            String unsupported = String.join(", ", runtimeArtifactStatus.unsupportedModules());
            plugin.getLogger().severe("Unsupported EzRTP runtime module combination detected: " + unsupported + ".");
            plugin.getLogger().severe("Likely symptoms include duplicate chat messages and duplicate/random double teleports.");
            plugin.getLogger().severe("Remove unsupported EzRTP runtime module jar(s) and keep only 'EzRTP.jar' and optionally one supported module plugin such as 'EzRTPPaperModule'.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        logRuntimeArtifactSummary(runtimeArtifactStatus);
        PlatformAdapterRegistrar.registerKnownProviders(plugin.getLogger());
        ChunkLoadStrategyRegistry.loadAndRegister(plugin, plugin.getLogger());
        PlatformRuntimeRegistry.loadAndRegister(plugin, plugin.getLogger());
        PlatformGuiBridgeRegistry.loadAndRegister(plugin, plugin.getLogger());
        com.skyblockexp.ezrtp.platform.PlatformMessageServiceRegistry.loadAndRegister(plugin, plugin.getLogger());
        com.skyblockexp.ezrtp.platform.PlatformSenderBridgeRegistry.loadAndRegister(plugin, plugin.getLogger());
        logSelectedPlatformAdapters();
        economyService = economyCoordinator.initializeEconomy();
        reloadPluginConfiguration();
        usageStorage = createUsageStorage();
        usageResetScheduler = new UsageResetScheduler(
                plugin, PlatformRuntimeRegistry.get().scheduler(), usageStorage);
        usageResetScheduler.schedule();
        registerListeners();
        registerCommand();
        initializeMetrics();
        new ReleaseUpdateChecker(plugin).checkForUpdates();
        plugin.getLogger().info("Ready.");
    }

    private void logRuntimeArtifactSummary(RuntimeArtifactStatus status) {
        if (status.hasSupportedRuntimeModule()) {
            plugin.getLogger().info("Runtime module: " + String.join(", ", status.supportedRuntimeModules()));
        } else {
            plugin.getLogger().warning("No runtime module installed; using built-in Bukkit adapters.");
        }
    }

    private RuntimeArtifactStatus inspectRuntimeArtifacts() {
        Map<String, Plugin> byName = new LinkedHashMap<>();
        for (Plugin installedPlugin : plugin.getServer().getPluginManager().getPlugins()) {
            String pluginName = installedPlugin.getName();
            if (pluginName == null || pluginName.isBlank()) {
                continue;
            }
            byName.put(pluginName, installedPlugin);
        }

        List<String> installedArtifacts = new ArrayList<>();
        if (byName.containsKey(CORE_PLUGIN_NAME)) {
            installedArtifacts.add(CORE_PLUGIN_NAME);
        }

        List<String> supportedRuntimeModules = new ArrayList<>();
        List<String> unsupportedModules = new ArrayList<>();
        for (String runtimeModuleName : RUNTIME_MODULE_PLUGIN_NAMES) {
            if (!byName.containsKey(runtimeModuleName)) {
                continue;
            }
            installedArtifacts.add(runtimeModuleName);
            if ("EzRTPPaperModule".equals(runtimeModuleName)
                    || "EzRTPPurpurModule".equals(runtimeModuleName)) {
                supportedRuntimeModules.add(runtimeModuleName);
            } else {
                unsupportedModules.add(runtimeModuleName);
            }
        }
        return new RuntimeArtifactStatus(installedArtifacts, supportedRuntimeModules, unsupportedModules);
    }

    private record RuntimeArtifactStatus(
            List<String> installedArtifacts,
            List<String> supportedRuntimeModules,
            List<String> unsupportedModules) {
        private boolean hasSupportedRuntimeModule() {
            return !supportedRuntimeModules.isEmpty();
        }
    }

    public void disable() {
        // cancelTasks is not supported on Folia (regionised scheduler); skip it there.
        if (!PlatformRuntimeCapabilitiesDetector.detect(plugin).regionizedRuntime()) {
            plugin.getServer().getScheduler().cancelTasks(plugin);
        }
        if (unsafeLocationMonitor != null) {
            unsafeLocationMonitor.shutdown();
            unsafeLocationMonitor = null;
        }
        if (performanceMonitor != null) {
            performanceMonitor.shutdown();
            performanceMonitor = null;
        }
        if (teleportService != null) {
            teleportService.shutdown();
            try { EzRtpAPI.unregisterProvider(teleportService); } catch (Throwable ignored) {}
        }
        if (teamsApiSubcommandBridge != null) {
            teamsApiSubcommandBridge.unregister();
            teamsApiSubcommandBridge = null;
        }
        networkCoordinator.shutdown();
        plugin.getLogger().info("EzRTP plugin disabled.");
        com.skyblockexp.ezrtp.platform.PlatformSenderBridgeRegistry.closeAndUnregister();
        com.skyblockexp.ezrtp.platform.PlatformMessageServiceRegistry.closeAndUnregister();
        ChunkLoadStrategyRegistry.unregister();
        PlatformRuntimeRegistry.unregister();
        PlatformGuiBridgeRegistry.unregister();
    }

    public void reloadPluginConfiguration() {
        configuration = configurationService.reloadConfiguration();
        forceRtpConfiguration = configurationService.reloadForceRtpConfiguration();
        
        // Load Chunky API only if Chunky integration is enabled in config
        RandomTeleportSettings defaultSettings = configuration.getDefaultSettings();
        boolean chunkyIntegrationEnabled = defaultSettings.getChunkyIntegrationSettings().isEnabled();

        if (chunkyIntegrationEnabled && chunkyAPI == null) {
            try {
                Class<?> chunkyApiClass = Class.forName("org.popcraft.chunky.api.ChunkyAPI");
                Object loaded = org.bukkit.Bukkit.getServicesManager().load((Class) chunkyApiClass);
                if (loaded != null) {
                    ChunkyProvider provider = ChunkyRuntimeProvider.loadProvider();
                    if (provider != null) {
                        provider.registerListeners(plugin);
                        if (chunkyWarmupCoordinator != null) {
                            chunkyWarmupCoordinator.registerWithChunky(provider, plugin);
                        }
                        this.chunkyAPI = provider;
                    }
                }
            } catch (ClassNotFoundException e) {
                plugin.getLogger().info("Chunky plugin not found - Chunky integration disabled.");
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to initialize Chunky integration: " + e.getMessage());
            }
        } else if (!chunkyIntegrationEnabled && chunkyAPI != null) {
            // Unregister Chunky listeners if integration is disabled
            plugin.getLogger().info("Chunky integration disabled in configuration.");
            chunkyAPI = null;
        }

        // Initialize PvP tag providers once; registered only when the corresponding plugin is present.
        if (pvpTagService == null) {
            pvpTagService = new PvpTagService();
            if (plugin.getServer().getPluginManager().isPluginEnabled("CombatLogX")) {
                pvpTagService.registerProvider(new com.skyblockexp.ezrtp.pvptag.CombatLogXPvpTagProvider());
                plugin.getLogger().info("PvP tag integration: CombatLogX detected.");
            }
            if (plugin.getServer().getPluginManager().isPluginEnabled("PvPManager")) {
                pvpTagService.registerProvider(new com.skyblockexp.ezrtp.pvptag.PvpManagerPvpTagProvider());
                plugin.getLogger().info("PvP tag integration: PvPManager detected.");
            }
            if (plugin.getServer().getPluginManager().isPluginEnabled("CombatLog")) {
                pvpTagService.registerProvider(new com.skyblockexp.ezrtp.pvptag.SimpleCombatLogPvpTagProvider());
                plugin.getLogger().info("PvP tag integration: Simple Combat Log detected.");
            }
        }

        messageProvider = configurationService.getMessageProvider();
        // Initialize EzCountdown integration bridge when EzCountdown is present.
        if (ezCountdownBridge == null
                && plugin.getServer().getPluginManager().isPluginEnabled("EzCountdown")) {
            try {
                ezCountdownBridge = new EzCountdownDisplayBridgeImpl(plugin);
                plugin.getServer().getPluginManager().registerEvents(
                        (org.bukkit.event.Listener) ezCountdownBridge, plugin);
                plugin.getLogger().info("EzCountdown integration enabled.");
            } catch (NoClassDefFoundError e) {
                plugin.getLogger().warning("Failed to initialize EzCountdown integration: " + e.getMessage());
                ezCountdownBridge = null;
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to initialize EzCountdown integration: " + e.getMessage());
                ezCountdownBridge = null;
            }
        }
        if (teleportService != null) {
            teleportService.setEzCountdownBridge(ezCountdownBridge);
        }
        // Initialize text rendering settings (force legacy conversion for older clients)
        boolean forceLegacy = configurationService.getEffectiveBaseConfiguration().getBoolean("messages.force-legacy-colors", false);
        com.skyblockexp.ezrtp.util.MessageUtil.setForceLegacyColors(forceLegacy);
        // Platform message service should handle Audience initialization; registry registration
        // is attempted during enable() when a platform service is available.

        // Configure memory safety for Chunky integration
        if (chunkyWarmupCoordinator != null) {
            var chunkySettings = defaultSettings.getChunkyIntegrationSettings();
            chunkyWarmupCoordinator.configureMemorySafety(
                chunkySettings.isMemorySafetyEnabled(),
                chunkySettings.getMinFreeMemoryMb(),
                chunkySettings.getMaxCoordinatorEntries(),
                chunkySettings.getLowMemoryRetentionMinutes()
            );

            // Log memory safety status
            if (chunkySettings.isMemorySafetyEnabled()) {
                Runtime runtime = Runtime.getRuntime();
                long freeMemoryMb = (runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory())) / (1024L * 1024L);
                plugin.getLogger().info(String.format("Chunky: memory safety enabled (%dMB free, threshold %dMB)",
                    freeMemoryMb, chunkySettings.getMinFreeMemoryMb()));
            }
        }

        if (protectionRegistry == null) {
            protectionRegistry = new ProtectionRegistry(plugin);
        }
        protectionRegistry.warnMissingProviders(defaultSettings.getProtectionSettings());
        if (teleportService == null) {
            teleportService = new RandomTeleportService(plugin, defaultSettings,
                configuration.getQueueSettings(), economyService,
                (player, settings) -> configuration.resolveTeleportCost(player, settings),
                protectionRegistry, messageProvider, ChunkLoadStrategyRegistry.get(), PlatformRuntimeRegistry.get(), chunkyAPI, chunkyWarmupCoordinator, pvpTagService);
            teleportService.setEzCountdownBridge(ezCountdownBridge);
            try { EzRtpAPI.registerProvider(plugin, teleportService); } catch (Throwable ignored) {}
        } else {
            teleportService.reload(defaultSettings, configuration.getQueueSettings());
            teleportService.setEconomyService(economyService);
            teleportService.setCostResolver((player, settings) -> configuration.resolveTeleportCost(player, settings));
            teleportService.setProtectionRegistry(protectionRegistry);            teleportService.setEzCountdownBridge(ezCountdownBridge);            try { EzRtpAPI.registerProvider(plugin, teleportService); } catch (Throwable ignored) {}
        }
        networkCoordinator.reload(configuration);
        RandomTeleportGuiManager guiManager = listenerRegistrar.getGuiManager();
        if (guiManager != null) {
            guiManager.closeAll();
        }
        FactionClaimSelectionGuiManager factionClaimGuiManager = listenerRegistrar.getFactionClaimGuiManager();
        if (factionClaimGuiManager != null) {
            factionClaimGuiManager.closeAll();
        }
        configurationService.validateEconomyConfiguration(configuration, economyService);

        // Reload performance monitoring settings and (re-)create the monitor
        performanceSettings = configurationService.reloadPerformanceConfiguration();
        if (performanceMonitor != null) {
            performanceMonitor.shutdown();
        }
        performanceMonitor = new PerformanceMonitor(
                performanceSettings,
                teleportService.getStatistics(),
                teleportService.getBiomeCache(),
                plugin.getLogger());
        teleportService.setPerformanceMonitor(performanceMonitor);
        performanceMonitor.schedulePeriodicExport(PlatformRuntimeRegistry.get().scheduler());

        // Reload unsafe-location monitoring settings and (re-)create the monitor
        unsafeLocationSettings = configurationService.reloadUnsafeLocationConfiguration();
        if (unsafeLocationMonitor != null) {
            unsafeLocationMonitor.shutdown();
        }
        unsafeLocationMonitor = new UnsafeLocationMonitor(
                unsafeLocationSettings,
                new UnsafeLocationStatistics(),
                teleportService.getStatistics(),
                plugin.getLogger());
        teleportService.setUnsafeLocationMonitor(unsafeLocationMonitor);
        unsafeLocationMonitor.schedulePeriodicExport(PlatformRuntimeRegistry.get().scheduler());
    }

    public MessageProvider getMessageProvider() {
        return messageProvider;
    }

    public RandomTeleportService getTeleportService() {
        return teleportService;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public UnsafeLocationMonitor getUnsafeLocationMonitor() {
        return unsafeLocationMonitor;
    }

    public EzRtpConfiguration getConfiguration() {
        return configuration;
    }

    public ForceRtpConfiguration getForceRtpConfiguration() {
        return forceRtpConfiguration;
    }

    public RtpUsageStorage getUsageStorage() {
        return usageStorage;
    }

    private void initializeMetrics() {
        metricsRegistrar = new EzRtpMetricsRegistrar(plugin, this::getTeleportService);
        metricsRegistrar.register();
    }

    private void logSelectedPlatformAdapters() {
        var runtime = PlatformRuntimeRegistry.get();
        var scheduler = runtime.scheduler();
        var strategy = ChunkLoadStrategyRegistry.get();
        var guiBridge = PlatformGuiBridgeRegistry.get();
        var messageService = com.skyblockexp.ezrtp.platform.PlatformMessageServiceRegistry.get();
        var senderBridge = com.skyblockexp.ezrtp.platform.PlatformSenderBridgeRegistry.get();

        plugin.getLogger().info("Platform: " + runtime.getClass().getSimpleName()
                + " | " + scheduler.getClass().getSimpleName()
                + " | " + strategy.getClass().getSimpleName());
        plugin.getLogger().info("Adapters: GUI=" + guiBridge.getClass().getSimpleName()
                + " | Messages=" + (messageService != null ? messageService.getClass().getSimpleName() : "<none>")
                + " | Sender=" + (senderBridge != null ? senderBridge.getClass().getSimpleName() : "<none>"));
    }

    private RtpUsageStorage createUsageStorage() {
        ConfigurationSection effectiveBase = configurationService.getEffectiveBaseConfiguration();
        String storageType = effectiveBase.getString("rtp-limits.storage", "yaml");
        if (storageType.equalsIgnoreCase("mysql")) {
            String url = effectiveBase.getString("rtp-limits.mysql.url", "jdbc:mysql://localhost:3306/mc");
            String user = effectiveBase.getString("rtp-limits.mysql.user", "root");
            String pass = effectiveBase.getString("rtp-limits.mysql.password", "");
            return new MySqlRtpUsageStorage(url, user, pass);
        }
        return new YamlRtpUsageStorage(new File(plugin.getDataFolder(), "rtp-usage.yml"));
    }


    private void registerCommand() {
        RandomTeleportGuiManager guiManager = listenerRegistrar.getGuiManager();
        var factionClaimGuiManager = listenerRegistrar.getFactionClaimGuiManager();
        RandomTeleportCommand command = new RandomTeleportCommand(plugin, this::getTeleportService,
            this::getConfiguration, this::getProtectionRegistry, guiManager, factionClaimGuiManager,
                usageStorage, heatmapSimulationStore, chunkyAPI, chunkyWarmupCoordinator);
        PluginCommand pluginCommand = Objects.requireNonNull(plugin.getCommand("rtp"), "rtp command not defined in plugin.yml");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        // Register force-rtp command
        ForceRtpCommand forceRtpCommand = new ForceRtpCommand(plugin, this::getTeleportService,
                this::getConfiguration, this::getForceRtpConfiguration);
        PluginCommand forceRtpPluginCommand = Objects.requireNonNull(plugin.getCommand("forcertp"), "forcertp command not defined in plugin.yml");
        forceRtpPluginCommand.setExecutor(forceRtpCommand);
        forceRtpPluginCommand.setTabCompleter(forceRtpCommand);

        teamsApiSubcommandBridge = new TeamsApiSubcommandBridge(plugin, factionClaimGuiManager);
        teamsApiSubcommandBridge.register();
    }

    private void registerListeners() {
        listenerRegistrar.register(this::getTeleportService,
                this::getConfiguration,
                networkCoordinator::getNetworkService,
                this::getMessageProvider,
                usageStorage);
    }

    public Object getChunkyAPI() {
        return chunkyAPI;
    }

    public ProtectionRegistry getProtectionRegistry() {
        return protectionRegistry;
    }

}
