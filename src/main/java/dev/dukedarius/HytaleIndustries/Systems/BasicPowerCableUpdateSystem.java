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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.UpdatePowerCableComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class BasicPowerCableUpdateSystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType;
    private final ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType;
    private final Query<ChunkStore> query;

    public BasicPowerCableUpdateSystem(
            ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType,
            ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType) {
        this.cableComponentType = cableComponentType;
        this.updateComponentType = updateComponentType;
        this.query = Query.and(
                updateComponentType,
                cableComponentType
        );
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {

        var cable = archetypeChunk.getComponent(index, cableComponentType);
        var ref = archetypeChunk.getReferenceTo(index);

        if (cable == null || ref == null) {
            return;
        }
        
        dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable Update] Starting update for cable");

        var blockStateInfo = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            return;
        }

        var chunkRef = blockStateInfo.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        var blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        var world = store.getExternalData().getWorld();
        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));

        // Recalculate connections
        Vector3i[] directions = {
            new Vector3i(0, 0, -1),  // North
            new Vector3i(0, 0, 1),   // South
            new Vector3i(-1, 0, 0),  // West
            new Vector3i(1, 0, 0),   // East
            new Vector3i(0, 1, 0),   // Up
            new Vector3i(0, -1, 0)   // Down
        };
        
        boolean[] hasEnergy = new boolean[6];
        boolean[] hasCableNeighbor = new boolean[6];
        
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

                boolean hasCable = false;
                if (holder != null && entity != null) {
                    BlockType neighborType = chunkForBlock.getBlockType(currentX & 31, currentY, currentZ & 31);
                    String neighborId = neighborType != null ? neighborType.getId() : null;
                    String baseId = normalizeBlockId(neighborId);

                    var neighborCable = store.getComponent(entity, cableComponentType);
                    if (neighborCable != null && "HytaleIndustries_BasicPowerCable".equals(baseId)) {
                        // Track raw neighbor existence for reconciliation
                        hasCableNeighbor[i] = true;
                        
                        // Bidirectional check for visual connection: neighbor's opposite side must not be None
                        Vector3i oppositeDir = new Vector3i(-dir.x, -dir.y, -dir.z);
                        boolean neighborAllows = neighborCable.isSideConnected(oppositeDir);
                        boolean thisAllows = cable.isSideConnected(dir);
                        dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log(
                            "    Checking connection: dir=(%d,%d,%d), this side allows: %b, neighbor opposite allows: %b, neighbor sideConfig: %d",
                            dir.x, dir.y, dir.z, thisAllows, neighborAllows, neighborCable.getSideConfig()
                        );
                        if (thisAllows && neighborAllows) {
                            hasCable = true;
                        }
                    }
                }
                
                // Check for energy blocks
                boolean hasEnergyBlock = hasEnergyAt(world, store, currentX, currentY, currentZ);
                hasEnergy[i] = hasEnergyBlock;
                
                // track presence; actual mask computed after reconciliation
            }
        }
        
        // Reconcile sideConfig based on connections
        dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable Update] Reconciling at (%d,%d,%d), sideConfig before: %d", x, y, z, cable.getSideConfig());
        for (int i = 0; i < directions.length; i++) {
            Vector3i dir = directions[i];
            boolean neighborCableExists = hasCableNeighbor[i];
            boolean hasEnergyBlock = hasEnergy[i];
            BasicPowerCableComponent.ConnectionState currentState = cable.getConnectionState(dir);
            
            String dirName = switch(i) {
                case 0 -> "North";
                case 1 -> "South";
                case 2 -> "West";
                case 3 -> "East";
                case 4 -> "Up";
                case 5 -> "Down";
                default -> "Unknown";
            };
            
            boolean isManual = cable.isManuallyConfigured(dir);
            
            // Rule 1: If Default and no neighbor (cable or energy), auto-set to None
            if (currentState == BasicPowerCableComponent.ConnectionState.Default && !(neighborCableExists || hasEnergyBlock)) {
                dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("  [%s] Rule 1: Default → None", dirName);
                cable.setConnectionState(dir, BasicPowerCableComponent.ConnectionState.None, false);
            }
            // Rule 2: If None and cable neighbor exists, auto-restore to Default (but NOT if manually configured)
            else if (currentState == BasicPowerCableComponent.ConnectionState.None && neighborCableExists && !isManual) {
                dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("  [%s] Rule 2: None → Default (neighborExists: %b, manual: %b)", dirName, neighborCableExists, isManual);
                cable.setConnectionState(dir, BasicPowerCableComponent.ConnectionState.Default, false);
            }
            // Rule 3: If None and energy block exists, auto-restore to Default (unless manually blocked)
            else if (currentState == BasicPowerCableComponent.ConnectionState.None && hasEnergyBlock && !isManual) {
                dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("  [%s] Rule 3: None → Default (energy block present)", dirName);
                cable.setConnectionState(dir, BasicPowerCableComponent.ConnectionState.Default, false);
            }
            // Extract and manual None states are NEVER touched by reconciliation
        }
        dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable Update] After reconciliation, sideConfig: %d, pipeState: %d (binary: %s)",
            cable.getSideConfig(), cable.getPipeState(), Integer.toBinaryString(cable.getPipeState()));

        // Recompute connection mask now that sideConfig may have changed
        int occupiedMask = 0;
        for (int i = 0; i < directions.length; i++) {
            Vector3i dir = directions[i];
            boolean neighborCableExists = hasCableNeighbor[i];
            boolean hasEnergyBlock = hasEnergy[i];
            if ((neighborCableExists || hasEnergyBlock) && cable.isSideConnected(dir)) {
                occupiedMask |= 1 << i;
            }
        }
        cable.setDirectionalState(occupiedMask);
        
        BlockType blockType = BlockType.getAssetMap().getAsset(
                blockChunk.getBlock(
                        ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()),
                        y,
                        ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex())
                )
        );

        if (blockType == null) {
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

            int lx = x & 31;
            int lz = z & 31;
            
            String stateName = String.format("State%03d", cable.getSideConfig());
            dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log(
                "[BasicPowerCable Update] Setting state at (%d,%d,%d) to: %s (sideConfig: %d, pipeState: %d)",
                x, y, z, stateName, cable.getSideConfig(), cable.getPipeState()
            );
            wc.setBlockInteractionState(x, y, z, blockType, stateName, true);

            // Update the component's pipe state in the entity store
            var entity = wc.getBlockComponentEntity(x, y, z);
            if (entity != null) {
                var entityStore = entity.getStore();
                var updatedCable = entityStore.getComponent(entity, cableComponentType);
                if (updatedCable != null) {
                    updatedCable.updateFrom(cable);
                    entityStore.replaceComponent(entity, cableComponentType, updatedCable);
                }
            }
        });
    }

    private static boolean hasEnergyAt(@Nonnull World world, Store<ChunkStore> store, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }

        // Resolve filler blocks to their origin
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        // Check if block has energy capability, but never treat our own conduits (pipes or cables)
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return false;
        }

        BlockType blockType = chunk.getBlockType(ox & 31, oy, oz & 31);
        String blockId = blockType != null ? blockType.getId() : null;
        String baseId = normalizeBlockId(blockId);
        if ("HytaleIndustries_BasicItemPipe".equals(baseId) || "HytaleIndustries_BasicPowerCable".equals(baseId)) {
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
        if (hasStore || hasConsume || hasProduce || hasCableEndpoint) {
            dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atFine().log("[BasicPowerCable Update] Found energy block at (%d,%d,%d)", ox, oy, oz);
            return true;
        }

        return false;
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

    /**
     * Normalize a runtime block id (which may include leading '*' and state suffix) into
     * the base asset id.
     */
    private static String normalizeBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }
        String base = blockId;
        if (base.startsWith("*")) {
            base = base.substring(1);
        }
        int stateIdx = base.indexOf("_State_");
        if (stateIdx > 0) {
            base = base.substring(0, stateIdx);
        }
        return base;
    }
}
