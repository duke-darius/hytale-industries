package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Energy.HEComponents;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.TransfersHE;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore;
import dev.dukedarius.HytaleIndustries.Pipes.SideConfigurableConduit;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Power Cables transfer HE (Hytale Energy).
 *
 * Semantics (mirrors Item Pipe configuration):
 * - Extract sides: pull HE from an adjacent block that implements TransfersHE.
 * - Default sides: allow the network to deliver HE into an adjacent ReceivesHE block.
 * - None sides: no connection.
 */
public class PowerCableBlockState extends BlockState implements TickableBlockState, SideConfigurableConduit {

    public static final String STATE_ID = "powerCable";

    // 2 bits per direction, same encoding as ItemPipeBlockState.
    private int sideConfig = 0;
    private boolean visualDirty = true;

    private boolean initialized = false;
    private float secondsAccumulator = 0.0f;

    // Simple default throughput per cable (per second). Tune later.
    private static final double TRANSFER_HE_PER_SECOND = 250.0;

    public static final Codec<PowerCableBlockState> CODEC = BuilderCodec.builder(PowerCableBlockState.class, PowerCableBlockState::new, BlockState.BASE_CODEC)
            .append(new KeyedCodec<>("SideConfig", Codec.INTEGER), (s, v) -> s.sideConfig = v, s -> s.sideConfig)
            .add()
            .build();

    private int getSideConfig() {
        WorldChunk c = getChunk();
        if (c != null) {
            return PipeSideConfigStore.getOrDefault(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
        }
        return sideConfig;
    }

    @Override
    public int getRawSideConfig() {
        return sideConfig;
    }

    @Override
    public void setRawSideConfig(int raw) {
        sideConfig = raw;
        visualDirty = true;
        WorldChunk c = getChunk();
        if (c != null) {
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), raw);
            c.markNeedsSaving();
        }
    }

    @Override
    public ItemPipeBlockState.ConnectionState getConnectionState(ItemPipeBlockState.Direction dir) {
        return ItemPipeBlockState.getConnectionStateFromSideConfig(getSideConfig(), dir);
    }

    @Override
    public void setConnectionState(ItemPipeBlockState.Direction dir, ItemPipeBlockState.ConnectionState state) {
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
            c.markNeedsSaving();
        }
    }

    @Override
    public ItemPipeBlockState.ConnectionState cycleConnectionState(ItemPipeBlockState.Direction dir) {
        ItemPipeBlockState.ConnectionState next = switch (getConnectionState(dir)) {
            case Default -> ItemPipeBlockState.ConnectionState.Extract;
            case Extract -> ItemPipeBlockState.ConnectionState.None;
            case None -> ItemPipeBlockState.ConnectionState.Default;
        };
        setConnectionState(dir, next);
        return next;
    }

    @Override
    public boolean isSideConnected(ItemPipeBlockState.Direction dir) {
        return getConnectionState(dir) != ItemPipeBlockState.ConnectionState.None;
    }

    private boolean hasAnyExtractSide() {
        for (ItemPipeBlockState.Direction d : ItemPipeBlockState.Direction.values()) {
            if (getConnectionState(d) == ItemPipeBlockState.ConnectionState.Extract) {
                return true;
            }
        }
        return false;
    }

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    private boolean neighborIsCable(@Nonnull World world, int x, int y, int z, @Nonnull ItemPipeBlockState.Direction dir) {
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
            return ItemPipeBlockState.getConnectionStateFromSideConfig(otherCfg, dir.opposite()) != ItemPipeBlockState.ConnectionState.None;
        }
        return false;
    }

    private boolean neighborIsEnergy(@Nonnull World world, int x, int y, int z, @Nonnull ItemPipeBlockState.Direction dir) {
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        int nz = z + dir.dz;
        if (ny < WORLD_MIN_Y || ny >= WORLD_MAX_Y_EXCLUSIVE) {
            return false;
        }

        int[] origin = resolveFillerOrigin(world, nx, ny, nz);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        // Quick in-memory check.
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return false;
        }

        // Connect to either sources or sinks.
        return HEComponents.transfers(world, ox, oy, oz) != null || HEComponents.receives(world, ox, oy, oz) != null;
    }

    private boolean reconcileNeighborFaces() {
        WorldChunk chunk = getChunk();
        if (chunk == null) return false;
        World world = chunk.getWorld();

        int cfg = getSideConfig();
        int original = cfg;
        for (ItemPipeBlockState.Direction d : ItemPipeBlockState.Direction.values()) {
            ItemPipeBlockState.ConnectionState cs = ItemPipeBlockState.getConnectionStateFromSideConfig(cfg, d);
            boolean canCable = neighborIsCable(world, getBlockX(), getBlockY(), getBlockZ(), d);
            boolean canEnergy = neighborIsEnergy(world, getBlockX(), getBlockY(), getBlockZ(), d);
            if (cs == ItemPipeBlockState.ConnectionState.Default && !(canCable || canEnergy)) {
                setConnectionState(d, ItemPipeBlockState.ConnectionState.None);
                cfg = sideConfig;
            } else if (cs == ItemPipeBlockState.ConnectionState.None && (canCable || canEnergy)) {
                // Auto-open faces to other cables or energy machines.
                setConnectionState(d, ItemPipeBlockState.ConnectionState.Default);
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

        // Keep a stable rotation so model state matches world-space directions.
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
            sideConfig = PipeSideConfigStore.getOrDefault(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
            PipeSideConfigStore.set(getBlockX(), getBlockY(), getBlockZ(), sideConfig);
            initialized = true;
            reconcileNeighborFaces();
            visualDirty = true;
            applyAnimationState();
            return;
        }

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

        // Only run 4 times per second.
        secondsAccumulator += dt;
        if (secondsAccumulator < 0.25f) {
            return;
        }
        double budget = TRANSFER_HE_PER_SECOND * secondsAccumulator;
        secondsAccumulator = 0.0f;

        if (!hasAnyExtractSide()) {
            return;
        }

        World world = chunk.getWorld();

        int cableX = getBlockX();
        int cableY = getBlockY();
        int cableZ = getBlockZ();

        List<HETransferEndpoint> sources = new ArrayList<>();
        LongOpenHashSet excludedKeys = new LongOpenHashSet();
        for (ItemPipeBlockState.Direction dir : ItemPipeBlockState.Direction.values()) {
            if (getConnectionState(dir) == ItemPipeBlockState.ConnectionState.Extract) {
                int sx = cableX + dir.dx;
                int sy = cableY + dir.dy;
                int sz = cableZ + dir.dz;
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

        List<HEReceiveEndpoint> receivers = findAllReachableReceivers(world, cableX, cableY, cableZ, excludedKeys);
        if (receivers.isEmpty()) {
            return;
        }

        // Evenly distribute available energy among all receivers.
        double totalAvailable = 0;
        for (HETransferEndpoint source : sources) {
            totalAvailable += source.transfers.getHeStored();
        }
        double amountToMove = Math.min(totalAvailable, budget);
        if (amountToMove <= 0.0) return;

        // Sort receivers by free capacity ascending to ensure optimal fair distribution.
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

    private static boolean isCable(@Nullable BlockType blockType) {
        return blockType != null && blockType.getState() != null && STATE_ID.equals(blockType.getState().getId());
    }

    @Nullable
    private static PowerCableBlockState getCableStateIfLoaded(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (stateRef == null) {
            return null;
        }

        ComponentType<ChunkStore, PowerCableBlockState> type = BlockStateModule.get().getComponentType(PowerCableBlockState.class);
        if (type == null) {
            return null;
        }

        return stateRef.getStore().getComponent(stateRef, type);
    }

    private static boolean canTraverseFromCable(@Nonnull World world, int x, int y, int z, @Nonnull ItemPipeBlockState.Direction dir) {
        PowerCableBlockState cable = getCableStateIfLoaded(world, x, y, z);
        if (cable == null) {
            return true; // fallback
        }
        return cable.isSideConnected(dir);
    }

    private static List<HEReceiveEndpoint> findAllReachableReceivers(@Nonnull World world, int startCableX, int startCableY, int startCableZ, LongOpenHashSet excludedKeys) {
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

            for (ItemPipeBlockState.Direction dir : ItemPipeBlockState.Direction.values()) {
                if (!canTraverseFromCable(world, x, y, z, dir)) {
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

                BlockType neighborType = chunk.getBlockType(ox & 31, oy, oz & 31);

                if (isCable(neighborType)) {
                    // Respect neighbor opposite face.
                    if (!canTraverseFromCable(world, ox, oy, oz, dir.opposite())) {
                        continue;
                    }

                    long nkey = packBlockPos(ox, oy, oz);
                    if (visited.add(nkey)) {
                        queue.enqueue(nkey);
                    }
                    continue;
                }

                // Only allow delivery on Default faces.
                PowerCableBlockState here = getCableStateIfLoaded(world, x, y, z);
                if (here != null && here.getConnectionState(dir) != ItemPipeBlockState.ConnectionState.Default) {
                    continue;
                }

                long receiverKey = packBlockPos(ox, oy, oz);
                if (excludedKeys.contains(receiverKey) || !foundKeys.add(receiverKey)) {
                    continue;
                }

                // Non-cable: see if it's a receiver.
                HEReceiveEndpoint ep = getReceivesEndpointIfLoaded(world, ox, oy, oz);
                if (ep != null) {
                    found.add(ep);
                }
            }
        }

        return found;
    }

    private static final class HETransferEndpoint {
        final Ref<ChunkStore> ref;
        final ComponentType<ChunkStore, Component<ChunkStore>> type;
        final Component<ChunkStore> component;
        final TransfersHE transfers;
        final WorldChunk chunk;
        final int x;
        final int y;
        final int z;

        HETransferEndpoint(Ref<ChunkStore> ref, ComponentType<ChunkStore, Component<ChunkStore>> type, Component<ChunkStore> component, TransfersHE transfers, WorldChunk chunk, int x, int y, int z) {
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

        HEReceiveEndpoint(Ref<ChunkStore> ref, ComponentType<ChunkStore, Component<ChunkStore>> type, Component<ChunkStore> component, ReceivesHE receives, WorldChunk chunk, int x, int y, int z) {
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

    @Nullable
    private static HETransferEndpoint getTransfersEndpointIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            return null;
        }

        BlockState st = chunk.getState(ox & 31, oy, oz & 31);
        if (!(st instanceof TransfersHE)) {
            return null;
        }

        Ref<ChunkStore> ref = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (ref == null) {
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) st.getClass());
        if (type == null) {
            return null;
        }

        Component<ChunkStore> component = ref.getStore().getComponent(ref, type);
        if (!(component instanceof TransfersHE transfers)) {
            return null;
        }

        return new HETransferEndpoint(ref, type, component, transfers, chunk, ox, oy, oz);
    }

    @Nullable
    private static HEReceiveEndpoint getReceivesEndpointIfLoaded(@Nonnull World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            return null;
        }

        BlockState st = chunk.getState(ox & 31, oy, oz & 31);
        if (!(st instanceof ReceivesHE)) {
            return null;
        }

        Ref<ChunkStore> ref = chunk.getBlockComponentEntity(ox & 31, oy, oz & 31);
        if (ref == null) {
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) st.getClass());
        if (type == null) {
            return null;
        }

        Component<ChunkStore> component = ref.getStore().getComponent(ref, type);
        if (!(component instanceof ReceivesHE receives)) {
            return null;
        }

        return new HEReceiveEndpoint(ref, type, component, receives, chunk, ox, oy, oz);
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
