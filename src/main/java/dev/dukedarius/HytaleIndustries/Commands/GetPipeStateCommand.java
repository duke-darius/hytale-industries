package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
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
import dev.dukedarius.HytaleIndustries.Pipes.PipeSelectionStore;

import javax.annotation.Nonnull;
import java.util.Locale;

public class GetPipeStateCommand extends AbstractPlayerCommand {

    public GetPipeStateCommand() {
        super("getPipeState", "Print ItemPipeBlockState at x y z: sideConfig and per-side states");
        this.withRequiredArg("x", "X coordinate of the block", ArgTypes.INTEGER);
        this.withRequiredArg("y", "Y coordinate of the block", ArgTypes.INTEGER);
        this.withRequiredArg("z", "Z coordinate of the block", ArgTypes.INTEGER);
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
        int x, y, z;
        context.sendMessage(Message.raw(context.getInputString()));
        if (parts.length >= 4) {
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ex) {
                context.sendMessage(Message.raw("Coordinates must be integers."));
                return;
            }
        } else {
            // Use last pipe the player selected (e.g., via Configure interaction)
            var last = PipeSelectionStore.get(playerRef.getUuid());
            if (last == null) {
                context.sendMessage(Message.raw("No pipe coords provided and no recent pipe selection found. Use /getPipeState <x> <y> <z> once, or open the pipe UI first."));
                return;
            }
            x = last.x;
            y = last.y;
            z = last.z;
        }

        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded for (" + x + "," + y + "," + z + ")"));
            return;
        }

        int lx = x & com.hypixel.hytale.math.util.ChunkUtil.SIZE_MASK;
        int lz = z & com.hypixel.hytale.math.util.ChunkUtil.SIZE_MASK;
        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef == null) {
            context.sendMessage(Message.raw("No BlockState entity at (" + x + "," + y + "," + z + ")"));
            return;
        }

        var type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            context.sendMessage(Message.raw("Pipe BlockState type not registered."));
            return;
        }

        ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
        if (pipe == null) {
            context.sendMessage(Message.raw("BlockState at (" + x + "," + y + "," + z + ") is not ItemPipeBlockState."));
            return;
        }

        int raw = pipe.getRawSideConfig();
        String summary = String.format(Locale.US,
                "Pipe @(%d,%d,%d) rawSideConfig=%d [N=%s S=%s W=%s E=%s U=%s D=%s]",
                x, y, z, raw,
                pipe.getConnectionState(ItemPipeBlockState.Direction.North),
                pipe.getConnectionState(ItemPipeBlockState.Direction.South),
                pipe.getConnectionState(ItemPipeBlockState.Direction.West),
                pipe.getConnectionState(ItemPipeBlockState.Direction.East),
                pipe.getConnectionState(ItemPipeBlockState.Direction.Up),
                pipe.getConnectionState(ItemPipeBlockState.Direction.Down));

        // Log and send to caller so you can paste it back easily.
        HytaleIndustriesPlugin.LOGGER.atInfo().log("[GetPipeState] " + summary);
        context.sendMessage(Message.raw(summary));
    }
}
