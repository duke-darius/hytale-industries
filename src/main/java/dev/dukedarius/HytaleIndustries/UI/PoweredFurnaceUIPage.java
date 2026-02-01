package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredFurnaceInventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PoweredFurnaceUIPage extends InteractiveCustomUIPage<PoweredFurnaceUIPage.UIEventData> {

    private static final long AUTO_UPDATE_PERIOD_MS = 250L;

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;
    private transient ScheduledFuture<?> autoUpdateTask;

    public PoweredFurnaceUIPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i pos) {
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

        cmd.append("Pages/HytaleIndustries_PoweredFurnace.ui");
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
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        World world = store.getExternalData().getWorld();
        var ctx = resolve(world);
        if (ctx == null || ctx.inv == null) return;

        ContainerWindow win;
        if (UIEventData.ACTION_VIEW_INPUT.equals(data.action)) {
            win = new ContainerWindow(ctx.inv.input);
        } else if (UIEventData.ACTION_VIEW_OUTPUT.equals(data.action)) {
            win = new ContainerWindow(ctx.inv.output);
        } else {
            return;
        }

        // Prevent ack underflow by resetting before switching off this custom page.
        player.getPageManager().clearCustomPageAcknowledgements();
        // Open the furnace container in a single page swap.
        player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, win);
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        var ctx = resolve(world);
        double he = 0.0;
        double heCap = 0.0;
        double progress = 0.0;
        int inputQty = 0;
        int outputQty = 0;
        String inputName = "Empty";
        String outputName = "Empty";
        boolean inputHasItem = false;
        boolean outputHasItem = false;
        if (ctx != null) {
            if (ctx.stores != null) {
                he = ctx.stores.current;
                heCap = ctx.stores.max;
            }
            if (ctx.inv != null && ctx.inv.workRequired > 0f) {
                progress = Math.min(1.0, Math.max(0.0, ctx.inv.currentWork / ctx.inv.workRequired));
            }
            var in = ctx.inv != null ? ctx.inv.input.getItemStack((short) 0) : null;
            if (in != null && !ItemStack.isEmpty(in)) {
                inputQty = in.getQuantity();
                inputName = in.getItemId();
                inputHasItem = true;
            }
            var out = ctx.inv != null ? ctx.inv.output.getItemStack((short) 0) : null;
            if (out != null && !ItemStack.isEmpty(out)) {
                outputName = out.getItemId();
                outputQty = out.getQuantity();
                outputHasItem = true;
            }
        }

        cmd.set("#PowerBar.Value", Math.max(0.0, Math.min(1.0, he / heCap)));
        cmd.set("#PowerBar.TooltipText", String.format("%d/%d HE Stored", (int)he, (int)heCap));
//        cmd.set("#HeText.Text", String.format("HE: %.0f / %.0f", he, heCap));
        if (inputHasItem) {
            cmd.set("#InputSlot.ItemId", inputName);
            cmd.set("#InputQty.Text", String.format("%d", inputQty));
        } else {
            cmd.set("#InputSlot.ItemId", "");
            cmd.set("#InputQty.Text", "");
        }

        if (outputHasItem) {
            cmd.set("#OutputSlot.ItemId", outputName);
            cmd.set("#OutputQty.Text", String.format("%d", outputQty));
        } else {
            cmd.set("#OutputSlot.ItemId", "");
            cmd.set("#OutputQty.Text", "");
        }

        cmd.set("#ProgressBar.Value", Math.max(0.0, Math.min(1.0, progress)));

        bindEvents(events);
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
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("PoweredFurnaceUI: timer tick failed");
                        stopTimer();
                    }
                });
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("PoweredFurnaceUI: schedule failed");
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

    private static void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InputButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_VIEW_INPUT), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OutputButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_VIEW_OUTPUT), false);
    }

    private Context resolve(World world) {
        if (world == null) return null;
        var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return null;
        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return null;
        var inv = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getPoweredFurnaceInventoryType());
        var stores = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getStoresHeType());
        if (inv != null) {
            if (inv.input == null || inv.input.getCapacity() <= 0) {
                inv.input = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                entity.getStore().replaceComponent(entity, HytaleIndustriesPlugin.INSTANCE.getPoweredFurnaceInventoryType(), inv);
            }
            if (inv.output == null || inv.output.getCapacity() <= 0) {
                inv.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                entity.getStore().replaceComponent(entity, HytaleIndustriesPlugin.INSTANCE.getPoweredFurnaceInventoryType(), inv);
            }
        }
        Context ctx = new Context();
        ctx.inv = inv;
        ctx.stores = stores;
        return ctx;
    }

    private static final class Context {
        PoweredFurnaceInventory inv;
        StoresHE stores;
    }

    public static final class UIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_VIEW_INPUT = "ViewInput";
        static final String ACTION_VIEW_OUTPUT = "ViewOutput";

        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
