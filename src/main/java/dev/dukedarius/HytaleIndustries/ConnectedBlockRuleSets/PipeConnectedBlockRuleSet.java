package dev.dukedarius.HytaleIndustries.ConnectedBlockRuleSets;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.logging.Level;

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
        int mask = computeConnectionMask(world, testedCoordinate);

        // Visual state is derived from the pipe's per-side config (Default/Extract/None),
        // not from the current block type, so that changing a single side updates the model.
        int pipeCfg = PipeSideConfigStore.get(testedCoordinate.x, testedCoordinate.y, testedCoordinate.z);
        String stateSuffix = computeVisualStateSuffix(pipeCfg);

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
            key = (stateSuffix != null) ? ("*" + baseKey + "_" + stateSuffix) : baseKey;
        } else {
            String baseKey = VARIANT_PREFIX + String.format("%02X", mask);
            key = (stateSuffix != null) ? ("*" + baseKey + "_" + stateSuffix) : baseKey;
        }

        // If the target block type isn't loaded, do NOT return it (ConnectedBlocksUtil will NPE on null BlockType).
        if (BlockType.getAssetMap().getIndex(key) == Integer.MIN_VALUE) {
            return Optional.empty();
        }

        return Optional.of(new ConnectedBlocksUtil.ConnectedBlockResult(key, outRotationIndex));
    }

    private static String computeVisualStateSuffix(int pipeCfg) {
        // If exactly one side is configured to Extract, use a directional state (ExtractNorth, etc.)
        // so the model can highlight that single arm. If multiple sides are Extract, fall back
        // to the generic "Extract" state.
        int extractCount = 0;
        Direction only = null;

        for (Direction d : Direction.values()) {
            if (ItemPipeBlockState.getConnectionStateFromSideConfig(pipeCfg, d) == ItemPipeBlockState.ConnectionState.Extract) {
                extractCount++;
                if (extractCount == 1) {
                    only = d;
                }
                if (extractCount > 1) {
                    return ItemPipeBlockState.EXTRACT_STATE;
                }
            }
        }

        if (extractCount == 1 && only != null) {
            return ItemPipeBlockState.EXTRACT_STATE + only.name();
        }

        return null;
    }

    private static int computeConnectionMask(World world, Vector3i pos) {
        int mask = 0;
        WorldChunk chunkForSelf = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkForSelf == null) {
            PipeSideConfigStore.clear(pos.x, pos.y, pos.z);
            return 0;
        }
        BlockType selfType = chunkForSelf.getBlockType(pos.x & 31, pos.y, pos.z & 31);
        if (!isPipeBlockType(selfType)) {
            PipeSideConfigStore.clear(pos.x, pos.y, pos.z);
            return 0;
        }

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

        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        long chunkIndex = ChunkUtil.indexChunkFromBlock(ox, oz);

        // We need state lookups for ItemContainerBlockState, which requires an in-memory chunk.
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }

        BlockType neighbor = chunk.getBlockType(ox & 31, oy, oz & 31);
        if (isPipeBlockType(neighbor)) {
            int otherCfg = PipeSideConfigStore.get(ox, oy, oz);
            return ItemPipeBlockState.getConnectionStateFromSideConfig(otherCfg, dir.opposite()) != ItemPipeBlockState.ConnectionState.None;
        }

        BlockState state = chunk.getState(ox & 31, oy, oz & 31);
        if (state instanceof ItemContainerBlockState) {
            return true;
        }

        // Multiblock support: some inventories only have the ItemContainerBlockState on one block of the multiblock.
        // If the neighbor block type is the same across a small footprint, search nearby blocks of the same type
        // for an ItemContainerBlockState.
        if (neighbor != null && neighbor.getId() != null) {
            String neighborId = neighbor.getId();
            int[][] offsets = {
                    {0, 0, 0},
                    {1, 0, 0}, {-1, 0, 0},
                    {0, 0, 1}, {0, 0, -1},
                    {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
                    {0, 1, 0}, {0, -1, 0}
            };
            for (int[] off : offsets) {
                int sx = ox + off[0];
                int sy = oy + off[1];
                int sz = oz + off[2];
                if (sy < 0 || sy >= 320) continue;
                WorldChunk sc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(sx, sz));
                if (sc == null) continue;
                BlockType t = sc.getBlockType(sx & 31, sy, sz & 31);
                if (t == null || t.getId() == null || !t.getId().equals(neighborId)) continue;
                BlockState st = sc.getState(sx & 31, sy, sz & 31);
                if (st instanceof ItemContainerBlockState) {
                    return true;
                }
            }
        }

        return false;
    }


    private static boolean isPipeBlockType(@Nullable BlockType blockType) {
        return blockType != null
                && blockType.getState() != null
                && ItemPipeBlockState.STATE_ID.equals(blockType.getState().getId());
    }

    // Resolve filler blocks to their origin block coordinates; returns {x,y,z}.
    private static int[] resolveFillerOrigin(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return new int[]{x, y, z};
        }
        int filler = chunk.getFiller(x & 31, y, z & 31);
        if (filler == 0) {
            return new int[]{x, y, z};
        }
        int dx = FillerBlockUtil.unpackX(filler);
        int dy = FillerBlockUtil.unpackY(filler);
        int dz = FillerBlockUtil.unpackZ(filler);
        return new int[]{x - dx, y - dy, z - dz};
    }
}
