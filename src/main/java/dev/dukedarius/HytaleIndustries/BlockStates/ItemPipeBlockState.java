package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;
import dev.dukedarius.HytaleIndustries.Pipes.SideConfigurableConduit;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemPipeBlockState extends BlockState implements TickableBlockState, SideConfigurableConduit {

    public static final String STATE_ID = "itemPipe";
    public static final String EXTRACT_STATE = "Extract";

    // Per-side configuration encoded as 2 bits per direction (N,S,W,E,U,D).
    // 0 = Default, 1 = Extract, 2 = None (no connection).
    private int sideConfig = 0;
    private boolean visualDirty = true;
    private int lastLoggedRaw = -1;

    public static final Codec<ItemPipeBlockState> CODEC = BuilderCodec.builder(ItemPipeBlockState.class, ItemPipeBlockState::new, BlockState.BASE_CODEC)
            .append(new KeyedCodec<>("SideConfig", Codec.INTEGER), (s, v) -> s.sideConfig = v, s -> s.sideConfig)
            .add()
            .build();

    public enum ConnectionState {
        Default,
        Extract,
        None
    }

    public enum Direction {
        North(0, 0, -1, 0),
        South(0, 0, 1, 1),
        West(-1, 0, 0, 2),
        East(1, 0, 0, 3),
        Up(0, 1, 0, 4),
        Down(0, -1, 0, 5);

        public final int dx;
        public final int dy;
        public final int dz;
        public final int index;

        Direction(int dx, int dy, int dz, int index) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.index = index;
        }

        public Direction opposite() {
            return switch (this) {
                case North -> South;
                case South -> North;
                case West -> East;
                case East -> West;
                case Up -> Down;
                case Down -> Up;
            };
        }
    }


    private int getSideConfig() {
        WorldChunk c = getChunk();
        if (c != null) {
            // Authoritative side config is stored outside the BlockState component because connected-block updates
            // recreate components and wipe custom fields.
            return PipeSideConfigStore.getOrDefault(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
        }
        return sideConfig;
    }

    public int getRawSideConfig() {
        return sideConfig;
    }

    public void setRawSideConfig(int raw) {
        sideConfig = raw;
        visualDirty = true;
        WorldChunk c = getChunk();
        if (c != null) {
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), raw);
        }
    }

    public ItemPipeBlockState() {
        super();
        sideConfig = 0;
        visualDirty = true;
    }

    public ConnectionState getConnectionState(Direction dir) {
        int cfg = getSideConfig();
        int v = (cfg >>> (dir.index * 2)) & 0b11;
        return switch (v) {
            case 1 -> ConnectionState.Extract;
            case 2 -> ConnectionState.None;
            default -> ConnectionState.Default;
        };
    }

    public void setConnectionState(Direction dir, ConnectionState state) {
        int shift = dir.index * 2;
        int mask = 0b11 << shift;
        int v = switch (state) {
            case Default -> 0;
            case Extract -> 1;
            case None -> 2;
        };

        int cfg = getSideConfig();
        cfg = (cfg & ~mask) | (v << shift);
        sideConfig = cfg;
        visualDirty = true;

        WorldChunk c = getChunk();
        if (c != null) {
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), cfg);
        }
    }

    public ConnectionState cycleConnectionState(Direction dir) {
        ConnectionState next = switch (getConnectionState(dir)) {
            case Default -> ConnectionState.Extract;
            case Extract -> ConnectionState.None;
            case None -> ConnectionState.Default;
        };
        setConnectionState(dir, next);
        return next;
    }

    public static ConnectionState getConnectionStateFromSideConfig(int sideConfig, Direction dir) {
        int v = (sideConfig >>> (dir.index * 2)) & 0b11;
        return switch (v) {
            case 1 -> ConnectionState.Extract;
            case 2 -> ConnectionState.None;
            default -> ConnectionState.Default;
        };
    }

    public boolean isSideConnected(Direction dir) {
        return getConnectionState(dir) != ConnectionState.None;
    }

    private boolean hasAnyExtractSide() {
        for (Direction d : Direction.values()) {
            if (getConnectionState(d) == ConnectionState.Extract) {
                return true;
            }
        }
        return false;
    }

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private float secondsAccumulator = 0.0f;
    private boolean initialized = false;

    private boolean neighborIsPipe(@Nonnull World world, int x, int y, int z, @Nonnull Direction dir) {
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        int nz = z + dir.dz;
        if (ny < WORLD_MIN_Y || ny >= WORLD_MAX_Y_EXCLUSIVE) {
            return false;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(nx, nz);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }

        BlockType neighborType = chunk.getBlockType(nx & 31, ny, nz & 31);
        if (neighborType != null && neighborType.getState() != null && STATE_ID.equals(neighborType.getState().getId())) {
            int otherCfg = PipeSideConfigStore.get(nx, ny, nz);
            return getConnectionStateFromSideConfig(otherCfg, dir.opposite()) != ConnectionState.None;
        }
        return false;
    }

    private boolean neighborIsInventory(@Nonnull World world, int x, int y, int z, @Nonnull Direction dir) {
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        int nz = z + dir.dz;
        if (ny < WORLD_MIN_Y || ny >= WORLD_MAX_Y_EXCLUSIVE) {
            return false;
        }
        int[] origin = resolveFillerOrigin(world, nx, ny, nz);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        long chunkIndex = ChunkUtil.indexChunkFromBlock(ox, oz);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(chunkIndex);
        }
        if (chunk == null) {
            return false;
        }
        BlockState st = chunk.getState(ox & 31, oy, oz & 31);
        if (st instanceof ItemContainerBlockState) {
            return true;
        }

        // Fallback: check component entity for container at origin.
        Ref<ChunkStore> ref = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (ref != null) {
            BlockState bs = BlockState.getBlockState(ref, ref.getStore());
            return bs instanceof ItemContainerBlockState;
        }
        return false;
    }

    private boolean reconcileNeighborFaces() {
        WorldChunk chunk = getChunk();
        if (chunk == null) return false;
        World world = chunk.getWorld();

        int cfg = getSideConfig();
        int original = cfg;
        for (Direction d : Direction.values()) {
            ConnectionState cs = getConnectionStateFromSideConfig(cfg, d);
            boolean canPipe = neighborIsPipe(world, getBlockX(), getBlockY(), getBlockZ(), d);
            boolean canInv = neighborIsInventory(world, getBlockX(), getBlockY(), getBlockZ(), d);
            if (cs == ConnectionState.Default && !(canPipe || canInv)) {
                setConnectionState(d, ConnectionState.None);
                cfg = sideConfig;
            } else if (cs == ConnectionState.None && canPipe) {
                setConnectionState(d, ConnectionState.Default);
                cfg = sideConfig;
            }
        }
        return cfg != original;
    }
    private void applyAnimationState() {
        visualDirty = false;
        WorldChunk chunk = getChunk();
        if (chunk == null) {
            return;
        }
        int lx = getBlockX() & 31;
        int lz = getBlockZ() & 31;
        BlockType blockType = chunk.getBlockType(lx, getBlockY(), lz);
        if (blockType == null) {
            return;
        }
        int currentRot = chunk.getRotationIndex(lx, getBlockY(), lz);
        if (currentRot != 0) {
            int settings = 64 | 256 | 4 | 2;
            int filler = chunk.getFiller(lx, getBlockY(), lz);
            int blockId = chunk.getBlock(lx, getBlockY(), lz);
            chunk.setBlock(lx, getBlockY(), lz, blockId, blockType, 0, filler, settings);
        }
        int raw = getSideConfig();
        String stateName = String.format("State%03d", raw);
        chunk.setBlockInteractionState(getBlockX(), getBlockY(), getBlockZ(), blockType, stateName, true);
        chunk.markNeedsSaving();
        if (raw != lastLoggedRaw) {
            lastLoggedRaw = raw;
        }
    }

    private void applyNow() {
        if (reconcileNeighborFaces()) {
            visualDirty = true;
        }
        if (visualDirty) {
            applyAnimationState();
        }
    }


    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        WorldChunk chunk = getChunk();
        if (chunk == null) {
            return;
        }
        int lx = getBlockX() & 31;
        int lz = getBlockZ() & 31;
        BlockType blockType = chunk.getBlockType(lx, getBlockY(), lz);
        if (blockType == null || blockType.getState() == null || !STATE_ID.equals(blockType.getState().getId())) {
            PipeSideConfigStore.clear(getBlockX(), getBlockY(), getBlockZ());
            return;
        }
        if (!initialized) {
            sideConfig = 0;
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
            initialized = true;
            reconcileNeighborFaces();
            visualDirty = true;
            applyAnimationState();
            return;
        }
        World world = chunk.getWorld();
        int stored = PipeSideConfigStore.getOrDefault(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
        if (stored != sideConfig) {
            sideConfig = stored;
            visualDirty = true;
        }
        if (reconcileNeighborFaces()) {
            visualDirty = true;
        }
        if (visualDirty) {
            applyAnimationState();
        }
        // Only run once per second.
        secondsAccumulator += dt;
        if (secondsAccumulator < 1.0f) {
            return;
        }
        // If the server lags and dt is large, don't "catch up" by moving many times.
        secondsAccumulator = 0.0f;

        // Only do work when at least one side is configured to Extract.
        if (!hasAnyExtractSide()) {
            return;
        }

        int pipeX = getBlockX();
        int pipeY = getBlockY();
        int pipeZ = getBlockZ();

        int moved = 0;
        // Try all adjacent inventories as potential sources, sharing the 4 items/sec budget.
        for (Direction dir : Direction.values()) {
            if (moved >= 4) {
                break;
            }
            if (getConnectionState(dir) != ConnectionState.Extract) {
                continue;
            }

            int sx = pipeX + dir.dx;
            int sy = pipeY + dir.dy;
            int sz = pipeZ + dir.dz;
            if (sy < WORLD_MIN_Y || sy >= WORLD_MAX_Y_EXCLUSIVE) {
                continue;
            }

            SourceInventory sourceInv = getInventoryIfLoaded(world, sx, sy, sz);
            if (sourceInv == null) {
                continue;
            }

            ItemContainer source = sourceInv.container;
            if (source == null || source.isEmpty()) {
                continue;
            }

            // If the source is a ProcessingBench (e.g. Bench_Furnace), ONLY extract from its OUTPUT slots.
            SlotRange extractRange = getExtractableSlotRange(sourceInv.blockType, source);
            if (extractRange.isEmpty()) {
                continue;
            }

            long excludedInventoryKey = packBlockPos(sourceInv.x, sourceInv.y, sourceInv.z);
            ItemContainer destination = findNearestInventory(world, pipeX, pipeY, pipeZ, excludedInventoryKey);
            if (destination == null) {
                continue;
            }

            moved += moveUpToNItems(source, destination, extractRange, 4 - moved);
        }
    }

    private static boolean isPipe(@Nullable BlockType blockType) {
        return blockType != null && blockType.getState() != null && STATE_ID.equals(blockType.getState().getId());
    }

    @Nullable
    private static ItemPipeBlockState getPipeStateIfLoaded(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x, y, z);
        if (stateRef == null) {
            return null;
        }

        ComponentType<ChunkStore, ItemPipeBlockState> type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            return null;
        }

        return stateRef.getStore().getComponent(stateRef, type);
    }

    private static boolean canTraverseFromPipe(@Nonnull World world, int x, int y, int z, @Nonnull Direction dir) {
        ItemPipeBlockState pipe = getPipeStateIfLoaded(world, x, y, z);
        if (pipe == null) {
            return true; // fall back to old behavior if state isn't available
        }
        return pipe.isSideConnected(dir);
    }

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

    @Nullable
    private static SourceInventory getInventoryIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return null;
        }

        BlockState state = chunk.getState(ox, oy, oz);
        ItemContainerBlockState inv = null;
        if (state instanceof ItemContainerBlockState icbs) {
            inv = icbs;
        } else {
            // Fallback: load the block state from the component entity if present.
            Ref<ChunkStore> ref = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
            if (ref != null) {
                BlockState bs = BlockState.getBlockState(ref, ref.getStore());
                if (bs instanceof ItemContainerBlockState ic) {
                    inv = ic;
                }
            }
        }
        if (inv == null) {
            // Multiblock fallback: search nearby blocks of the same block type for an ItemContainer.
            BlockType neighborType = chunk.getBlockType(ox, oy, oz);
            if (neighborType == null || neighborType.getId() == null) {
                return null;
            }
            String typeId = neighborType.getId();
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
                if (sy < 0 || sy >= WORLD_MAX_Y_EXCLUSIVE) continue;
                WorldChunk sc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(sx, sz));
                if (sc == null) {
                    sc = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(sx, sz));
                }
                if (sc == null) continue;
                BlockType bt = sc.getBlockType(sx & 31, sy, sz & 31);
                if (bt == null || bt.getId() == null || !bt.getId().equals(typeId)) continue;
                BlockState st = sc.getState(sx & 31, sy, sz & 31);
                if (st instanceof ItemContainerBlockState inv2) {
                    ItemContainer container2 = inv2.getItemContainer();
                    if (container2 == null) continue;
                    return new SourceInventory(container2, bt, sx, sy, sz);
                }
                // Fallback via component entity
                Ref<ChunkStore> ref2 = sc.getBlockComponentEntity(sx & 31, sy, sz & 31);
                if (ref2 != null) {
                    BlockState bs2 = BlockState.getBlockState(ref2, ref2.getStore());
                    if (bs2 instanceof ItemContainerBlockState ic3 && ic3.getItemContainer() != null) {
                        return new SourceInventory(ic3.getItemContainer(), bt, sx, sy, sz);
                    }
                }
            }
            return null;
        }


        ItemContainer container = inv.getItemContainer();
        if (container == null) {
            return null;
        }

        BlockType blockType = chunk.getBlockType(ox, oy, oz);
        return new SourceInventory(container, blockType, ox, oy, oz);
    }

    @Nonnull
    private static SlotRange getExtractableSlotRange(@Nullable BlockType blockType, @Nonnull ItemContainer container) {
        int cap = container.getCapacity();
        if (cap <= 0) {
            return new SlotRange(0, 0);
        }

        if (blockType != null) {
            Bench bench = blockType.getBench();
            if (bench instanceof ProcessingBench processing) {
                // Assumption (matches in-game UX): container layout is [inputs][fuel][outputs].
                // We use tierLevel=1 as a safe default.
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

                // If the counts don't line up with the actual container size (mods / future changes), fall back to "last N slots".
                if (start < 0 || start >= cap || end > cap) {
                    int s = Math.max(0, cap - outputCount);
                    return new SlotRange(s, cap);
                }
                return new SlotRange(start, end);
            }
        }

        // Default: extract from any slot.
        return new SlotRange(0, cap);
    }

    private static int moveUpToNItems(@Nonnull ItemContainer source, @Nonnull ItemContainer destination, @Nonnull SlotRange range, int maxToMove) {
        if (maxToMove <= 0 || range.isEmpty()) {
            return 0;
        }

        int moved = 0;
        // Keep pulling 1-at-a-time until we hit the budget or can't move anything anymore.
        // This guarantees we can move up to 4 per second even if all items are in a single stack.
        while (moved < maxToMove) {
            boolean movedThisPass = false;

            for (int i = range.startInclusive; i < range.endExclusive && moved < maxToMove; i++) {
                short slot = (short) i;
                ItemStack stack = source.getItemStack(slot);
                if (stack == null || ItemStack.isEmpty(stack)) {
                    continue;
                }

                var tx = source.moveItemStackFromSlot(slot, 1, destination, false, true);
                if (tx != null && tx.succeeded()) {
                    moved++;
                    movedThisPass = true;
                }
            }

            if (!movedThisPass) {
                break;
            }
        }

        return moved;
    }


    @Nullable
    private static ItemContainer findNearestInventory(@Nonnull World world, int startPipeX, int startPipeY, int startPipeZ, long excludedInventoryKey) {
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

            for (Direction dir : Direction.values()) {
                if (!canTraverseFromPipe(world, x, y, z, dir)) {
                    continue;
                }

                int nx = x + dir.dx;
                int ny = y + dir.dy;
                int nz = z + dir.dz;
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
                BlockType neighborType = chunk.getBlockType(ox, oy, oz);

                if (isPipe(neighborType)) {
                    // Respect the neighbor's opposite face.
                    if (!canTraverseFromPipe(world, ox, oy, oz, dir.opposite())) {
                        continue;
                    }

                    long nkey = packBlockPos(ox, oy, oz);
                    if (visited.add(nkey)) {
                        queue.enqueue(nkey);
                    }
                    continue;
                }

                // Non-pipe: see if it's an inventory (multiblock-aware). "None" disables connecting to inventories too.
                BlockState state = chunk.getState(ox, oy, oz);
                if (state instanceof ItemContainerBlockState inv) {
                    long invKey = packBlockPos(ox, oy, oz);
                    if (invKey != excludedInventoryKey) {
                        return inv.getItemContainer();
                    }
                }
            }
        }

        return null;
    }

    // Pack world coords into a single long (26-bit X/Z, 12-bit Y).
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

    // Resolve filler blocks to their origin block coordinates; returns {x,y,z}.
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
