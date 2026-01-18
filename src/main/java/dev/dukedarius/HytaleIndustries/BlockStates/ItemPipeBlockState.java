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
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemPipeBlockState extends BlockState implements TickableBlockState {

    public static final String STATE_ID = "itemPipe";
    public static final String EXTRACT_STATE = "Extract";

    // Per-side configuration encoded as 2 bits per direction (N,S,W,E,U,D).
    // 0 = Default, 1 = Extract, 2 = None (no connection).
    private int sideConfig = 0;

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
        WorldChunk c = getChunk();
        if (c != null) {
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), raw);
        }
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

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // Only run once per second.
        secondsAccumulator += dt;
        if (secondsAccumulator < 1.0f) {
            return;
        }
        // If the server lags and dt is large, don't "catch up" by moving many times.
        secondsAccumulator = 0.0f;

        WorldChunk chunk = getChunk();
        if (chunk == null) {
            return;
        }
        World world = chunk.getWorld();

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

            long excludedInventoryKey = packBlockPos(sx, sy, sz);
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

        SourceInventory(@Nonnull ItemContainer container, @Nullable BlockType blockType) {
            this.container = container;
            this.blockType = blockType;
        }
    }

    @Nullable
    private static SourceInventory getInventoryIfLoaded(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }

        BlockState state = chunk.getState(x, y, z);
        if (!(state instanceof ItemContainerBlockState inv)) {
            return null;
        }

        ItemContainer container = inv.getItemContainer();
        if (container == null) {
            return null;
        }

        BlockType blockType = chunk.getBlockType(x, y, z);
        return new SourceInventory(container, blockType);
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

                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (chunk == null) {
                    continue;
                }

                BlockType neighborType = chunk.getBlockType(nx, ny, nz);
                if (isPipe(neighborType)) {
                    // Respect the neighbor's opposite face.
                    if (!canTraverseFromPipe(world, nx, ny, nz, dir.opposite())) {
                        continue;
                    }

                    long nkey = packBlockPos(nx, ny, nz);
                    if (visited.add(nkey)) {
                        queue.enqueue(nkey);
                    }
                    continue;
                }

                // Non-pipe: see if it's an inventory. "None" disables connecting to inventories too.
                BlockState state = chunk.getState(nx, ny, nz);
                if (state instanceof ItemContainerBlockState inv) {
                    long invKey = packBlockPos(nx, ny, nz);
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
}
