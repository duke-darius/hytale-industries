package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.SmallBatteryBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SmallBatteryUIPage extends InteractiveCustomUIPage<SmallBatteryUIPage.SmallBatteryUIEventData> {

    private static final long MAX_HE = 1_000_000L;
    private static final long AUTO_UPDATE_PERIOD_MS = 250L; // 4 Hz

    private final int x;
    private final int y;
    private final int z;

    private transient Ref<EntityStore> lastRef;
    private transient Store<EntityStore> lastStore;
    private transient World lastWorld;

    private transient ScheduledFuture<?> autoUpdateTask;
    private transient long lastSentHeInt = Long.MIN_VALUE;

    public SmallBatteryUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, SmallBatteryUIEventData.CODEC);
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

        uiCommandBuilder.append("Pages/HytaleIndustries_SmallBattery.ui");
        render(uiCommandBuilder, uiEventBuilder, store);
    }

    @Override
    public void onDismiss(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
        stopTimer();
        super.onDismiss(ref, store);
    }

    private void render(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();

        double he = 0.0;
        BlockState state = world.getState(x, y, z, true);
        if (state instanceof SmallBatteryBlockState battery) {
            he = battery.getHeStored();
        }

        setHeText(cmd, he);
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

                        // Store access must happen on the owning World thread.
                        world.execute(() -> {
                            try {
                                timerTickOnWorldThread();
                            } catch (Throwable t) {
                                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("SmallBatteryUI: timer tick crashed");
                            }
                        });
                    } catch (Throwable t) {
                        HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("SmallBatteryUI: timer scheduling crashed");
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

        if (player.getPageManager().getCustomPage() != this) {
            return;
        }

        double he = 0.0;
        BlockState state = world.getState(x, y, z, true);
        if (state instanceof SmallBatteryBlockState battery) {
            he = battery.getHeStored();
        }

        long heInt = toDisplayHeInt(he);
        if (heInt == lastSentHeInt) {
            return;
        }
        lastSentHeInt = heInt;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        cmd.set("#HeText.Text", "HE: " + heInt + " / " + MAX_HE);
        sendUpdate(cmd, events, false);
    }

    public static final class SmallBatteryUIEventData {
        public static final BuilderCodec<SmallBatteryUIEventData> CODEC = BuilderCodec.builder(
                        SmallBatteryUIEventData.class,
                        SmallBatteryUIEventData::new
                )
                .build();
    }
}
