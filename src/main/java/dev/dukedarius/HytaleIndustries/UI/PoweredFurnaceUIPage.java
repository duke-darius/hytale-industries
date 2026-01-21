package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
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
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.PoweredFurnaceBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PoweredFurnaceUIPage extends InteractiveCustomUIPage<PoweredFurnaceUIPage.UIEventData> {

    private static final long AUTO_UPDATE_PERIOD_MS = 250L;
    private static final int PROGRESS_BAR_WIDTH = 200;

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

        // Close the custom page before opening containers.
        try {
            player.getPageManager().handleEvent(ref, store, new CustomPageEvent(CustomPageEventType.Dismiss, ""));
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("PoweredFurnaceUI: failed to dismiss page");
        }

        World world = store.getExternalData().getWorld();
        BlockState st = world.getState(x, y, z, true);
        if (!(st instanceof PoweredFurnaceBlockState pf)) {
            return;
        }

        ContainerWindow win;
        if (UIEventData.ACTION_VIEW_INPUT.equals(data.action)) {
            win = new ContainerWindow(pf.getInputContainer());
        } else if (UIEventData.ACTION_VIEW_OUTPUT.equals(data.action)) {
            win = new ContainerWindow(pf.getOutputContainer());
        } else {
            return;
        }

        ContainerWindow playerWin = new ContainerWindow(player.getInventory().getStorage());
        player.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, playerWin, win);
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        BlockState st = world.getState(x, y, z, true);
        double he = 0.0;
        double heCap = 0.0;
        double progress = 0.0;
        int inputQty = 0;
        int outputQty = 0;
        String inputName = "Empty";
        String outputName = "Empty";
        if (st instanceof PoweredFurnaceBlockState pf) {
            he = pf.getHeStored();
            heCap = pf.getHeCapacity();
            progress = pf.getProgressPercent();
            var in = pf.getInputStack();
            if (in != null && !ItemStack.isEmpty(in)) {
                inputQty = in.getQuantity();
                inputName = in.getItemId();
            }
            var out = pf.getOutputStack();
            if (out != null && !ItemStack.isEmpty(out)) {
                outputName = out.getItemId();
                outputQty = out.getQuantity();
            }
        }

        cmd.set("#HeText.Text", String.format("HE: %.0f / %.0f", he, heCap));
        if(inputQty > 0){
            cmd.set("#InputSlot.ItemId", inputName);
            cmd.set("#InputSlot.Quantity", (int) inputQty);
        }
        if(outputQty > 0){
            cmd.set("#OutputSlot.ItemId", outputName);
            cmd.set("#OutputSlot.Quantity", (int) outputQty);
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
