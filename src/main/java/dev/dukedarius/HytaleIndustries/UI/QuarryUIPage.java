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
import dev.dukedarius.HytaleIndustries.Energy.PowerUtils;
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
        render(cmd, events, store, true);
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

        boolean didSomething = false;

        if (data.widthValue != null) {
            try {
                quarry.setWidth(Integer.parseInt(data.widthValue));
                didSomething = true;
            } catch (NumberFormatException ignored) {
                didSomething = true;
            }
        }

        if (data.depthValue != null) {
            try {
                quarry.setDepth(Integer.parseInt(data.depthValue));
                didSomething = true;
            } catch (NumberFormatException ignored) {
                didSomething = true;
            }
        }

        if (data.yStartValue != null) {
            try {
                quarry.setYStart(Integer.parseInt(data.yStartValue));
                didSomething = true;
            } catch (NumberFormatException ignored) {
                didSomething = true;
            }
        }

        if (data.action != null) {
            switch (data.action) {
                case UIEventData.ACTION_START -> {
                    quarry.startMining();
                    didSomething = true;
                }
                case UIEventData.ACTION_RESET -> {
                    quarry.reset();
                    didSomething = true;
                }
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
        }

        if (!didSomething) {
            return;
        }

        // Re-render immediately after any action/change.
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(cmd, events, store, true);
        sendUpdate(cmd, events, false);
    }

    private void render(@Nonnull UICommandBuilder cmd,
                        @Nonnull UIEventBuilder events,
                        @Nonnull Store<EntityStore> store,
                        boolean includeInputValues) {
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

            // NOTE: Avoid spamming Value updates while idle so players can type without the auto-update timer
            // constantly overwriting their input.
            if (includeInputValues) {
                cmd.set("#WidthValue.Value", String.valueOf(quarry.getWidth()));
                cmd.set("#DepthValue.Value", String.valueOf(quarry.getDepth()));
                cmd.set("#YStartValue.Value", String.valueOf(quarry.getYStart()));
            }

            // Power bar
            double he = quarry.getHeStored();
            double heCap = quarry.getHeCapacity();
            cmd.set("#PowerBar.Value", Math.max(0.0, Math.min(1.0, he / heCap)));
            cmd.set("#PowerBar.TooltipText", String.format("%d/%d HE Stored", (int) he, (int) heCap));
            cmd.set("#PowerText.Text", PowerUtils.formatHe(he) + "/" + PowerUtils.formatHe(heCap) + " HE");

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
        render(cmd, events, store, false);
        sendUpdate(cmd, events, false);
    }

    private static void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#StartButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_START), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_RESET), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#WidthValue",
                EventData.of(UIEventData.KEY_WIDTH_VALUE, "#WidthValue.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DepthValue",
                EventData.of(UIEventData.KEY_DEPTH_VALUE, "#DepthValue.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#YStartValue",
                EventData.of(UIEventData.KEY_Y_START_VALUE, "#YStartValue.Value"), false);

    }

    public static final class UIEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_WIDTH_VALUE = "@Width";
        static final String KEY_DEPTH_VALUE = "@Depth";
        static final String KEY_Y_START_VALUE = "@YStart";

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
                .append(new KeyedCodec<>(KEY_WIDTH_VALUE, Codec.STRING), (d, v) -> d.widthValue = v, d -> d.widthValue)
                .add()
                .append(new KeyedCodec<>(KEY_DEPTH_VALUE, Codec.STRING), (d, v) -> d.depthValue = v, d -> d.depthValue)
                .add()
                .append(new KeyedCodec<>(KEY_Y_START_VALUE, Codec.STRING), (d, v) -> d.yStartValue = v, d -> d.yStartValue)
                .add()
                .build();

        @javax.annotation.Nullable
        private String action;

        @javax.annotation.Nullable
        private String widthValue;

        @javax.annotation.Nullable
        private String depthValue;

        @javax.annotation.Nullable
        private String yStartValue;
    }
}
