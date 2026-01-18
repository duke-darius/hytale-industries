package dev.dukedarius.HytaleIndustries.ConnectedBlockRuleSets;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;

import javax.annotation.Nullable;
import java.util.Optional;

public class PipeConnectedBlockRuleSet extends ConnectedBlockRuleSet {

    public static final BuilderCodec<PipeConnectedBlockRuleSet> CODEC = BuilderCodec.builder(
            PipeConnectedBlockRuleSet.class,
            PipeConnectedBlockRuleSet::new
    ).build();

    // Variant ids we generate in the asset pack.
    // 6-bit mask: N,S,E,W,U,D.
    private static final String VARIANT_PREFIX = "HytaleIndustries_ItemPipe_C";

    @Override
    public boolean onlyUpdateOnPlacement() {
        return false;
    }

    @Override
    public Optional<ConnectedBlocksUtil.ConnectedBlockResult> getConnectedBlockType(
            World world,
            Vector3i testedCoordinate,
            BlockType currentBlockType,
            int rotationIndex,
            Vector3i placementNormal,
            boolean isPlacement
    ) {
        // Preserve the current state (default vs Extract).
        boolean extract = ItemPipeBlockState.EXTRACT_STATE.equals(currentBlockType.getStateForBlock(currentBlockType));

        int mask = computeConnectionMask(world, testedCoordinate);

        // Players can place blocks with different rotation indices (yaw), but our generated
        // connection variants are authored in world-space (N/S/E/W/U/D). If we propagate the
        // placement rotation, the visible arms will appear "rotated" relative to neighbors.
        //
        // Force a stable rotation index so that mask bits always correspond to world directions.
        int outRotationIndex = 0;

        // If we're not connected to anything, revert to the base pipe block type (not a C00 variant).
        String key;
        if (mask == 0) {
            String baseKey = "HytaleIndustries_ItemPipe";
            key = extract ? ("*" + baseKey + "_" + ItemPipeBlockState.EXTRACT_STATE) : baseKey;
        } else {
            String baseKey = VARIANT_PREFIX + String.format("%02X", mask);
            key = extract ? ("*" + baseKey + "_" + ItemPipeBlockState.EXTRACT_STATE) : baseKey;
        }

        // If the target block type isn't loaded, do NOT return it (ConnectedBlocksUtil will NPE on null BlockType).
        if (BlockType.getAssetMap().getIndex(key) == Integer.MIN_VALUE) {
            return Optional.empty();
        }

        return Optional.of(new ConnectedBlocksUtil.ConnectedBlockResult(key, outRotationIndex));
    }

    private static int computeConnectionMask(World world, Vector3i pos) {
        int mask = 0;

        int pipeCfg = PipeSideConfigStore.get(pos.x, pos.y, pos.z);

        // N,S,E,W,U,D -> bits 0..5
        // Note: we consider pipes connectable to:
        // - other pipes
        // - adjacent inventories (blocks with an ItemContainerBlockState)
        // but "None" disables a face entirely.

        // North/South in the model variants follow the conventional "north = -Z" mapping.
        if (isConnectable(world, pos, pipeCfg, Direction.North)) mask |= 1 << 0; // north
        if (isConnectable(world, pos, pipeCfg, Direction.South)) mask |= 1 << 1; // south

        // East/West are swapped in the authored model variants (C04 vs C08), so swap X here
        // to make end-caps and corners connect correctly.
        if (isConnectable(world, pos, pipeCfg, Direction.East)) mask |= 1 << 3; // east
        if (isConnectable(world, pos, pipeCfg, Direction.West)) mask |= 1 << 2; // west

        if (isConnectable(world, pos, pipeCfg, Direction.Up)) mask |= 1 << 4; // up
        if (isConnectable(world, pos, pipeCfg, Direction.Down)) mask |= 1 << 5; // down

        return mask;
    }

    private static boolean isConnectable(World world, Vector3i pos, int pipeCfg, Direction dir) {
        if (ItemPipeBlockState.getConnectionStateFromSideConfig(pipeCfg, dir) == ItemPipeBlockState.ConnectionState.None) {
            return false;
        }

        int x = pos.x + dir.dx;
        int y = pos.y + dir.dy;
        int z = pos.z + dir.dz;

        // Match world vertical bounds used elsewhere (0..319 inclusive).
        if (y < 0 || y >= 320) {
            return false;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);

        // We need state lookups for ItemContainerBlockState, which requires an in-memory chunk.
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }

        BlockType neighbor = chunk.getBlockType(x & 31, y, z & 31);
        if (isPipeBlockType(neighbor)) {
            int otherCfg = PipeSideConfigStore.get(x, y, z);
            return ItemPipeBlockState.getConnectionStateFromSideConfig(otherCfg, dir.opposite()) != ItemPipeBlockState.ConnectionState.None;
        }

        BlockState state = chunk.getState(x & 31, y, z & 31);
        return state instanceof ItemContainerBlockState;
    }


    private static boolean isPipeBlockType(@Nullable BlockType blockType) {
        return blockType != null
                && blockType.getState() != null
                && ItemPipeBlockState.STATE_ID.equals(blockType.getState().getId());
    }
}
