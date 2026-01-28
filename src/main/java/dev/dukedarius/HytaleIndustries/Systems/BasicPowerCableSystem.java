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
import dev.dukedarius.HytaleIndustries.Components.PowerCables.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.UpdatePowerCableComponent;
import dev.dukedarius.HytaleIndustries.Energy.HEComponents;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class BasicPowerCableSystem extends RefSystem<ChunkStore> {

    public static final String CABLE_STATE_ID = "basicPowerCable";
    
    private final ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType;
    private final ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType;
    private final Query<ChunkStore> query;
    
    public BasicPowerCableSystem(
            ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType,
            ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType) {
        this.cableComponentType = cableComponentType;
        this.updateComponentType = updateComponentType;
        this.query = Query.and(
                cableComponentType,
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
        if (addReason != AddReason.SPAWN) {
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

        var world = storeChunkStore.getExternalData().getWorld();
        var cableComponent = storeChunkStore.getComponent(refChunkStore, cableComponentType);

        if (cableComponent == null) {
            return;
        }

        HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable] Cable spawned at (%d,%d,%d), initial sideConfig: %d", x, y, z, cableComponent.getSideConfig());
        
        // Calculate connection mask
        Vector3i[] directions = {
            new Vector3i(0, 0, -1),  // North
            new Vector3i(0, 0, 1),   // South
            new Vector3i(-1, 0, 0),  // West
            new Vector3i(1, 0, 0),   // East
            new Vector3i(0, 1, 0),   // Up
            new Vector3i(0, -1, 0)   // Down
        };
        
        int occupiedMask = 0;
        boolean[] hasEnergy = new boolean[6];
        for (int i = 0; i < directions.length; i++) {
            Vector3i dir = directions[i];
            var currentX = x + dir.x;
            var currentY = y + dir.y;
            var currentZ = z + dir.z;
            
            String dirName = switch(i) {
                case 0 -> "North";
                case 1 -> "South";
                case 2 -> "West";
                case 3 -> "East";
                case 4 -> "Up";
                case 5 -> "Down";
                default -> "Unknown";
            };

            var chunkIndex = ChunkUtil.indexChunkFromBlock(currentX, currentZ);
            var chunkForBlock = world.getChunk(chunkIndex);

            if (chunkForBlock != null) {
                var holder = chunkForBlock.getBlockComponentHolder(currentX, currentY, currentZ);
                var entity = chunkForBlock.getBlockComponentEntity(currentX, currentY, currentZ);

                boolean hasCable = false;
                if (holder != null) {
                    var neighborCable = storeChunkStore.getComponent(entity, cableComponentType);
                    if (neighborCable != null) {
                        // A cable neighbor exists - this direction should connect
                        hasCable = true;
                        HytaleIndustriesPlugin.LOGGER.atFine().log("  [%s] Found cable at (%d,%d,%d)", dirName, currentX, currentY, currentZ);
                    }
                }
                
                // Check for energy blocks (TransfersHE or ReceivesHE)
                boolean hasEnergyBlock = hasEnergyAt(world, storeChunkStore, currentX, currentY, currentZ);
                hasEnergy[i] = hasEnergyBlock;
                if (hasEnergyBlock) {
                    HytaleIndustriesPlugin.LOGGER.atFine().log("  [%s] Found energy block at (%d,%d,%d)", dirName, currentX, currentY, currentZ);
                }
                
                // Set connection bit if there's a cable OR an energy block
                if (hasCable || hasEnergyBlock) {
                    occupiedMask |= 1 << i;
                }
            }
        }

        HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable] Final occupiedMask: %d (binary: %s)", occupiedMask, Integer.toBinaryString(occupiedMask));
        
        // Always update visual state on spawn
        if (cableComponent != null) {
            // Update pipeState (connection bitmask)
            cableComponent.setDirectionalState(occupiedMask);
            cableComponent.setPipeState(occupiedMask);
            
            // Reconcile sideConfig based on connections
            for (int i = 0; i < directions.length; i++) {
                Vector3i dir = directions[i];
                boolean hasCable = (occupiedMask & (1 << i)) != 0;
                boolean hasEnergyBlock = hasEnergy[i];
                BasicPowerCableComponent.ConnectionState currentState = cableComponent.getConnectionState(dir);
                
                // Rule 1: If Default and no neighbor (cable or energy), auto-set to None
                if (currentState == BasicPowerCableComponent.ConnectionState.Default && !(hasCable || hasEnergyBlock)) {
                    cableComponent.setConnectionState(dir, BasicPowerCableComponent.ConnectionState.None);
                }
                // Rule 2: If None and cable neighbor exists, auto-restore to Default
                else if (currentState == BasicPowerCableComponent.ConnectionState.None && hasCable) {
                    cableComponent.setConnectionState(dir, BasicPowerCableComponent.ConnectionState.Default);
                }
                // Extract state is NEVER touched by reconciliation
            }

            BlockType blockType = BlockType.getAssetMap().getAsset(
                    blockChunk.getBlock(
                            ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()),
                            y,
                            ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex())
                    )
            );

            if (blockType != null) {
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
                    
                    // Force rotation to 0
                    int currentRot = wc.getRotationIndex(lx, finalY, lz);
                    if (currentRot != 0) {
                        int settings = 64 | 256 | 4 | 2;
                        int filler = wc.getFiller(lx, finalY, lz);
                        int blockId = wc.getBlock(lx, finalY, lz);
                        wc.setBlock(lx, finalY, lz, blockId, blockType, 0, filler, settings);
                    }

                    String stateName = String.format("State%03d", cableComponent.getSideConfig());
                    wc.setBlockInteractionState(finalX, finalY, finalZ, blockType, stateName, true);

                    // Mark neighboring cables for update
                    HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable] Marking neighbors for update at (%d,%d,%d)", finalX, finalY, finalZ);
                    for (int i = 0; i < directions.length; i++) {
                        Vector3i dir = directions[i];
                        int nx = finalX + dir.x;
                        int ny = finalY + dir.y;
                        int nz = finalZ + dir.z;

                        long nChunkIndex = ChunkUtil.indexChunkFromBlock(nx, nz);
                        WorldChunk nChunk = wc.getWorld().getChunk(nChunkIndex);

                        if (nChunk != null) {
                            var nEntity = nChunk.getBlockComponentEntity(nx, ny, nz);
                            if (nEntity != null) {
                                var nCable = _store.getComponent(nEntity, cableComponentType);
                                if (nCable != null) {
                                    HytaleIndustriesPlugin.LOGGER.atFine().log("    Found neighbor cable at (%d,%d,%d), marking for update", nx, ny, nz);
                                    _store.ensureComponent(nEntity, updateComponentType);
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
        if (removeReason != RemoveReason.REMOVE) {
            return;
        }

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
                    var neighborCable = commandBufferChunkStore.getComponent(entity, cableComponentType);
                    if (neighborCable != null) {
                        commandBufferChunkStore.ensureComponent(entity, updateComponentType);
                    }
                }
            }
        }
    }

    private static boolean hasEnergyAt(@Nonnull World world, Store<ChunkStore> store, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }

        // Resolve filler blocks to their origin
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];
        
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return false;
        }

        var entity = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (entity == null) {
            return false;
        }

        var entityStore = entity.getStore();
        boolean hasStore = entityStore.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getStoresHeType()) != null;
        boolean hasConsume = entityStore.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getConsumesHeType()) != null;
        boolean hasProduce = entityStore.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getProducesHeType()) != null;
        boolean hasCableEndpoint = entityStore.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getCableEndpointType()) != null;
        return hasStore || hasConsume || hasProduce || hasCableEndpoint;
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
