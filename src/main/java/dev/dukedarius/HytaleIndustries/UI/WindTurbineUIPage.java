package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.WindTurbineComponent;
import dev.dukedarius.HytaleIndustries.Energy.PowerUtils;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WindTurbineUIPage extends InteractiveCustomUIPage<WindTurbineUIPage.UIEventData> {

    private static final long AUTO_UPDATE_PERIOD_MS = 250L;

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;
    private transient ScheduledFuture<?> autoUpdateTask;

    public WindTurbineUIPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, UIEventData.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        lastRef = ref;
        lastStore = store;
        lastWorld = store.getExternalData().getWorld();

        ensureTimerStarted();

        cmd.append("Pages/HytaleIndustries_WindTurbine.ui");
        render(cmd, events, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopTimer();
        super.onDismiss(ref, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        // No interactive events for Wind Turbine yet.
    }

    private void render(@Nonnull UICommandBuilder cmd,
                        @Nonnull UIEventBuilder events,
                        @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();

        double he = 0.0;
        double heCap = 0.0;
        double production = 0.0;
        double windSpeed = 1.0;

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk != null && HytaleIndustriesPlugin.INSTANCE != null) {
            var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            var storesType = HytaleIndustriesPlugin.INSTANCE.getStoresHeType();
            var turbineType = HytaleIndustriesPlugin.INSTANCE.getWindTurbineComponentType();
            if (entity != null) {
                if (storesType != null) {
                    StoresHE stores = entity.getStore().getComponent(entity, storesType);
                    if (stores != null) {
                        he = stores.current;
                        heCap = stores.max;
                    }
                }
                if (turbineType != null) {
                    WindTurbineComponent turbine = entity.getStore().getComponent(entity, turbineType);
                    if (turbine != null) {
                        production = turbine.lastProductionPerSecond;
                    }
                }
                if (HytaleIndustriesPlugin.INSTANCE != null && HytaleIndustriesPlugin.INSTANCE.getWindManager() != null) {
                    try {
                        windSpeed = HytaleIndustriesPlugin.INSTANCE.getWindManager().getSpeed(world);
                    } catch (Throwable ignored) {
                        windSpeed = 1.0;
                    }
                }
            }
        }

        if (heCap <= 0.0) {
            heCap = 1.0;
        }

        cmd.set("#PowerBar.Value", Math.max(0.0, Math.min(1.0, he / heCap)));
        cmd.set("#PowerBar.TooltipText", String.format("%d/%d HE Stored", (int) he, (int) heCap));
        cmd.set("#PowerText.Text", PowerUtils.formatHe(he) + "/" + PowerUtils.formatHe(heCap) + " HE");

        // Production info
        cmd.set("#ProductionLabel.Text", String.format("%.1f HE/s", production));
        cmd.set("#HeightLabel.Text", "Y=" + y);
        cmd.set("#WindLabel.Text", String.format("Wind: %.2fx", windSpeed));
    }

    private void ensureTimerStarted() {
        if (autoUpdateTask != null) return;

        autoUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                World world = lastWorld;
                if (world == null) return;
                world.execute(() -> {
                    try {
                        timerTickOnWorldThread();
                    } catch (Throwable t) {
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("WindTurbineUI: timer tick failed");
                        stopTimer();
                    }
                });
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("WindTurbineUI: schedule failed");
                stopTimer();
            }
        }, AUTO_UPDATE_PERIOD_MS, AUTO_UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        ScheduledFuture<?> t = autoUpdateTask;
        autoUpdateTask = null;
        if (t != null) t.cancel(false);
        lastRef = null;
        lastStore = null;
        lastWorld = null;
    }

    private void timerTickOnWorldThread() {
        Ref<EntityStore> ref = lastRef;
        Store<EntityStore> store = lastStore;
        World world = lastWorld;
        if (ref == null || store == null || world == null) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (player.getPageManager().getCustomPage() != this) return;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .build();
    }
}
