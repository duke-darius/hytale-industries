package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESControllerComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Systems.EnergizedStorage.ESNetworkSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3i;

public class ESControllerUIPage extends InteractiveCustomUIPage<ESControllerUIPage.EventData> {
    private final int x, y, z;

    public ESControllerUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
        this.x = pos.x; this.y = pos.y; this.z = pos.z;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
                      @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events,
                      @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_ESController.ui");
        render(cmd, store);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
                                @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl EventData data) {
        // read-only UI, no events to handle
    }

    private void render(@NonNullDecl UICommandBuilder cmd, @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            cmd.set("#StatusValue.Text", "No world");
            return;
        }

        var chunkStore = world.getChunkStore().getStore();
        ESControllerComponent ctrl = ESNetworkSystem.findController(
                world, chunkStore, x, y, z,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType(),
                HytaleIndustriesPlugin.INSTANCE.getEsControllerType());

        if (ctrl == null) {
            cmd.set("#StatusValue.Text", "Not Found");
            return;
        }

        // Status
        cmd.set("#StatusValue.Text", ctrl.networkOnline ? "Online" : "Offline");

        // Power
        cmd.set("#PowerValue.Text", String.format("%d / %d HE", ctrl.energyStored, ctrl.energyMax));
        cmd.set("#PowerUsageValue.Text", String.format("%d HE/t", ctrl.totalPowerUsage));

        // Storage
        cmd.set("#StorageValue.Text", String.format("%d / %d items", ctrl.totalStored, ctrl.maxCapacity));

        // Devices
        cmd.set("#ControllersValue.Text", "1");
        cmd.set("#GridsValue.Text", String.valueOf(ctrl.gridCount));
        cmd.set("#HousingsValue.Text", String.valueOf(ctrl.diskHousingCount));
        cmd.set("#DisksValue.Text", String.valueOf(ctrl.totalDiskCount));

        // Item types
        cmd.set("#ItemTypesValue.Text", String.valueOf(ctrl.itemIndex.size()));
    }

    public static final class EventData {
        public String action;
        public static final BuilderCodec<EventData> CODEC =
                BuilderCodec.builder(EventData.class, EventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (o, v) -> o.action = v, o -> o.action).add()
                        .build();
    }
}
