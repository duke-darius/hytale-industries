package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BasicItemPipeUpdateSystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType;
    private final ComponentType<ChunkStore, UpdatePipeComponent> updateComponentType;
    private final Query<ChunkStore> query;

    public BasicItemPipeUpdateSystem(
            ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType,
            ComponentType<ChunkStore, UpdatePipeComponent> updateComponentType) {
        this.pipeComponentType = pipeComponentType;
        this.updateComponentType = updateComponentType;
        this.query = Query.and(
                updateComponentType,
                pipeComponentType
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {

        HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] tick dt=" + dt + " pos=(" + index + ")");

        var pipe = archetypeChunk.getComponent(index, pipeComponentType);
        var ref = archetypeChunk.getReferenceTo(index);

        if (pipe == null || ref == null) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("Pipe or ref is null");
            return;
        }

        HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Pipe: %s", pipe);

        var blockStateInfo = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Block state info is null");
            return;
        }

        var chunkRef = blockStateInfo.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Chunk ref is null or invalid");
            return;
        }

        var blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Block chunk is null");
            return;
        }

        var world = store.getExternalData().getWorld();
        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));
        HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Updating pipe at: %s, %s, %s", x, y, z);

        // Recalculate connections using ItemPipeBlockState direction order:
        // 0=North(0,0,-1), 1=South(0,0,1), 2=West(-1,0,0), 3=East(1,0,0), 4=Up(0,1,0), 5=Down(0,-1,0)
        Vector3i[] directions = {
            new Vector3i(0, 0, -1),  // North
            new Vector3i(0, 0, 1),   // South
            new Vector3i(-1, 0, 0),  // West
            new Vector3i(1, 0, 0),   // East
            new Vector3i(0, 1, 0),   // Up
            new Vector3i(0, -1, 0)   // Down
        };
        
        int occupiedMask = 0;
        boolean[] hasInventory = new boolean[6];
        for (int i = 0; i < directions.length; i++) {
            Vector3i dir = directions[i];
            var currentX = x + dir.x;
            var currentY = y + dir.y;
            var currentZ = z + dir.z;

            var chunkIndex = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
            var chunkForBlock = world.getChunk(chunkIndex);

            if (chunkForBlock != null) {
                var holder = chunkForBlock.getBlockComponentHolder(currentX, currentY, currentZ);
                var entity = chunkForBlock.getBlockComponentEntity(currentX, currentY, currentZ);

                if (holder != null) {
                    var neighborPipe = store.getComponent(entity, pipeComponentType);
                    if (neighborPipe != null) {
                        // A pipe neighbor exists - this direction should connect
                        occupiedMask |= 1 << i;
                    }
                }
                
                // Check for inventory - blocks with Bench config or state ID "container"
                if (hasInventoryAt(world, currentX, currentY, currentZ)) {
                    hasInventory[i] = true;
                }
            }
        }

        HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Occupied mask: %s", occupiedMask);

        // Always update visual state (sideConfig may have changed via UI even if connections didn't)
        // Update pipeState (connection bitmask)
        pipe.setDirectionalState(occupiedMask);
        
        // Reconcile sideConfig based on connections (like ItemPipeBlockState.reconcileNeighborFaces lines 258-269)
        // ONLY auto-adjusts Default state - respects Extract and manual None configuration
        for (int i = 0; i < directions.length; i++) {
            Vector3i dir = directions[i];
            boolean hasPipe = (occupiedMask & (1 << i)) != 0;
            boolean hasInv = hasInventory[i];
            BasicItemPipeComponent.ConnectionState currentState = pipe.getConnectionState(dir);
            boolean isManual = pipe.isManuallyConfigured(dir);
            
            // Rule 1: If Default and no neighbor (pipe or inventory), auto-set to None
            if (currentState == BasicItemPipeComponent.ConnectionState.Default && !(hasPipe || hasInv)) {
                pipe.setConnectionState(dir, BasicItemPipeComponent.ConnectionState.None, false);
            } 
            // Rule 2: If None and pipe neighbor exists, auto-restore to Default (but NOT if manually configured)
            else if (currentState == BasicItemPipeComponent.ConnectionState.None && hasPipe && !isManual) {
                pipe.setConnectionState(dir, BasicItemPipeComponent.ConnectionState.Default, false);
            }
            // Extract and manual None states are NEVER touched by reconciliation
        }
        
        BlockType blockType = BlockType.getAssetMap().getAsset(
                blockChunk.getBlock(
                        ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()),
                        y,
                        ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex())
                )
        );
        HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Block type: %s", blockType.getId());

        if (blockType == null) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log("[BasicItemPipeUpdateSystem] Block type is null during update");
            return;
        }

        commandBuffer.run(_store -> {
            var updateComp = _store.getComponent(ref, updateComponentType);
            if (updateComp != null) {
                updateComp.setHasUpdated(true);
            }

            WorldChunk wc = _store.getComponent(chunkRef, WorldChunk.getComponentType());
            if (wc == null) {
                return;
            }

            // Generate state name from sideConfig (like ItemPipeBlockState line 292-293)
            String stateName = String.format("State%03d", pipe.getSideConfig());

            HytaleIndustriesPlugin.LOGGER.atInfo().log(
                    "[BasicItemPipeUpdateSystem] Updating block state at (%s, %s, %s) to: %s",
                    x, y, z, stateName
            );

            wc.setBlockInteractionState(x, y, z, blockType, stateName, true);

            // Update the component's pipe state in the entity store
            var entity = wc.getBlockComponentEntity(x, y, z);
            if (entity != null) {
                var entityStore = entity.getStore();
                var updatedPipe = entityStore.getComponent(entity, pipeComponentType);
                if (updatedPipe != null) {
                    updatedPipe.updateFrom(pipe);
                }
            }

            // Remove the update marker
            _store.removeComponent(ref, updateComponentType);
        });
    }

    private static boolean hasInventoryAt(@Nonnull World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }

        // Resolve filler blocks to their origin
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        // Use World.getState() to check for ItemContainerBlockState (the interface)
        var state = world.getState(ox, oy, oz, true);
        return state instanceof ItemContainerBlockState;
    }

    @Nonnull
    private static int[] resolveFillerOrigin(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
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
