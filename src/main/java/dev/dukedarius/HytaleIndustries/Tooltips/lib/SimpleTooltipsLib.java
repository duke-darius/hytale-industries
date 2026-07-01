package dev.dukedarius.HytaleIndustries.Tooltips.lib;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.SimpleTooltipsApi;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.SimpleTooltipsApiProvider;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.TooltipProvider;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.internal.TooltipPacketAdapter;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.internal.TooltipRegistry;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.internal.VirtualItemRegistry;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.internal.GlobalTooltipManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Tooltip system initialization — merged from SimpleTooltipsLib into HytaleIndustries.
 * Call {@link #initialize(JavaPlugin)} from your plugin's setup() method.
 */
public final class SimpleTooltipsLib {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static boolean initialized = false;

    private SimpleTooltipsLib() {}

    public static void initialize(@Nonnull JavaPlugin plugin) {
        if (initialized) return;

        LOGGER.atInfo().log("[Tooltips] Initializing tooltip system...");

        TooltipRegistry tooltipRegistry = new TooltipRegistry();
        VirtualItemRegistry virtualItemRegistry = new VirtualItemRegistry();
        GlobalTooltipManager globalTooltipManager = new GlobalTooltipManager(virtualItemRegistry);
        TooltipPacketAdapter packetAdapter = new TooltipPacketAdapter(virtualItemRegistry, tooltipRegistry, globalTooltipManager);

        packetAdapter.register();

        SimpleTooltipsApi api = new ApiImpl(tooltipRegistry, virtualItemRegistry, packetAdapter, globalTooltipManager);
        SimpleTooltipsApiProvider.register(api);

        plugin.getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent.class,
                event -> packetAdapter.onPlayerLeave(event.getPlayerRef().getUuid()));

        plugin.getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent.class,
                event -> globalTooltipManager.sendAllUpdates(event.getPlayerRef()));

        plugin.getEventRegistry().register((short) 0,
                com.hypixel.hytale.server.core.asset.LoadAssetEvent.class,
                event -> virtualItemRegistry.reserveQualitySlots());

        initialized = true;
        LOGGER.atInfo().log("[Tooltips] Tooltip system initialized");
    }

    private static class ApiImpl implements SimpleTooltipsApi {
        private final TooltipRegistry registry;
        private final VirtualItemRegistry virtualItemRegistry;
        private final TooltipPacketAdapter packetAdapter;
        private final GlobalTooltipManager globalTooltipManager;

        ApiImpl(TooltipRegistry registry, VirtualItemRegistry virtualItemRegistry,
                TooltipPacketAdapter packetAdapter, GlobalTooltipManager globalTooltipManager) {
            this.registry = registry;
            this.virtualItemRegistry = virtualItemRegistry;
            this.packetAdapter = packetAdapter;
            this.globalTooltipManager = globalTooltipManager;
        }

        @Override public void setLanguageResolver(@Nullable LanguageResolver resolver) { packetAdapter.setLanguageResolver(resolver); }
        @Override public void registerProvider(@Nonnull TooltipProvider provider) { registry.registerProvider(provider); }
        @Override public boolean unregisterProvider(@Nonnull String id) { return registry.unregisterProvider(id); }
        @Override public void addGlobalLine(@Nonnull String id, @Nonnull String line) { globalTooltipManager.addGlobalLine(id, line); refreshAllPlayers(); }
        @Override public void addGlobalTranslationLine(@Nonnull String id, @Nonnull String key) { globalTooltipManager.addGlobalTranslationLine(id, key); refreshAllPlayers(); }
        @Override public void replaceGlobalTooltip(@Nonnull String id, @Nonnull String... lines) { globalTooltipManager.replaceGlobalTooltip(id, lines); refreshAllPlayers(); }
        @Override public void replaceGlobalTranslationTooltip(@Nonnull String id, @Nonnull String... keys) { globalTooltipManager.replaceGlobalTranslationTooltip(id, keys); refreshAllPlayers(); }
        @Override public void clearGlobalTooltips(@Nonnull String id) { globalTooltipManager.clearGlobalTooltips(id); refreshAllPlayers(); }
        @Override public void addItemGlobalLine(@Nonnull String id, @Nonnull String line) { globalTooltipManager.addItemGlobalLine(id, line); refreshAllPlayers(); }
        @Override public void addItemGlobalTranslationLine(@Nonnull String id, @Nonnull String key) { globalTooltipManager.addItemGlobalTranslationLine(id, key); refreshAllPlayers(); }
        @Override public void replaceItemGlobalTooltip(@Nonnull String id, @Nonnull String... lines) { globalTooltipManager.replaceItemGlobalTooltip(id, lines); refreshAllPlayers(); }
        @Override public void replaceItemGlobalTranslationTooltip(@Nonnull String id, @Nonnull String... keys) { globalTooltipManager.replaceItemGlobalTranslationTooltip(id, keys); refreshAllPlayers(); }
        @Override public void clearItemGlobalTooltips(@Nonnull String id) { globalTooltipManager.clearItemGlobalTooltips(id); refreshAllPlayers(); }
        @Override public void invalidatePlayer(@Nonnull UUID uuid) { registry.clearCache(); virtualItemRegistry.invalidatePlayer(uuid); packetAdapter.invalidatePlayer(uuid); }
        @Override public void invalidateAll() { registry.clearCache(); virtualItemRegistry.clearCache(); packetAdapter.invalidateAllPlayers(); }
        @Override public void refreshPlayer(@Nonnull UUID uuid) { invalidatePlayer(uuid); packetAdapter.refreshPlayer(uuid); }
        @Override public void refreshAllPlayers() { invalidateAll(); packetAdapter.refreshAllPlayers(); }
    }
}
