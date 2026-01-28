package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BasicItemPipeExtractionSystem extends EntityTickingSystem<ChunkStore> {

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;
    private static final Vector3i[] DIRECTIONS = {
            new Vector3i(0, 0, -1),  // North
            new Vector3i(0, 0, 1),   // South
            new Vector3i(-1, 0, 0),  // West
            new Vector3i(1, 0, 0),   // East
            new Vector3i(0, 1, 0),   // Up
            new Vector3i(0, -1, 0)   // Down
    };

    private final ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType;
    private final Query<ChunkStore> query;

    public BasicItemPipeExtractionSystem(ComponentType<ChunkStore, BasicItemPipeComponent> pipeComponentType) {
        this.pipeComponentType = pipeComponentType;
        this.query = Query.and(pipeComponentType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        
        var pipe = archetypeChunk.getComponent(index, pipeComponentType);
        var ref = archetypeChunk.getReferenceTo(index);

        if (pipe == null || ref == null) {
            return;
        }

        // Accumulate time - only run extraction once per second
        float newTime = pipe.getSecondsAccumulator() + dt;
        if (newTime < 1.0f) {
            pipe.setSecondsAccumulator(newTime);
            return;
        }
        pipe.setSecondsAccumulator(0.0f);

        // Only do work when at least one side is configured to Extract
        if (!hasAnyExtractSide(pipe)) {
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
        int pipeX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
        int pipeY = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
        int pipeZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));

        // Find all Extract-configured source inventories
        List<SourceInventory> sources = new ArrayList<>();
        LongOpenHashSet excludedKeys = new LongOpenHashSet();
        
        for (int i = 0; i < DIRECTIONS.length; i++) {
            Vector3i dir = DIRECTIONS[i];
            if (pipe.getConnectionState(dir) == BasicItemPipeComponent.ConnectionState.Extract) {
                int sx = pipeX + dir.x;
                int sy = pipeY + dir.y;
                int sz = pipeZ + dir.z;
                if (sy < WORLD_MIN_Y || sy >= WORLD_MAX_Y_EXCLUSIVE) continue;

                SourceInventory sourceInv = getInventoryIfLoaded(world, sx, sy, sz);
                if (sourceInv != null) {
                    sources.add(sourceInv);
                    excludedKeys.add(packBlockPos(sourceInv.x, sourceInv.y, sourceInv.z));
                }
            }
        }

        if (sources.isEmpty()) {
            return;
        }

        // Find all reachable destination inventories through the pipe network
        List<InventoryEndpoint> endpoints = findAllReachableInventories(world, pipeX, pipeY, pipeZ, excludedKeys, store);
        if (endpoints.isEmpty()) {
            return;
        }

        // Move up to 4 items per second from sources to destinations
        int totalMoved = 0;
        for (SourceInventory sourceInv : sources) {
            if (totalMoved >= 4) break;

            ItemContainer source = sourceInv.container;
            if (source == null || source.isEmpty()) continue;

            SlotRange extractRange = getExtractableSlotRange(sourceInv.blockType, source);
            if (extractRange.isEmpty()) continue;

            for (InventoryEndpoint ep : endpoints) {
                int count = moveUpToNItems(source, ep.container, extractRange, 4 - totalMoved);
                if (count > 0) {
                    totalMoved += count;
                }
                if (totalMoved >= 4) break;
            }
        }
    }

    private boolean hasAnyExtractSide(BasicItemPipeComponent pipe) {
        for (Vector3i dir : DIRECTIONS) {
            if (pipe.getConnectionState(dir) == BasicItemPipeComponent.ConnectionState.Extract) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static SourceInventory getInventoryIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        var state = world.getState(ox, oy, oz, true);
        if (!(state instanceof ItemContainerBlockState containerState)) {
            return null;
        }

        ItemContainer container = containerState.getItemContainer();
        if (container == null) {
            return null;
        }

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return null;
        }

        BlockType blockType = chunk.getBlockType(ox & 31, oy, oz & 31);
        return new SourceInventory(container, blockType, ox, oy, oz);
    }

    @Nonnull
    private static SlotRange getExtractableSlotRange(@Nullable BlockType blockType, @Nonnull ItemContainer container) {
        int cap = container.getCapacity();
        if (cap <= 0) {
            return new SlotRange(0, 0);
        }

        if (blockType != null) {
            if (blockType.getState() != null && "poweredFurnace".equals(blockType.getState().getId())) {
                // Combined container layout: [0]=input, [1]=output
                if (cap >= 2) {
                    return new SlotRange(cap - 1, cap);
                }
            }
            Bench bench = blockType.getBench();
            if (bench instanceof ProcessingBench processing) {
                // Container layout: [inputs][fuel][outputs]
                int tierLevel = 1;
                int inputCount = 0;
                try {
                    inputCount = processing.getInput(tierLevel).length;
                } catch (Throwable ignored) {
                }
                int fuelCount = processing.getFuel() != null ? processing.getFuel().length : 0;
                int outputCount = 1;
                try {
                    outputCount = processing.getOutputSlotsCount(tierLevel);
                } catch (Throwable ignored) {
                }

                int start = inputCount + fuelCount;
                int end = start + outputCount;

                if (start < 0 || start >= cap || end > cap) {
                    int s = Math.max(0, cap - outputCount);
                    return new SlotRange(s, cap);
                }
                return new SlotRange(start, end);
            }
        }

        // Default: extract from any slot
        return new SlotRange(0, cap);
    }

    private static int moveUpToNItems(@Nonnull ItemContainer source, @Nonnull ItemContainer destination, 
                                      @Nonnull SlotRange range, int maxToMove) {
        if (maxToMove <= 0 || range.isEmpty()) {
            return 0;
        }

        int totalMoved = 0;
        for (int i = range.startInclusive; i < range.endExclusive && totalMoved < maxToMove; i++) {
            short slot = (short) i;
            ItemStack stack = source.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }

            int beforeCount = stack.getQuantity();
            int toMove = Math.min(beforeCount, maxToMove - totalMoved);
            var tx = source.moveItemStackFromSlot(slot, toMove, destination, false, true);
            if (tx != null && tx.succeeded()) {
                ItemStack stackAfter = source.getItemStack(slot);
                int afterCount = (stackAfter == null || ItemStack.isEmpty(stackAfter)) ? 0 : stackAfter.getQuantity();
                int movedNow = beforeCount - afterCount;
                if (movedNow > 0) {
                    totalMoved += movedNow;
                }
            }
        }

        return totalMoved;
    }

    private List<InventoryEndpoint> findAllReachableInventories(@Nonnull World world, int startPipeX, int startPipeY, 
                                                                 int startPipeZ, LongOpenHashSet excludedKeys,
                                                                 Store<ChunkStore> store) {
        List<InventoryEndpoint> found = new ArrayList<>();
        LongOpenHashSet foundKeys = new LongOpenHashSet();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        long start = packBlockPos(startPipeX, startPipeY, startPipeZ);
        visited.add(start);
        queue.enqueue(start);

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int x = unpackX(current);
            int y = unpackY(current);
            int z = unpackZ(current);

            // Get pipe component at current position to check traversal rules
            WorldChunk currentChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (currentChunk == null) {
                currentChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            }
            if (currentChunk == null) {
                continue;
            }

            var currentEntity = currentChunk.getBlockComponentEntity(x & 31, y, z & 31);
            BasicItemPipeComponent currentPipe = null;
            if (currentEntity != null) {
                currentPipe = store.getComponent(currentEntity, pipeComponentType);
            }

            for (int i = 0; i < DIRECTIONS.length; i++) {
                Vector3i dir = DIRECTIONS[i];
                
                // Check if we can traverse from this pipe in this direction
                if (currentPipe != null && !currentPipe.isSideConnected(dir)) {
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
                BasicItemPipeComponent neighborPipe = null;
                if (entity != null) {
                    neighborPipe = store.getComponent(entity, pipeComponentType);
                }

                if (neighborPipe != null) {
                    // It's a pipe - check if we can traverse through it
                    Vector3i oppositeDir = new Vector3i(-dir.x, -dir.y, -dir.z);
                    if (!neighborPipe.isSideConnected(oppositeDir)) {
                        continue;
                    }

                    long nkey = packBlockPos(ox, oy, oz);
                    if (visited.add(nkey)) {
                        queue.enqueue(nkey);
                    }
                    continue;
                }

                // Non-pipe: inventory target (only allow delivery on Default faces)
                if (currentPipe != null && currentPipe.getConnectionState(dir) != BasicItemPipeComponent.ConnectionState.Default) {
                    continue;
                }

                // ECS inventory: FuelInventory component (generic ECS container)
                Ref<ChunkStore> invRef = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
                if (invRef != null) {
                    var fuelInv = invRef.getStore().getComponent(invRef, HytaleIndustriesPlugin.INSTANCE.getFuelInventoryType());
                    if (fuelInv != null && fuelInv.fuelContainer != null) {
                        long invKey = packBlockPos(ox, oy, oz);
                        if (!excludedKeys.contains(invKey) && foundKeys.add(invKey)) {
                            found.add(new InventoryEndpoint(fuelInv.fuelContainer, invKey));
                        }
                    }
                }

                // Legacy BlockState inventory
                if (!foundKeys.contains(packBlockPos(ox, oy, oz))) {
                    var state = world.getState(ox, oy, oz, true);
                    if (state instanceof ItemContainerBlockState containerState) {
                        long invKey = packBlockPos(ox, oy, oz);
                        if (!excludedKeys.contains(invKey) && foundKeys.add(invKey)) {
                            ItemContainer container = containerState.getItemContainer();
                            if (container != null) {
                                found.add(new InventoryEndpoint(container, invKey));
                            }
                        }
                    }
                }
            }
        }

        return found;
    }

    // Helper classes and methods

    private static final class SlotRange {
        final int startInclusive;
        final int endExclusive;

        SlotRange(int startInclusive, int endExclusive) {
            this.startInclusive = Math.max(0, startInclusive);
            this.endExclusive = Math.max(this.startInclusive, endExclusive);
        }

        boolean isEmpty() {
            return this.endExclusive <= this.startInclusive;
        }

        int size() {
            return this.endExclusive - this.startInclusive;
        }
    }

    private static final class SourceInventory {
        final ItemContainer container;
        final BlockType blockType;
        final int x;
        final int y;
        final int z;

        SourceInventory(@Nonnull ItemContainer container, @Nullable BlockType blockType, int x, int y, int z) {
            this.container = container;
            this.blockType = blockType;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class InventoryEndpoint {
        final ItemContainer container;
        final long packedPos;

        InventoryEndpoint(ItemContainer container, long packedPos) {
            this.container = container;
            this.packedPos = packedPos;
        }
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
