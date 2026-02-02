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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.AlloySmelterInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Custom UI page for the Alloy Smelter machine.
 * Shows two input slots, one output, HE bar and progress.
 */
public class AlloySmelterUIPage extends InteractiveCustomUIPage<AlloySmelterUIPage.UIEventData> {

    private static final long AUTO_UPDATE_PERIOD_MS = 250L;

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;
    private transient ScheduledFuture<?> autoUpdateTask;

    public AlloySmelterUIPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i pos) {
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

        cmd.append("Pages/HytaleIndustries_AlloySmelter.ui");
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
        if (UIEventData.ACTION_VIEW_INPUT_A.equals(data.action)) {
            win = new ContainerWindow(ctx.inv.inputA);
        } else if (UIEventData.ACTION_VIEW_INPUT_B.equals(data.action)) {
            win = new ContainerWindow(ctx.inv.inputB);
        } else if (UIEventData.ACTION_VIEW_OUTPUT.equals(data.action)) {
            win = new ContainerWindow(ctx.inv.output);
        } else {
            return;
        }

        // Prevent ack underflow by resetting before switching off this custom page.
        player.getPageManager().clearCustomPageAcknowledgements();
        player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, win);
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        var ctx = resolve(world);
        double he = 0.0;
        double heCap = 0.0;
        double progress = 0.0;
        int inputQtyA = 0;
        int inputQtyB = 0;
        int outputQty = 0;
        String inputNameA = "Empty";
        String inputNameB = "Empty";
        String outputName = "Empty";
        boolean inputHasA = false;
        boolean inputHasB = false;
        boolean outputHas = false;
        if (ctx != null) {
            if (ctx.stores != null) {
                he = ctx.stores.current;
                heCap = ctx.stores.max;
            }
            if (ctx.inv != null && ctx.inv.workRequired > 0f) {
                progress = Math.min(1.0, Math.max(0.0, ctx.inv.currentWork / ctx.inv.workRequired));
            }
            if (ctx.inv != null) {
                if (ctx.inv.inputA != null) {
                    ItemStack inA = ctx.inv.inputA.getItemStack((short) 0);
                    if (inA != null && !ItemStack.isEmpty(inA)) {
                        inputQtyA = inA.getQuantity();
                        inputNameA = inA.getItemId();
                        inputHasA = true;
                    }
                }
                if (ctx.inv.inputB != null) {
                    ItemStack inB = ctx.inv.inputB.getItemStack((short) 0);
                    if (inB != null && !ItemStack.isEmpty(inB)) {
                        inputQtyB = inB.getQuantity();
                        inputNameB = inB.getItemId();
                        inputHasB = true;
                    }
                }
            }
            if (ctx.inv != null && ctx.inv.output != null) {
                ItemStack out = ctx.inv.output.getItemStack((short) 0);
                if (out != null && !ItemStack.isEmpty(out)) {
                    outputName = out.getItemId();
                    outputQty = out.getQuantity();
                    outputHas = true;
                }
            }
        }

        cmd.set("#PowerBar.Value", heCap > 0 ? Math.max(0.0, Math.min(1.0, he / heCap)) : 0.0);
        cmd.set("#PowerBar.TooltipText", String.format("%d/%d HE Stored", (int) he, (int) heCap));

        if (inputHasA) {
            cmd.set("#InputSlotA.ItemId", inputNameA);
            cmd.set("#InputQtyA.Text", String.format("%d", inputQtyA));
        } else {
            cmd.set("#InputSlotA.ItemId", "");
            cmd.set("#InputQtyA.Text", "");
        }

        if (inputHasB) {
            cmd.set("#InputSlotB.ItemId", inputNameB);
            cmd.set("#InputQtyB.Text", String.format("%d", inputQtyB));
        } else {
            cmd.set("#InputSlotB.ItemId", "");
            cmd.set("#InputQtyB.Text", "");
        }

        if (outputHas) {
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
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("AlloySmelterUI: timer tick failed");
                        stopTimer();
                    }
                });
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("AlloySmelterUI: schedule failed");
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InputButtonA",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_VIEW_INPUT_A), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InputButtonB",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_VIEW_INPUT_B), false);
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
        var inv = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getAlloySmelterInventoryType());
        var stores = entity.getStore().getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getStoresHeType());
        if (inv != null) {
            boolean dirty = false;
            if (inv.inputA == null || inv.inputA.getCapacity() <= 0) {
                inv.inputA = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                dirty = true;
            }
            if (inv.inputB == null || inv.inputB.getCapacity() <= 0) {
                inv.inputB = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                dirty = true;
            }
            if (inv.output == null || inv.output.getCapacity() <= 0) {
                inv.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                dirty = true;
            }
            if (dirty) {
                entity.getStore().replaceComponent(entity, HytaleIndustriesPlugin.INSTANCE.getAlloySmelterInventoryType(), inv);
            }
        }
        Context ctx = new Context();
        ctx.inv = inv;
        ctx.stores = stores;
        return ctx;
    }

    private static final class Context {
        AlloySmelterInventory inv;
        StoresHE stores;
    }

    public static final class UIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_VIEW_INPUT_A = "ViewInputA";
        static final String ACTION_VIEW_INPUT_B = "ViewInputB";
        static final String ACTION_VIEW_OUTPUT = "ViewOutput";

        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
