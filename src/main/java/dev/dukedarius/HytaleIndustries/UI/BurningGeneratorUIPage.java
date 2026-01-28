package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.dukedarius.HytaleIndustries.Components.Energy.FuelInventory;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BurningGeneratorUIPage extends InteractiveCustomUIPage<BurningGeneratorUIPage.BurningGeneratorUIEventData> {

    private static final long MAX_HE = 10000L;

    // Timer interval.
    private static final long AUTO_UPDATE_PERIOD_MS = 250L;

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;

    private transient ScheduledFuture<?> autoUpdateTask;
    private transient long lastSentHeInt = Long.MIN_VALUE;
    private transient long lastSentProdInt = Long.MIN_VALUE;

    public BurningGeneratorUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, BurningGeneratorUIEventData.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(
            @NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl UICommandBuilder uiCommandBuilder,
            @NonNullDecl UIEventBuilder uiEventBuilder,
            @NonNullDecl Store<EntityStore> store
    ) {
        this.lastRef = ref;
        this.lastStore = store;
        this.lastWorld = store.getExternalData().getWorld();

        ensureTimerStarted();

        uiCommandBuilder.append("Pages/HytaleIndustries_BurningGenerator.ui");
        render(uiCommandBuilder, uiEventBuilder, store);
    }

    @Override
    public void onDismiss(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
        stopTimer();
        super.onDismiss(ref, store);
    }

    @Override
    public void handleDataEvent(
            @NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl Store<EntityStore> store,
            @NonNullDecl BurningGeneratorUIEventData data
    ) {
        HytaleIndustriesPlugin.LOGGER.atInfo().log("BurningGeneratorUI: handleDataEvent action=" + data.action);

        if (!BurningGeneratorUIEventData.ACTION_VIEW_CONTAINER.equals(data.action)) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("BurningGeneratorUI: no Player component");
            return;
        }


        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return;
        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return;
        FuelInventory fuel = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getFuelInventoryType());
        if (fuel == null) return;
        if (fuel.fuelContainer == null) {
            fuel.fuelContainer = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
            entity.getStore().replaceComponent(entity, HytaleIndustriesPlugin.INSTANCE.getFuelInventoryType(), fuel);
        }

        var genWin = new ContainerWindow(fuel.fuelContainer);
        // Reset pending custom-page acknowledgements to avoid underflow when client sends its next ack.
        player.getPageManager().clearCustomPageAcknowledgements();
        boolean ok = player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, false, genWin);
        HytaleIndustriesPlugin.LOGGER.atInfo().log("BurningGeneratorUI: setPageWithWindows(Page.Inventory) ok=" + ok);

        // No fallback; components handle inventory.
    }

    private void render(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();

        double he = 0.0;
        long hePerTick = 0;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            if (entity != null) {
                StoresHE stores = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getStoresHeType());
                if (stores != null) he = stores.current;
                var produces = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getProducesHeType());
                var fuelInv = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getFuelInventoryType());
                boolean hasFuel = false;
                if (fuelInv != null && fuelInv.fuelContainer != null) {
                    try {
                        var stack = fuelInv.fuelContainer.getItemStack((short) 0);
                        hasFuel = stack != null && !com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack);
                    } catch (IllegalArgumentException ignored) { }
                }
                if (produces != null && produces.enabled && hasFuel) {
                    double eff = produces.efficiency > 0 ? produces.efficiency : 1.0;
                    double mult = produces.productionMultiplier > 0 ? produces.productionMultiplier : 1.0;
                    hePerTick = (long) Math.floor(Math.max(0, produces.producedPerTick) * eff * mult);
                }
            }
        }

        setHeText(cmd, he);
        setProdText(cmd, hePerTick);

        bindEvents(events);
    }

    private static void bindEvents(@NonNullDecl UIEventBuilder events) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ViewContainerButton",
                new EventData().append(BurningGeneratorUIEventData.KEY_ACTION, BurningGeneratorUIEventData.ACTION_VIEW_CONTAINER),
                false
        );
    }

    private static long toDisplayHeInt(double he) {
        long heInt = (long) Math.floor(Math.max(0.0, he));
        if (heInt > MAX_HE) {
            heInt = MAX_HE;
        }
        return heInt;
    }

    private void setHeText(@NonNullDecl UICommandBuilder cmd, double he) {
        long heInt = toDisplayHeInt(he);
        cmd.set("#HeText.Text", "HE: " + heInt + " / " + MAX_HE);
    }

    private void setProdText(@NonNullDecl UICommandBuilder cmd, long hePerTick) {
        cmd.set("#ProdText.Text", "Output: " + hePerTick + " HE/t");
    }

    private void ensureTimerStarted() {
        if (autoUpdateTask != null) {
            return;
        }

        autoUpdateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        World world = lastWorld;
                        if (world == null) {
                            return;
                        }

                        world.execute(() -> {
                            try {
                                timerTickOnWorldThread();
                            } catch (Throwable t) {
                                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("BurningGeneratorUI: timerTick crashed");
                            }
                        });
                    } catch (Throwable t) {
                        // Scheduled executors stop repeating if the task throws.
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("BurningGeneratorUI: timer scheduling crashed");
                    }
                },
                AUTO_UPDATE_PERIOD_MS,
                AUTO_UPDATE_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopTimer() {
        ScheduledFuture<?> t = autoUpdateTask;
        autoUpdateTask = null;
        if (t != null) {
            t.cancel(false);
        }
        lastRef = null;
        lastStore = null;
        lastWorld = null;
    }

    private void timerTickOnWorldThread() {
        Ref<EntityStore> ref = lastRef;
        Store<EntityStore> store = lastStore;
        World world = lastWorld;
        if (ref == null || store == null || world == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Only update while we are the active custom page.
        if (player.getPageManager().getCustomPage() != this) {
            return;
        }

        double he = 0.0;
        long hePerTick = 0;
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            if (entity != null) {
                StoresHE stores = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getStoresHeType());
                if (stores != null) he = stores.current;
                var produces = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getProducesHeType());
                if (produces != null && produces.enabled ) {
                    double eff = produces.efficiency > 0 ? produces.efficiency : 1.0;
                    double mult = produces.productionMultiplier > 0 ? produces.productionMultiplier : 1.0;
                    hePerTick = (long) Math.floor(Math.max(0, produces.producedPerTick) * eff * mult);
                }
            }
        }

        long heInt = toDisplayHeInt(he);
        long prodInt = hePerTick;
        if (heInt == lastSentHeInt && prodInt == lastSentProdInt) return;
        lastSentHeInt = heInt;
        lastSentProdInt = prodInt;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        cmd.set("#HeText.Text", "HE: " + heInt + " / " + MAX_HE);
        cmd.set("#ProdText.Text", "Output: " + prodInt + " HE/t");
        bindEvents(events);
        sendUpdate(cmd, events, false);
    }

    public static final class BurningGeneratorUIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_VIEW_CONTAINER = "ViewContainer";

        public static final BuilderCodec<BurningGeneratorUIEventData> CODEC = BuilderCodec.builder(
                        BurningGeneratorUIEventData.class,
                        BurningGeneratorUIEventData::new
                )
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
