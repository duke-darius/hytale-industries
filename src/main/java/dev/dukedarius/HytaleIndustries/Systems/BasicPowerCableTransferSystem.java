package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.TransfersHE;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ticking system that handles energy transfer for basic power cables.
 * Based on PowerCableBlockState extraction logic and BasicItemPipeExtractionSystem pattern.
 * Transfers 250 HE/second.
 */
public class BasicPowerCableTransferSystem extends EntityTickingSystem<ChunkStore> {

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;
    private static final double TRANSFER_HE_PER_SECOND = 250.0;
    private static final Vector3i[] DIRECTIONS = {
            new Vector3i(0, 0, -1),  // North
            new Vector3i(0, 0, 1),   // South
            new Vector3i(-1, 0, 0),  // West
            new Vector3i(1, 0, 0),   // East
            new Vector3i(0, 1, 0),   // Up
            new Vector3i(0, -1, 0)   // Down
    };

    private final ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType;
    private final Query<ChunkStore> query;

    public BasicPowerCableTransferSystem(ComponentType<ChunkStore, BasicPowerCableComponent> cableComponentType) {
        this.cableComponentType = cableComponentType;
        this.query = Query.and(cableComponentType);
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

        // Accumulate time - only run extraction 4 times per second (0.25s interval)
        float newTime = cable.getSecondsAccumulator() + dt;
        if (newTime < 0.25f) {
            cable.setSecondsAccumulator(newTime);
            return;
        }
        double budget = TRANSFER_HE_PER_SECOND * newTime;
        cable.setSecondsAccumulator(0.0f);

        // Only do work when at least one side is configured to Extract
        if (!hasAnyExtractSide(cable)) {
            return;
        }

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
        int cableX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int cableY = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int cableZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));

        // Find all Extract-configured source energy blocks
        List<HETransferEndpoint> sources = new ArrayList<>();
        LongOpenHashSet excludedKeys = new LongOpenHashSet();
        
        for (int i = 0; i < DIRECTIONS.length; i++) {
            Vector3i dir = DIRECTIONS[i];
            if (cable.getConnectionState(dir) == BasicPowerCableComponent.ConnectionState.Extract) {
                int sx = cableX + dir.x;
                int sy = cableY + dir.y;
                int sz = cableZ + dir.z;
                if (sy < WORLD_MIN_Y || sy >= WORLD_MAX_Y_EXCLUSIVE) continue;

                HETransferEndpoint source = getTransfersEndpointIfLoaded(world, sx, sy, sz);
                if (source != null) {
                    sources.add(source);
                    excludedKeys.add(packBlockPos(source.x, source.y, source.z));
                }
            }
        }

        if (sources.isEmpty()) {
            return;
        }

        // Find all reachable receivers through the cable network
        List<HEReceiveEndpoint> receivers = findAllReachableReceivers(world, cableX, cableY, cableZ, excludedKeys, store);
        if (receivers.isEmpty()) {
            return;
        }

        // Calculate total available energy
        double totalAvailable = 0;
        for (HETransferEndpoint source : sources) {
            totalAvailable += source.transfers.getHeStored();
        }
        double amountToMove = Math.min(totalAvailable, budget);
        if (amountToMove <= 0.0) {
            return;
        }

        // Sort receivers by free capacity ascending to ensure optimal fair distribution
        receivers.sort(Comparator.comparingDouble(r -> r.receives.getHeFreeCapacity()));

        double remainingToDistribute = amountToMove;
        int remainingReceiversCount = receivers.size();

        for (HEReceiveEndpoint receiver : receivers) {
            double share = remainingToDistribute / remainingReceiversCount;
            double canTake = receiver.receives.getHeFreeCapacity();
            double toGive = Math.min(share, canTake);

            if (toGive > 0.0) {
                double stillToExtract = toGive;
                for (HETransferEndpoint source : sources) {
                    double sourceAvailable = source.transfers.getHeStored();
                    if (sourceAvailable <= 0.0) continue;

                    double extract = Math.min(stillToExtract, sourceAvailable);
                    source.transfers.extractHe(extract);
                    persistEndpoint(source, commandBuffer);
                    stillToExtract -= extract;
                    if (stillToExtract <= 0.0) break;
                }

                receiver.receives.receiveHe(toGive);
                persistEndpoint(receiver, commandBuffer);
                remainingToDistribute -= toGive;
            }
            remainingReceiversCount--;
        }
    }

    private boolean hasAnyExtractSide(BasicPowerCableComponent cable) {
        for (Vector3i dir : DIRECTIONS) {
            if (cable.getConnectionState(dir) == BasicPowerCableComponent.ConnectionState.Extract) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static HETransferEndpoint getTransfersEndpointIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return null;
        }

        BlockState st = chunk.getState(ox & 31, oy, oz & 31);
        if (!(st instanceof TransfersHE)) {
            return null;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (stateRef == null) {
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) st.getClass());
        if (type == null) {
            return null;
        }

        Component<ChunkStore> component = stateRef.getStore().getComponent(stateRef, type);
        if (!(component instanceof TransfersHE transfers)) {
            return null;
        }

        return new HETransferEndpoint(stateRef, type, component, transfers, chunk, ox, oy, oz);
    }

    @Nullable
    private static HEReceiveEndpoint getReceivesEndpointIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return null;
        }

        BlockState st = chunk.getState(ox & 31, oy, oz & 31);
        if (!(st instanceof ReceivesHE)) {
            return null;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (stateRef == null) {
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) st.getClass());
        if (type == null) {
            return null;
        }

        Component<ChunkStore> component = stateRef.getStore().getComponent(stateRef, type);
        if (!(component instanceof ReceivesHE receives)) {
            return null;
        }

        return new HEReceiveEndpoint(stateRef, type, component, receives, chunk, ox, oy, oz);
    }

    private List<HEReceiveEndpoint> findAllReachableReceivers(@Nonnull World world, int startCableX, int startCableY, 
                                                               int startCableZ, LongOpenHashSet excludedKeys,
                                                               Store<ChunkStore> store) {
        List<HEReceiveEndpoint> found = new ArrayList<>();
        LongOpenHashSet foundKeys = new LongOpenHashSet();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        long start = packBlockPos(startCableX, startCableY, startCableZ);
        visited.add(start);
        queue.enqueue(start);

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int x = unpackX(current);
            int y = unpackY(current);
            int z = unpackZ(current);

            // Get cable component at current position to check traversal rules
            WorldChunk currentChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (currentChunk == null) {
                currentChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            }
            if (currentChunk == null) {
                continue;
            }

            var currentEntity = currentChunk.getBlockComponentEntity(x & 31, y, z & 31);
            BasicPowerCableComponent currentCable = null;
            if (currentEntity != null) {
                currentCable = store.getComponent(currentEntity, cableComponentType);
            }

            for (int i = 0; i < DIRECTIONS.length; i++) {
                Vector3i dir = DIRECTIONS[i];
                
                // Check if we can traverse from this cable in this direction
                if (currentCable != null && !currentCable.isSideConnected(dir)) {
                    continue;
                }

                int nx = x + dir.x;
                int ny = y + dir.y;
                int nz = z + dir.z;
                if (ny < WORLD_MIN_Y || ny >= WORLD_MAX_Y_EXCLUSIVE) {
                    continue;
                }

                int[] origin = resolveFillerOrigin(world, nx, ny, nz);
                int ox = origin[0], oy = origin[1], oz = origin[2];

                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
                if (chunk == null) {
                    chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
                }
                if (chunk == null) {
                    continue;
                }

                var entity = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
                BasicPowerCableComponent neighborCable = null;
                if (entity != null) {
                    neighborCable = store.getComponent(entity, cableComponentType);
                }

                if (neighborCable != null) {
                    // It's a cable - check if we can traverse through it
                    Vector3i oppositeDir = new Vector3i(-dir.x, -dir.y, -dir.z);
                    if (!neighborCable.isSideConnected(oppositeDir)) {
                        continue;
                    }

                    long nkey = packBlockPos(ox, oy, oz);
                    if (visited.add(nkey)) {
                        queue.enqueue(nkey);
                    }
                    continue;
                }

                // Non-cable: check if it's a receiver (only allow delivery on Default faces)
                if (currentCable != null && currentCable.getConnectionState(dir) != BasicPowerCableComponent.ConnectionState.Default) {
                    continue;
                }

                long receiverKey = packBlockPos(ox, oy, oz);
                if (excludedKeys.contains(receiverKey) || !foundKeys.add(receiverKey)) {
                    continue;
                }

                HEReceiveEndpoint ep = getReceivesEndpointIfLoaded(world, ox, oy, oz);
                if (ep != null) {
                    found.add(ep);
                }
            }
        }

        return found;
    }

    // Helper classes

    private static final class HETransferEndpoint {
        final Ref<ChunkStore> ref;
        final ComponentType<ChunkStore, Component<ChunkStore>> type;
        final Component<ChunkStore> component;
        final TransfersHE transfers;
        final WorldChunk chunk;
        final int x;
        final int y;
        final int z;

        HETransferEndpoint(Ref<ChunkStore> ref, ComponentType<ChunkStore, Component<ChunkStore>> type, 
                          Component<ChunkStore> component, TransfersHE transfers, WorldChunk chunk, 
                          int x, int y, int z) {
            this.ref = ref;
            this.type = type;
            this.component = component;
            this.transfers = transfers;
            this.chunk = chunk;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class HEReceiveEndpoint {
        final Ref<ChunkStore> ref;
        final ComponentType<ChunkStore, Component<ChunkStore>> type;
        final Component<ChunkStore> component;
        final ReceivesHE receives;
        final WorldChunk chunk;
        final int x;
        final int y;
        final int z;

        HEReceiveEndpoint(Ref<ChunkStore> ref, ComponentType<ChunkStore, Component<ChunkStore>> type, 
                         Component<ChunkStore> component, ReceivesHE receives, WorldChunk chunk, 
                         int x, int y, int z) {
            this.ref = ref;
            this.type = type;
            this.component = component;
            this.receives = receives;
            this.chunk = chunk;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static void persistEndpoint(HETransferEndpoint ep, CommandBuffer<ChunkStore> commandBuffer) {
        if (commandBuffer != null) {
            commandBuffer.replaceComponent(ep.ref, ep.type, ep.component);
        } else {
            ep.ref.getStore().replaceComponent(ep.ref, ep.type, ep.component);
        }
        ep.chunk.markNeedsSaving();
    }

    private static void persistEndpoint(HEReceiveEndpoint ep, CommandBuffer<ChunkStore> commandBuffer) {
        if (commandBuffer != null) {
            commandBuffer.replaceComponent(ep.ref, ep.type, ep.component);
        } else {
            ep.ref.getStore().replaceComponent(ep.ref, ep.type, ep.component);
        }
        ep.chunk.markNeedsSaving();
    }

    // Position packing/unpacking (26-bit X/Z, 12-bit Y)
    private static long packBlockPos(int x, int y, int z) {
        long lx = (long) x & 0x3FFFFFFL;
        long lz = (long) z & 0x3FFFFFFL;
        long ly = (long) y & 0xFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }

    private static int unpackX(long packed) {
        int x = (int) (packed >> 38);
        // sign extend 26-bit
        if (x >= 0x2000000) {
            x -= 0x4000000;
        }
        return x;
    }

    private static int unpackZ(long packed) {
        int z = (int) ((packed >> 12) & 0x3FFFFFFL);
        if (z >= 0x2000000) {
            z -= 0x4000000;
        }
        return z;
    }

    private static int unpackY(long packed) {
        return (int) (packed & 0xFFFL);
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
