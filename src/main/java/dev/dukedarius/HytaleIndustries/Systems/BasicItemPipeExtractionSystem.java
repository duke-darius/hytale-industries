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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent.FilterMode;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
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
    private static final String POWER_CABLE_BLOCK_ID = "HytaleIndustries_BasicPowerCable";
    private static final String ITEM_PIPE_BLOCK_ID = "HytaleIndustries_BasicItemPipe";
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

                SourceInventory sourceInv = getInventoryIfLoaded(world, store, sx, sy, sz);
                if (sourceInv != null) {
                    FilterMode mode = pipe.getFilterMode(dir);
                    String[] items = pipe.getFilterItems(dir);
                    sources.add(new SourceInventory(sourceInv.inventory, sx, sy, sz, dir, mode, items));
                    excludedKeys.add(packBlockPos(sourceInv.x, sourceInv.y, sourceInv.z));
                }
            }
        }

        if (sources.isEmpty()) {
            return;
        }

        // Find all reachable destination inventories through the pipe network
        List<InventoryEndpoint> endpoints = findAllReachableInventories(world, store, pipeX, pipeY, pipeZ, excludedKeys);
        if (endpoints.isEmpty()) {
            return;
        }

        // Move up to 256 items per tick from sources to destinations
        int totalMoved = 0;
        for (SourceInventory sourceInv : sources) {
            if (totalMoved >= 256) break;

            MachineInventory source = sourceInv.inventory;
            if (source == null || source.getContainer() == null || source.getContainer().isEmpty()) continue;

            for (InventoryEndpoint ep : endpoints) {
                int count = moveUpToNItems(sourceInv, ep, 256 - totalMoved);
                if (count > 0) totalMoved += count;
                if (totalMoved >= 256) break;
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
    private static SourceInventory getInventoryIfLoaded(@Nonnull World world, Store<ChunkStore> store, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        List<MachineInventory> inventories = InventoryAdapters.find(world, store, ox, oy, oz);
        if (inventories.isEmpty()) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log(
                    "[PipeExtraction] no inventory at (%d,%d,%d) from pipe (%d,%d,%d)",
                    ox, oy, oz, x, y, z);
        }
        for (MachineInventory inv : inventories) {
            if (inv != null && inv.hasOutputSlots()) {
                return new SourceInventory(inv, ox, oy, oz);
            }
        }
        return null;
    }

    @Nonnull
    private static int moveUpToNItems(@Nonnull SourceInventory sourceInv,
                                      @Nonnull InventoryEndpoint dest,
                                      int maxToMove) {
        if (maxToMove <= 0) {
            return 0;
        }

        MachineInventory source = sourceInv.inventory;
        MachineInventory destination = dest.inventory;
        if (source == null || destination == null) {
            return 0;
        }

        var sourceContainer = source.getContainer();
        var destContainer = destination.getContainer();
        if (sourceContainer == null || destContainer == null) return 0;

        int totalMoved = 0;

        for (int i = 0; i < source.getSlotCount() && totalMoved < maxToMove; i++) {
            if (!source.getSlotIO(i).allowsOutput()) continue;
            short slot = (short) i;
            ItemStack stack = sourceContainer.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) {
                continue;
            }

            String itemId = stack.getItemId();
            // Extraction filter: only allow items matching this side's extract filter
            if (!allowsFilter(sourceInv.extractMode, sourceInv.extractItems, itemId)) {
                continue;
            }

            // Insertion filter: only allow items matching destination side's insert filter
            if (!allowsFilter(dest.insertMode, dest.insertItems, itemId)) {
                continue;
            }

            int beforeCount = stack.getQuantity();
            int toMove = Math.min(beforeCount, maxToMove - totalMoved);
            if (!destination.hasInputSlots()) continue;

            int movedNow = tryInsertRespectingIO(sourceContainer, slot, toMove, destination, destContainer);
            if (movedNow > 0) {
                totalMoved += movedNow;
            }
        }

        return totalMoved;
    }

    private static boolean allowsFilter(FilterMode mode, String[] items, String itemId) {
        if (mode == null || mode == FilterMode.None || itemId == null) {
            return true;
        }

        boolean listed = false;
        if (items != null && items.length > 0) {
            for (String id : items) {
                if (itemId.equals(id)) {
                    listed = true;
                    break;
                }
            }
        }

        return switch (mode) {
            case Whitelist -> listed;
            case Blacklist -> !listed;
            case None -> true;
        };
    }

    private static int tryInsertRespectingIO(com.hypixel.hytale.server.core.inventory.container.ItemContainer sourceContainer,
                                             short sourceSlot,
                                             int requested,
                                             MachineInventory destInv,
                                             com.hypixel.hytale.server.core.inventory.container.ItemContainer destContainer) {
        ItemStack stack = sourceContainer.getItemStack(sourceSlot);
        if (stack == null || ItemStack.isEmpty(stack) || requested <= 0) return 0;

        int remaining = Math.min(requested, stack.getQuantity());
        int moved = 0;

        for (int d = 0; d < destInv.getSlotCount() && remaining > 0; d++) {
            if (!destInv.getSlotIO(d).allowsInput()) continue;
            short dstSlot = (short) d;
            ItemStack dst = destContainer.getItemStack(dstSlot);

            if (dst == null || ItemStack.isEmpty(dst)) {
                int move = remaining;
                ItemStack placed = stack.withQuantity(move);
                destContainer.setItemStackForSlot(dstSlot, placed);
                remaining -= move;
                moved += move;
            } else if (stack.getItemId().equals(dst.getItemId())) {
                int maxStack = stack.getItem().getMaxStack();

                int space = maxStack - dst.getQuantity();
                if (space > 0) {
                    int move = Math.min(space, remaining);
                    destContainer.setItemStackForSlot(dstSlot, dst.withQuantity(dst.getQuantity() + move));
                    remaining -= move;
                    moved += move;
                }
            }
        }

        if (moved > 0) {
            int newQty = stack.getQuantity() - moved;
            sourceContainer.setItemStackForSlot(sourceSlot, newQty <= 0 ? ItemStack.EMPTY : stack.withQuantity(newQty));
            HytaleIndustriesPlugin.LOGGER.atInfo().log(
                    "[PipeExtraction] inserted=%d item=%s srcSlot=%d dstSlots=%d maxStackUsed=%d",
                    moved, stack.getItemId(), sourceSlot, destInv.getSlotCount(),
                    stack.getItem().getMaxStack());
        }

        return moved;
    }

    private List<InventoryEndpoint> findAllReachableInventories(@Nonnull World world, Store<ChunkStore> store, int startPipeX, int startPipeY,
                                                                 int startPipeZ, LongOpenHashSet excludedKeys) {
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

                // If this block is a power cable, treat it as non-traversable for items.
                BlockType blockType = chunk.getBlockType(ox & 31, oy, oz & 31);
                String blockId = blockType != null ? blockType.getId() : null;
                // Normalize id to base block id (strip '*' + state suffix) before comparison,
                // since runtime ids often look like "*HytaleIndustries_BasicPowerCable_State_...".
                String normalizedId = blockId;
                if (normalizedId != null) {
                    if (normalizedId.startsWith("*")) {
                        normalizedId = normalizedId.substring(1);
                    }
                    int stateIdx = normalizedId.indexOf("_State_");
                    if (stateIdx > 0) {
                        normalizedId = normalizedId.substring(0, stateIdx);
                    }
                }
                if (POWER_CABLE_BLOCK_ID.equals(normalizedId)) {
                    // Do not traverse into power cables at all from the item-pipe graph.
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

                List<MachineInventory> inventories = InventoryAdapters.find(world, store, ox, oy, oz);
                if (inventories.isEmpty()) {
                    continue;
                }

                BlockType invBlockType = world.getBlockType(ox, oy, oz);
                String invBlockId = invBlockType != null ? invBlockType.getId() : null;

                String baseId = invBlockId;
                if (baseId != null) {
                    if (baseId.startsWith("*")) {
                        baseId = baseId.substring(1);
                    }
                    int stateIdx = baseId.indexOf("_State_");
                    if (stateIdx > 0) {
                        baseId = baseId.substring(0, stateIdx);
                    }
                }

                // Never treat our own conduits (item pipes or power cables) as item destinations,
                // even if some adapter exposes an ItemContainer for them.
                if (ITEM_PIPE_BLOCK_ID.equals(baseId) || POWER_CABLE_BLOCK_ID.equals(baseId)) {
                    continue;
                }

                for (MachineInventory inv : inventories) {
                    long invKey = packBlockPos(ox, oy, oz);
                    if (!inv.hasInputSlots()) {
                        continue;
                    }
                    if (!excludedKeys.contains(invKey) && foundKeys.add(invKey)) {
                        FilterMode insertMode = currentPipe != null ? currentPipe.getFilterMode(dir) : FilterMode.None;
                        String[] insertItems = currentPipe != null ? currentPipe.getFilterItems(dir) : null;

                        found.add(new InventoryEndpoint(inv, invKey, insertMode, insertItems));
                    }
                }
            }
        }

        return found;
    }

    // Helper classes and methods


    private static final class SourceInventory {
        final MachineInventory inventory;
        final int x;
        final int y;
        final int z;
        final Vector3i dir;
        final FilterMode extractMode;
        final String[] extractItems;
        SourceInventory(@Nonnull MachineInventory inventory,
                        int x, int y, int z,
                        @Nonnull Vector3i dir,
                        @Nonnull FilterMode mode,
                        @Nullable String[] items) {
            this.inventory = inventory;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = new Vector3i(dir.x, dir.y, dir.z);
            this.extractMode = mode;
            this.extractItems = items != null ? items.clone() : null;
        }

        // Convenience constructor used by getInventoryIfLoaded before filter data is attached
        SourceInventory(@Nonnull MachineInventory inventory, int x, int y, int z) {
            this(inventory, x, y, z, new Vector3i(0, 0, 0), FilterMode.None, null);
        }
    }

    private static final class InventoryEndpoint {
        final MachineInventory inventory;
        final long packedPos;
        final FilterMode insertMode;
        final String[] insertItems;
        InventoryEndpoint(MachineInventory inventory,
                          long packedPos,
                          @Nonnull FilterMode mode,
                          @Nullable String[] items) {
            this.inventory = inventory;
            this.packedPos = packedPos;
            this.insertMode = mode;
            this.insertItems = items != null ? items.clone() : null;
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
