package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.BurningGeneratorBlockState;

import javax.annotation.Nonnull;

public class SetGeneratorStateCommand extends AbstractPlayerCommand {

    public SetGeneratorStateCommand() {
        super("setGeneratorState", "Set/reset a burning generator block state: /setGeneratorState <x> <y> <z> <stateName|reset>");
        this.withOptionalArg("x", "X coordinate of the generator", ArgTypes.INTEGER);
        this.withOptionalArg("y", "Y coordinate of the generator", ArgTypes.INTEGER);
        this.withOptionalArg("z", "Z coordinate of the generator", ArgTypes.INTEGER);
        this.withOptionalArg("state", "State name (e.g., testState) or 'reset'", ArgTypes.STRING);
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
        Integer x = null, y = null, z = null;
        String stateName = null;

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.startsWith("--x=")) x = parseIntSafe(p.substring(4));
            else if (p.startsWith("--y=")) y = parseIntSafe(p.substring(4));
            else if (p.startsWith("--z=")) z = parseIntSafe(p.substring(4));
            else if (p.startsWith("--state=")) stateName = p.substring(8);
        }
        if (x == null || y == null || z == null) {
            if (parts.length >= 4) {
                x = x != null ? x : parseIntSafe(parts[1]);
                y = y != null ? y : parseIntSafe(parts[2]);
                z = z != null ? z : parseIntSafe(parts[3]);
            }
        }
        if (stateName == null && parts.length >= 5) {
            stateName = parts[4];
        }

        if (x == null || y == null || z == null) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return;
        }
        if (stateName == null || stateName.isEmpty()) {
            context.sendMessage(Message.raw("Missing state. Usage: /setGeneratorState <x> <y> <z> <stateName|reset>"));
            return;
        }
        boolean reset = "reset".equalsIgnoreCase(stateName);

        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded."));
            return;
        }

        int lx = x & 31;
        int lz = z & 31;

        BlockType blockType = chunk.getBlockType(lx, y, lz);
        if (blockType == null || blockType.getState() == null || !BurningGeneratorBlockState.STATE_ID.equals(blockType.getState().getId())) {
            context.sendMessage(Message.raw("Block at coords is not a Burning Generator."));
            return;
        }


        // Derive base id (strip variant marker '*' and existing state suffix if any).
        String id = blockType.getId();
        if (id == null) {
            context.sendMessage(Message.raw("Block type has no id."));
            return;
        }
        if (id.startsWith("*")) {
            id = id.substring(1);
        }
        int idx = id.indexOf("_State_");
        if (idx > 0) {
            id = id.substring(0, idx);
        }

        String targetState = reset ? "default" : stateName;
        chunk.setBlockInteractionState(x, y, z, blockType, targetState, true);
        chunk.markNeedsSaving();

        context.sendMessage(Message.raw((reset ? "Reset" : "Set") + " generator state to '" + targetState + "' at (" + x + "," + y + "," + z + ")"));
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
