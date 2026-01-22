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
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.QuarryBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class QuarryUIPage extends InteractiveCustomUIPage<QuarryUIPage.UIEventData> {

    private static final long AUTO_UPDATE_PERIOD_MS = 250L;

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;
    private transient ScheduledFuture<?> autoUpdateTask;

    public QuarryUIPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i pos) {
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

        cmd.append("Pages/HytaleIndustries_Quarry.ui");
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
        World world = store.getExternalData().getWorld();
        BlockState st = world.getState(x, y, z, true);
        if (!(st instanceof QuarryBlockState quarry)) {
            return;
        }

        switch (data.action) {
            case UIEventData.ACTION_START -> quarry.startMining();
            case UIEventData.ACTION_RESET -> quarry.reset();
            case UIEventData.ACTION_WIDTH_INC -> quarry.setWidth(quarry.getWidth() + 1);
            case UIEventData.ACTION_WIDTH_DEC -> quarry.setWidth(quarry.getWidth() - 1);
            case UIEventData.ACTION_DEPTH_INC -> quarry.setDepth(quarry.getDepth() + 1);
            case UIEventData.ACTION_DEPTH_DEC -> quarry.setDepth(quarry.getDepth() - 1);
            case UIEventData.ACTION_CLOSE -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    try {
                        player.getPageManager().handleEvent(ref, store, new CustomPageEvent(CustomPageEventType.Dismiss, ""));
                    } catch (Throwable t) {
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("QuarryUI: failed to dismiss page");
                    }
                }
                return;
            }
        }

        // Re-render immediately after action.
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        BlockState st = world.getState(x, y, z, true);
        
        if (st instanceof QuarryBlockState quarry) {
            boolean isIdle = quarry.currentStatus == QuarryBlockState.QuarryStatus.IDLE;
            
            cmd.set("#StatusLabel.Text", quarry.currentStatus.name());

            String yText = "-";
            if (quarry.getCurrentPos() != null) {
                yText = String.valueOf(quarry.getCurrentPos().y);
            }
            cmd.set("#YLevelLabel.Text", yText);

            cmd.set("#WidthText.Text", String.valueOf(quarry.getWidth()));
            cmd.set("#DepthText.Text", String.valueOf(quarry.getDepth()));

            // Power bar
            double he = quarry.getHeStored();
            double heCap = quarry.getHeCapacity();
            cmd.set("#PowerBar.Value", Math.max(0.0, Math.min(1.0, he / heCap)));
            cmd.set("#PowerBar.TooltipText", String.format("%d/%d HE Stored", (int) he, (int) heCap));
            cmd.set("#PowerText.Text", String.format("%d/%d HE", (int) he, (int) heCap));

            // Visibility logic
            cmd.set("#ConfigGroup.Visible", isIdle);
            cmd.set("#StartButton.Visible", isIdle);
            cmd.set("#ResetButton.Visible", !isIdle);
        }

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
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("QuarryUI: timer tick failed");
                        stopTimer();
                    }
                });
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("QuarryUI: schedule failed");
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#StartButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_START), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_RESET), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WidthInc",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_WIDTH_INC), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WidthDec",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_WIDTH_DEC), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepthInc",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_DEPTH_INC), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepthDec",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_DEPTH_DEC), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_CLOSE), false);
    }

    public static final class UIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_START = "Start";
        static final String ACTION_RESET = "Reset";
        static final String ACTION_CLOSE = "Close";
        static final String ACTION_WIDTH_INC = "WidthInc";
        static final String ACTION_WIDTH_DEC = "WidthDec";
        static final String ACTION_DEPTH_INC = "DepthInc";
        static final String ACTION_DEPTH_DEC = "DepthDec";

        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
