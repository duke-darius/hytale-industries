package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;

import javax.annotation.Nonnull;

public class SetPipeSideCommand extends AbstractPlayerCommand {

    public SetPipeSideCommand() {
        super("setPipeSide", "Set a pipe side state: /setPipeSide <x> <y> <z> <Side> <Default|Extract|None>");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String[] parts = context.getInputString().trim().split("\\s+");
        if (parts.length < 6) {
            context.sendMessage(Message.raw("Usage: /setPipeSide <x> <y> <z> <Side> <Default|Extract|None>"));
            return;
        }
        int x, y, z;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
            z = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return;
        }

        ItemPipeBlockState.Direction dir;
        try {
            dir = ItemPipeBlockState.Direction.valueOf(parts[4]);
        } catch (IllegalArgumentException ex) {
            context.sendMessage(Message.raw("Side must be one of: North,South,East,West,Up,Down"));
            return;
        }

        ItemPipeBlockState.ConnectionState state;
        try {
            state = ItemPipeBlockState.ConnectionState.valueOf(parts[5]);
        } catch (IllegalArgumentException ex) {
            context.sendMessage(Message.raw("State must be one of: Default,Extract,None"));
            return;
        }

        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded."));
            return;
        }
        int lx = x & 31;
        int lz = z & 31;

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef == null) {
            context.sendMessage(Message.raw("No BlockState entity at that position."));
            return;
        }

        var type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            context.sendMessage(Message.raw("Pipe BlockState type not registered."));
            return;
        }

        ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
        if (pipe == null) {
            context.sendMessage(Message.raw("Block is not an ItemPipe."));
            return;
        }

        pipe.setConnectionState(dir, state);
        stateRef.getStore().replaceComponent(stateRef, type, pipe);
        PipeSideConfigStore.set(x, y, z, pipe.getRawSideConfig());
        chunk.markNeedsSaving();

        // Force apply desired variant like the UI does.
        int blockId = chunk.getBlock(lx, y, lz);
        int rotIndex = chunk.getRotationIndex(lx, y, lz);
        int settings = 64 | 256 | 4 | 2;
        boolean changed = chunk.setBlock(lx, y, lz, blockId, chunk.getBlockType(lx, y, lz), rotIndex, 0, settings);
        HytaleIndustriesPlugin.LOGGER.atInfo().log("[setPipeSide] set " + dir + "=" + state + " changed=" + changed + " rot=" + rotIndex);

        context.sendMessage(Message.raw("Set " + dir + " to " + state + " at (" + x + "," + y + "," + z + ")"));
    }
}
