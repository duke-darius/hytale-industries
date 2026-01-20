package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.ConnectionState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PipeHUDPage extends InteractiveCustomUIPage<PipeHUDPage.NoEvent> {

    public static class NoEvent {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<NoEvent> CODEC =
                com.hypixel.hytale.codec.builder.BuilderCodec.builder(NoEvent.class, NoEvent::new).build();
    }

    private final int x;
    private final int y;
    private final int z;

    public PipeHUDPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, NoEvent.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/HytaleIndustries_PipeHUD.ui");
        render(uiCommandBuilder, store);
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        ItemPipeBlockState pipe = null;
        int raw = 0;
        String stateId = "n/a";

        if (chunk != null) {
            int lx = x & 31;
            int lz = z & 31;
            var ref = chunk.getBlockComponentEntity(lx, y, lz);
            var type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
            if (ref != null && type != null) {
                pipe = ref.getStore().getComponent(ref, type);
                if (pipe != null) {
                    raw = pipe.getRawSideConfig();
                    if (pipe.getBlockType() != null && pipe.getBlockType().getState() != null) {
                        stateId = pipe.getBlockType().getId();
                    }
                }
            }
        }

        cmd.set("#Title.Text", "Pipe HUD");
        cmd.set("#Coords.Text", "Coords: (" + x + "," + y + "," + z + ")");
        cmd.set("#Raw.Text", "Raw sideConfig: " + raw);
        cmd.set("#StateId.Text", "State Id: " + stateId);

        setFace(cmd, "North", pipe);
        setFace(cmd, "South", pipe);
        setFace(cmd, "East", pipe);
        setFace(cmd, "West", pipe);
        setFace(cmd, "Up", pipe);
        setFace(cmd, "Down", pipe);
    }

    private void setFace(@Nonnull UICommandBuilder cmd, @Nonnull String dirName, @Nullable ItemPipeBlockState pipe) {
        ConnectionState state = ConnectionState.None;
        if (pipe != null) {
            Direction d = Direction.valueOf(dirName);
            state = pipe.getConnectionState(d);
        }
        String text = dirName + ": " + state;
        cmd.set("#" + dirName + ".Text", text);
    }
}
