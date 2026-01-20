package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSelectionStore;
import dev.dukedarius.HytaleIndustries.UI.PipeHUDPage;

import javax.annotation.Nonnull;

public class PipeHUDCommand extends AbstractPlayerCommand {

    public PipeHUDCommand() {
        super("pipehud", "Show a HUD with info about the looked-at/selected pipe");
        this.withOptionalArg("x", "X coordinate of the pipe", ArgTypes.INTEGER);
        this.withOptionalArg("y", "Y coordinate of the pipe", ArgTypes.INTEGER);
        this.withOptionalArg("z", "Z coordinate of the pipe", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String[] parts = context.getInputString().trim().split("\\s+");
        Integer x = null, y = null, z = null;
        if (parts.length >= 4) {
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (x == null || y == null || z == null) {
            var last = PipeSelectionStore.get(playerRef.getUuid());
            if (last != null) {
                x = last.x;
                y = last.y;
                z = last.z;
            }
        }
        if (x == null || y == null || z == null) {
            context.sendMessage(Message.raw("Usage: /pipehud <x> <y> <z> or select a pipe first."));
            return;
        }

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded for (" + x + "," + y + "," + z + ")."));
            return;
        }
        int lx = x & 31;
        int lz = z & 31;
        var stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef == null) {
            context.sendMessage(Message.raw("No block state at (" + x + "," + y + "," + z + ")."));
            return;
        }
        var type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            context.sendMessage(Message.raw("Pipe state type not registered."));
            return;
        }
        ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
        if (pipe == null) {
            context.sendMessage(Message.raw("Block at (" + x + "," + y + "," + z + ") is not an Item Pipe."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PipeHUDPage page = new PipeHUDPage(playerRef, new Vector3i(x, y, z));
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
