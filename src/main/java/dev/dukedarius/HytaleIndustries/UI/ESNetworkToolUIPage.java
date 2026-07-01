package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.*;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3i;

public class ESNetworkToolUIPage extends InteractiveCustomUIPage<ESNetworkToolUIPage.ToolEventData> {
    private final int x, y, z;

    public ESNetworkToolUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ToolEventData.CODEC);
        this.x = pos.x; this.y = pos.y; this.z = pos.z;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
                      @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events,
                      @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_ESNetworkTool.ui");
        render(cmd, events, store);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
                                @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl ToolEventData data) {
        if (data.action == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        var resolved = resolveBlock(world);
        if (resolved == null) return;

        if ("increase".equals(data.action)) {
            resolved.member.priority++;
        } else if ("decrease".equals(data.action)) {
            resolved.member.priority--;
        }

        resolved.blockStore.replaceComponent(resolved.entity,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType(), resolved.member);

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    private void render(@NonNullDecl UICommandBuilder cmd,
                        @NonNullDecl UIEventBuilder events,
                        @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) { cmd.set("#DeviceType.Text", "No world"); return; }

        var resolved = resolveBlock(world);
        if (resolved == null) {
            cmd.set("#DeviceType.Text", "No ES Device");
            return;
        }

        // Device type
        String deviceType = "ES Network Member";
        if (resolved.blockStore.getComponent(resolved.entity,
                HytaleIndustriesPlugin.INSTANCE.getEsControllerType()) != null) {
            deviceType = "ES Controller";
        } else if (resolved.blockStore.getComponent(resolved.entity,
                HytaleIndustriesPlugin.INSTANCE.getEsDiskHousingType()) != null) {
            deviceType = "ES Disk Housing";
        } else if (resolved.blockStore.getComponent(resolved.entity,
                HytaleIndustriesPlugin.INSTANCE.getEsGridType()) != null) {
            deviceType = "ES Grid Terminal";
        }

        cmd.set("#DeviceType.Text", deviceType);
        cmd.set("#PosValue.Text", String.format("(%d, %d, %d)", x, y, z));
        cmd.set("#PriorityValue.Text", String.valueOf(resolved.member.priority));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#IncreaseBtn",
                new EventData().append(ToolEventData.KEY_ACTION, "increase"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DecreaseBtn",
                new EventData().append(ToolEventData.KEY_ACTION, "decrease"), false);
    }

    private ResolvedBlock resolveBlock(World world) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return null;

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return null;

        var blockStore = entity.getStore();
        ESNetworkMemberComponent member = blockStore.getComponent(entity,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType());
        if (member == null) return null;

        return new ResolvedBlock(entity, blockStore, member);
    }

    private record ResolvedBlock(Ref<ChunkStore> entity, Store<ChunkStore> blockStore,
                                  ESNetworkMemberComponent member) {}

    public static final class ToolEventData {
        static final String KEY_ACTION = "Action";
        public String action;

        public static final BuilderCodec<ToolEventData> CODEC =
                BuilderCodec.builder(ToolEventData.class, ToolEventData::new)
                        .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                                (o, v) -> o.action = v, o -> o.action).add()
                        .build();
    }
}
