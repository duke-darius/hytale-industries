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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.ChunkLoading.ChunkLoaderComponent;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;

public class ChunkLoaderUIPage extends com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage<ChunkLoaderUIPage.ChunkLoaderUIEventData> {

    private final int x;
    private final int y;
    private final int z;

    public ChunkLoaderUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ChunkLoaderUIEventData.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(
            @NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl UICommandBuilder cmd,
            @NonNullDecl UIEventBuilder events,
            @NonNullDecl Store<EntityStore> store
    ) {
        cmd.append("Pages/HytaleIndustries_ChunkLoader.ui");
        render(cmd, events, store);
    }

    @Override
    public void handleDataEvent(
            @NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl Store<EntityStore> store,
            @NonNullDecl ChunkLoaderUIEventData data
    ) {
        if (data.action == null) {
            return;
        }

        ChunkLoaderComponent.LoadingMode newMode =
                ChunkLoaderUIEventData.ACTION_SET_ACTIVE.equals(data.action)
                        ? ChunkLoaderComponent.LoadingMode.Active
                        : ChunkLoaderUIEventData.ACTION_SET_BACKGROUND.equals(data.action)
                        ? ChunkLoaderComponent.LoadingMode.Background
                        : null;
        if (newMode == null) return;

        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) {
            return;
        }

        int lx = x & 31;
        int lz = z & 31;
        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef == null) return;

        var type = dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.INSTANCE.getChunkLoaderComponentType();
        if (type == null) return;

        ChunkLoaderComponent st = stateRef.getStore().getComponent(stateRef, type);
        if (st == null) return;

        st.loadingMode = newMode;
        stateRef.getStore().replaceComponent(stateRef, type, st);
        chunk.markNeedsSaving();

        // Update UI.
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    private void render(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        ChunkLoaderComponent.LoadingMode mode = ChunkLoaderComponent.LoadingMode.Background;

        World world = store.getExternalData().getWorld();
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            var ref = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            var type = dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.INSTANCE.getChunkLoaderComponentType();
            if (ref != null && type != null) {
                ChunkLoaderComponent comp = ref.getStore().getComponent(ref, type);
                if (comp != null) {
                    mode = comp.loadingMode;
                }
            }
        }

        cmd.set("#ModeText.Text", "Mode: " + mode.name());

        // Simple highlighting.
        String bg = mode == ChunkLoaderComponent.LoadingMode.Background ? "#2D7D46" : "#404040";
        String ac = mode == ChunkLoaderComponent.LoadingMode.Active ? "#2D7D46" : "#404040";
        cmd.set("#BackgroundButton.Background", bg);
        cmd.set("#ActiveButton.Background", ac);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackgroundButton",
                new EventData().append(ChunkLoaderUIEventData.KEY_ACTION, ChunkLoaderUIEventData.ACTION_SET_BACKGROUND),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ActiveButton",
                new EventData().append(ChunkLoaderUIEventData.KEY_ACTION, ChunkLoaderUIEventData.ACTION_SET_ACTIVE),
                false
        );
    }

    public static final class ChunkLoaderUIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_SET_BACKGROUND = "SetBackground";
        static final String ACTION_SET_ACTIVE = "SetActive";

        public static final BuilderCodec<ChunkLoaderUIEventData> CODEC = BuilderCodec.builder(
                        ChunkLoaderUIEventData.class,
                        ChunkLoaderUIEventData::new
                )
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        @Nullable
        private String action;
    }
}
