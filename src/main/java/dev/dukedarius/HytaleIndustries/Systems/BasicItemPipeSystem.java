package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.UpdatePipeComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class BasicItemPipeSystem extends RefSystem<ChunkStore> {

    public static final String PIPE_STATE_ID = "basicItemPipe";
    
    private final ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType;
    private final ComponentType<ChunkStore, UpdatePipeComponent> updateComponentType;
    private final Query<ChunkStore> query;
    
    public BasicItemPipeSystem(
            ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType,
            ComponentType<ChunkStore, UpdatePipeComponent> updateComponentType) {
        this.pipeComponentType = pipeComponentType;
        this.updateComponentType = updateComponentType;
        this.query = Query.and(
                pipeComponentType,
                Query.not(updateComponentType)
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(Ref<ChunkStore> refChunkStore, AddReason addReason,
                              Store<ChunkStore> storeChunkStore,
                              CommandBuffer<ChunkStore> commandBufferChunkStore) {
        HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe added");

        if (addReason != AddReason.SPAWN) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe added reason is not SPAWN: %s", addReason);
            return;
        }

        var blockStateInfo = storeChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            return;
        }

        var chunkRef = blockStateInfo.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        var blockChunk = storeChunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));

        HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe spawned at: %s, %s, %s", x, y, z);

        var world = storeChunkStore.getExternalData().getWorld();
        var pipeComponent = storeChunkStore.getComponent(refChunkStore, pipeComponentType);

        if (pipeComponent == null) {
            return;
        }

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
                    var neighborPipe = storeChunkStore.getComponent(entity, pipeComponentType);
                    if (neighborPipe != null) {
                        // A pipe neighbor exists - this direction should connect
                        occupiedMask |= 1 << i;
                        HytaleIndustriesPlugin.LOGGER.atFine().log(
                            "Found neighbor pipe at (%s,%s,%s) in direction %s",
                            currentX, currentY, currentZ, dir
                        );
                    }
                }

                if (hasInventoryAt(world, storeChunkStore, currentX, currentY, currentZ)) {
                    hasInventory[i] = true;
                }
            }
        }

        // Always update visual state on spawn (even if mask is 0)
        if (pipeComponent != null) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("Setting pipe state to mask: %s", Integer.toBinaryString(occupiedMask));

            // Update pipeState (connection bitmask)
            pipeComponent.setDirectionalState(occupiedMask);
            pipeComponent.setPipeState(occupiedMask);
            
            // Reconcile sideConfig based on connections (like ItemPipeBlockState.reconcileNeighborFaces lines 258-269)
            // ONLY auto-adjusts Default state - respects Extract and manual None configuration
            for (int i = 0; i < directions.length; i++) {
                Vector3i dir = directions[i];
                boolean hasPipe = (occupiedMask & (1 << i)) != 0;
                boolean hasInv = hasInventory[i];
                BasicItemPipeComponent.ConnectionState currentState = pipeComponent.getConnectionState(dir);
                
                // Rule 1: If Default and no neighbor (pipe or inventory), auto-set to None
                if (currentState == BasicItemPipeComponent.ConnectionState.Default && !(hasPipe || hasInv)) {
                    pipeComponent.setConnectionState(dir, BasicItemPipeComponent.ConnectionState.None);
                } 
                // Rule 2: If None and pipe neighbor exists, auto-restore to Default
                // (but respect manual None configuration when only inventory exists)
                else if (currentState == BasicItemPipeComponent.ConnectionState.None && hasPipe) {
                    pipeComponent.setConnectionState(dir, BasicItemPipeComponent.ConnectionState.Default);
                }
                // Extract state is NEVER touched by reconciliation - it's manual-only
            }

            BlockType blockType = BlockType.getAssetMap().getAsset(
                    blockChunk.getBlock(
                            ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()),
                            y,
                            ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex())
                    )
            );

            if (blockType != null) {
                final int finalOccupiedMask = occupiedMask;
                final int finalX = x;
                final int finalY = y;
                final int finalZ = z;
                
                commandBufferChunkStore.run(_store -> {
                    WorldChunk wc = _store.getComponent(chunkRef, WorldChunk.getComponentType());
                    if (wc == null) {
                        return;
                    }

                    int lx = finalX & 31;
                    int lz = finalZ & 31;
                    
                    // Force rotation to 0 (no rotation) - matches ItemPipeBlockState behavior
                    int currentRot = wc.getRotationIndex(lx, finalY, lz);
                    if (currentRot != 0) {
                        int settings = 64 | 256 | 4 | 2;  // Settings flags
                        int filler = wc.getFiller(lx, finalY, lz);
                        int blockId = wc.getBlock(lx, finalY, lz);
                        wc.setBlock(lx, finalY, lz, blockId, blockType, 0, filler, settings);
                    }

                    // Generate state name from sideConfig (like ItemPipeBlockState line 292-293)
                    String stateName = String.format("State%03d", pipeComponent.getSideConfig());

                    HytaleIndustriesPlugin.LOGGER.atFine().log(
                            "Setting block state at (%s, %s, %s) to: %s (rot was: %s)",
                            finalX, finalY, finalZ, stateName, currentRot
                    );

                    wc.setBlockInteractionState(finalX, finalY, finalZ, blockType, stateName, true);

                    // Mark ALL neighboring pipes for update (not just connected ones)
                    // because the neighbor's sideConfig needs to reconcile even if currently blocked
                    for (int i = 0; i < directions.length; i++) {
                        Vector3i dir = directions[i];

                        var currentX = finalX + dir.x;
                        var currentY = finalY + dir.y;
                        var currentZ = finalZ + dir.z;

                        var chunkIndex = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
                        var neighborChunk = world.getChunk(chunkIndex);

                        if (neighborChunk != null) {
                            var holder = neighborChunk.getBlockComponentHolder(currentX, currentY, currentZ);
                            if (holder != null) {
                                var entity = neighborChunk.getBlockComponentEntity(currentX, currentY, currentZ);
                                var neighborPipe = holder.getComponent(pipeComponentType);

                                if (neighborPipe != null) {
                                    HytaleIndustriesPlugin.LOGGER.atFine().log("Marking neighbor pipe for update at: %s, %s, %s", currentX, currentY, currentZ);
                                    _store.ensureComponent(entity, updateComponentType);
                                }
                            }
                        }
                    }
                });
            }
        }
    }
    @Override
    public void onEntityRemove(Ref<ChunkStore> refChunkStore, RemoveReason removeReason,
                               Store<ChunkStore> storeChunkStore,
                               CommandBuffer<ChunkStore> commandBufferChunkStore) {
        HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe removed: %s", removeReason);

        if (removeReason != RemoveReason.REMOVE) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe removal reason is not REMOVE: %s", removeReason);
            return;
        }

        // Check if it's already being updated
        if (storeChunkStore.getComponent(refChunkStore, updateComponentType) != null ||
                commandBufferChunkStore.getComponent(refChunkStore, updateComponentType) != null) {
            return;
        }

        var blockStateInfo = storeChunkStore.getComponent(refChunkStore, BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            return;
        }

        var chunkRef = blockStateInfo.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        var blockChunk = storeChunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));

        HytaleIndustriesPlugin.LOGGER.atFine().log("Basic pipe removed at: %s, %s, %s", x, y, z);

        var world = commandBufferChunkStore.getExternalData().getWorld();

        // Notify neighbors to update
        for (var dir : Vector3i.BLOCK_SIDES) {
            var currentX = x + dir.x;
            var currentY = y + dir.y;
            var currentZ = z + dir.z;

            var chunkIndex = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
            var neighborChunk = world.getChunk(chunkIndex);

            if (neighborChunk != null) {
                var holder = neighborChunk.getBlockComponentHolder(currentX, currentY, currentZ);
                var entity = neighborChunk.getBlockComponentEntity(currentX, currentY, currentZ);

                if (holder != null) {
                    var neighborPipe = commandBufferChunkStore.getComponent(entity, pipeComponentType);
                    if (neighborPipe != null && neighborPipe.canConnectTo(dir)) {
                        HytaleIndustriesPlugin.LOGGER.atFine().log("Notifying neighbor pipe to update at: %s, %s, %s", currentX, currentY, currentZ);
                        commandBufferChunkStore.ensureComponent(entity, updateComponentType);
                    }
                }
            }
        }
    }

    private static boolean hasInventoryAt(@Nonnull World world, Store<ChunkStore> store, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }

        // Resolve filler blocks to their origin
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];
        
        if (ox != x || oy != y || oz != z) {
            HytaleIndustriesPlugin.LOGGER.atFine().log(
                "Resolved filler block at (%s,%s,%s) to origin (%s,%s,%s)",
                x, y, z, ox, oy, oz
            );
        }

        boolean hasInventory = !dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters.find(world, store, ox, oy, oz).isEmpty();

        HytaleIndustriesPlugin.LOGGER.atFine().log(
            "Checking inventory at (%s,%s,%s): hasInventory=%s",
            ox, oy, oz, hasInventory
        );

        return hasInventory;
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
