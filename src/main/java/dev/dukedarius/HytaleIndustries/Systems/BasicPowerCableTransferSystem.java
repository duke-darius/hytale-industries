package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Components.Energy.CableEndpoint;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import java.util.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Transfers HE from adjacent sources on Extract faces through the cable graph to reachable sinks.
 * Cables themselves do not store HE.
 */
public class BasicPowerCableTransferSystem extends EntityTickingSystem<ChunkStore> {

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    private static final Vector3i[] DIRS = {
            new Vector3i(0, 0, -1),
            new Vector3i(0, 0, 1),
            new Vector3i(-1, 0, 0),
            new Vector3i(1, 0, 0),
            new Vector3i(0, 1, 0),
            new Vector3i(0, -1, 0)
    };

    private final ComponentType<ChunkStore, BasicPowerCableComponent> cableType;
    private final ComponentType<ChunkStore, CableEndpoint> endpointType;
    private final ComponentType<ChunkStore, StoresHE> storesType;
    private final Query<ChunkStore> query;

    public BasicPowerCableTransferSystem(ComponentType<ChunkStore, BasicPowerCableComponent> cableType,
                                         ComponentType<ChunkStore, CableEndpoint> endpointType,
                                         ComponentType<ChunkStore, StoresHE> storesType) {
        this.cableType = cableType;
        this.endpointType = endpointType;
        this.storesType = storesType;
        this.query = Query.and(cableType, endpointType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
        BasicPowerCableComponent cable = chunk.getComponent(index, cableType);
        CableEndpoint endpoint = chunk.getComponent(index, endpointType);
        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        if (cable == null || endpoint == null || ref == null) return;

        // rate limit
        float acc = cable.getSecondsAccumulator() + dt;
        // run every tick (~1 / TPS). Use small accumulator to handle lag gracefully.
        if (acc < (1f / HytaleIndustriesPlugin.TPS)) {
            cable.setSecondsAccumulator(acc);
            return;
        }
        cable.setSecondsAccumulator(0f);
        double budget = endpoint.capacityPerTick;
        if (budget <= 0) return;

        World world = store.getExternalData().getWorld();
        Pos pos = worldPos(store, ref);
        if (pos == null) return;

        // gather sources (adjacent StoresHE on Extract faces)
        List<Source> sources = new ArrayList<>();
        for (Vector3i dir : DIRS) {
            if (cable.getConnectionState(dir) != BasicPowerCableComponent.ConnectionState.Extract) continue;
            Pos n = offset(pos, dir);
            StoresHE sh = getStores(world, store, n);
            if (sh != null && sh.current > 0) {
                float loss = getLoss(world, store, n);
                sources.add(new Source(n.ref, sh, loss));
            }
        }
        if (sources.isEmpty()) return;

        // gather sinks reachable via cables (Default faces only)
        List<Sink> sinks = gatherSinks(world, store, pos, cable);
        if (sinks.isEmpty()) return;

        // de-duplicate sinks by ref to prevent multiple faces weighting one sink
        Map<Ref<ChunkStore>, Sink> dedup = new LinkedHashMap<>();
        for (Sink s : sinks) dedup.putIfAbsent(s.ref, s);
        List<Sink> uniqueSinks = new ArrayList<>(dedup.values());

        long totalFree = 0;
        for (Sink s : uniqueSinks) totalFree += s.free();
        if (totalFree <= 0) return;

        long totalAvailable = 0;
        for (Source s : sources) totalAvailable += s.available();
        if (totalAvailable <= 0) return;

        long totalSend = (long) Math.min(budget, totalAvailable);
        if (totalSend <= 0) return;

        // Compute desired shares by free capacity
        long[] desired = new long[uniqueSinks.size()];
        long totalDesired = 0;
        for (int i = 0; i < uniqueSinks.size(); i++) {
            long free = uniqueSinks.get(i).free();
            long share = Math.min(free, Math.round((double) totalSend * free / totalFree));
            desired[i] = share;
            totalDesired += share;
        }
        if (totalDesired <= 0) return;

        double scale = totalSend < totalDesired ? (double) totalSend / totalDesired : 1.0;

        long sentSoFar = 0;
        for (int i = 0; i < uniqueSinks.size(); i++) {
            Sink sink = uniqueSinks.get(i);
            if (sink.free() <= 0) continue;
            long target = (long) Math.floor(desired[i] * scale);
            if (i == uniqueSinks.size() - 1) {
                target = Math.max(target, totalSend - sentSoFar); // give remainder to last sink
            }
            if (target <= 0) continue;

            long delivered = pullFromSources(sources, target);
            if (delivered > 0) {
                sink.store.current = Math.min(sink.store.max, sink.store.current + delivered);
                sink.modified = true;
                sentSoFar += delivered;
            }
            if (sentSoFar >= totalSend) break;
        }

        // persist modified stores
        for (Source s : sources) {
            if (s.modified) buffer.replaceComponent(s.ref, storesType, s.store);
        }
        for (Sink s : uniqueSinks) {
            if (s.modified) buffer.replaceComponent(s.ref, storesType, s.store);
        }
    }

    private List<Sink> gatherSinks(World world, Store<ChunkStore> store, Pos start, BasicPowerCableComponent startCable) {
        List<Sink> out = new ArrayList<>();
        Queue<Pos> q = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        q.add(start);
        visited.add(pack(start.x, start.y, start.z));

        while (!q.isEmpty()) {
            Pos cur = q.poll();
            BasicPowerCableComponent cable = getCable(world, store, cur);

            for (Vector3i dir : DIRS) {
                int nx = cur.x + dir.x;
                int ny = cur.y + dir.y;
                int nz = cur.z + dir.z;
                if (ny < WORLD_MIN_Y || ny >= WORLD_MAX_Y_EXCLUSIVE) continue;
                Pos npos = resolve(world, nx, ny, nz);
                if (npos == null) continue;

                BasicPowerCableComponent nCable = getCable(world, store, npos);
                if (nCable != null) {
                    // traverse cable if both ends allow connection
                    Vector3i opposite = new Vector3i(-dir.x, -dir.y, -dir.z);
                    if (cable != null && cable.isSideConnected(dir) && nCable.isSideConnected(opposite)) {
                        long key = pack(npos.x, npos.y, npos.z);
                        if (visited.add(key)) q.add(npos);
                    }
                    continue;
                }

                // sink: only if our side is Default
                if (cable != null && cable.getConnectionState(dir) != BasicPowerCableComponent.ConnectionState.Default) continue;
                StoresHE sh = getStores(world, store, npos);
                if (sh == null || sh.current >= sh.max) continue;
                out.add(new Sink(npos.ref, sh));
            }
        }
        return out;
    }

    private StoresHE getStores(World world, Store<ChunkStore> store, Pos pos) {
        if (pos == null || pos.ref == null) return null;
        return pos.ref.getStore().getComponent(pos.ref, storesType);
    }

    private CableEndpoint getEndpoint(World world, Store<ChunkStore> store, Pos pos) {
        if (pos == null || pos.ref == null) return null;
        return pos.ref.getStore().getComponent(pos.ref, endpointType);
    }

    private BasicPowerCableComponent getCable(World world, Store<ChunkStore> store, Pos pos) {
        if (pos == null || pos.ref == null) return null;
        return pos.ref.getStore().getComponent(pos.ref, cableType);
    }

    private float getLoss(World world, Store<ChunkStore> store, Pos pos) {
        CableEndpoint ep = getEndpoint(world, store, pos);
        return ep != null ? ep.lossPerMeter : 0f;
    }

    private Pos worldPos(Store<ChunkStore> store, Ref<ChunkStore> ref) {
        var info = store.getComponent(ref, com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo.getComponentType());
        if (info == null || info.getChunkRef() == null || !info.getChunkRef().isValid()) return null;
        BlockChunk bc = store.getComponent(info.getChunkRef(), BlockChunk.getComponentType());
        if (bc == null) return null;
        int x = ChunkUtil.worldCoordFromLocalCoord(bc.getX(), ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(bc.getZ(), ChunkUtil.zFromBlockInColumn(info.getIndex()));
        WorldChunk wc = store.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
        if (wc == null) return null;
        return new Pos(x, y, z, wc.getBlockComponentEntity(x & 31, y, z & 31), store.getExternalData().getWorld());
    }

    private Pos offset(Pos p, Vector3i dir) {
        return resolve(p.world, p.x + dir.x, p.y + dir.y, p.z + dir.z);
    }

    private Pos resolve(World world, int x, int y, int z) {
        int[] origin = resolveFillerOrigin(world, x, y, z);
        WorldChunk chunk = world != null ? world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(origin[0], origin[2])) : null;
        if (chunk == null && world != null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(origin[0], origin[2]));
        if (chunk == null) return null;
        Ref<ChunkStore> ref = chunk.getBlockComponentEntity(origin[0] & 31, origin[1], origin[2] & 31);
        return new Pos(origin[0], origin[1], origin[2], ref, chunk.getWorld());
    }

    private static int[] resolveFillerOrigin(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return new int[]{x, y, z};
        int filler = chunk.getFiller(x & 31, y, z & 31);
        if (filler == 0) return new int[]{x, y, z};
        int dx = FillerBlockUtil.unpackX(filler);
        int dy = FillerBlockUtil.unpackY(filler);
        int dz = FillerBlockUtil.unpackZ(filler);
        return new int[]{x - dx, y - dy, z - dz};
    }

    private static long pack(int x, int y, int z) {
        long lx = (long) x & 0x3FFFFFFL;
        long lz = (long) z & 0x3FFFFFFL;
        long ly = (long) y & 0xFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }

    private static class Pos {
        final int x, y, z;
        final Ref<ChunkStore> ref;
        final World world;
        Pos(int x, int y, int z, Ref<ChunkStore> ref) { this(x, y, z, ref, null); }
        Pos(int x, int y, int z, Ref<ChunkStore> ref, World world) {
            this.x = x; this.y = y; this.z = z; this.ref = ref; this.world = world;
        }
    }

    private static class Source {
        final Ref<ChunkStore> ref;
        final StoresHE store;
        final float loss;
        boolean modified = false;
        Source(Ref<ChunkStore> ref, StoresHE store, float loss) { this.ref = ref; this.store = store; this.loss = loss; }
        long available() { return store.current; }
        void draw(long amt) { store.current -= amt; modified = true; }
    }

    private static class Sink {
        final Ref<ChunkStore> ref;
        final StoresHE store;
        boolean modified = false;
        Sink(Ref<ChunkStore> ref, StoresHE store) { this.ref = ref; this.store = store; }
        long free() { return store.max - store.current; }
    }

    private static long pullFromSources(List<Source> sources, long requested) {
        long remaining = requested;
        for (Source src : sources) {
            if (remaining <= 0) break;
            long avail = src.available();
            if (avail <= 0) continue;
            long send = Math.min(avail, remaining);
            long delivered = (long) Math.max(0, Math.floor(send * (1f - src.loss)));
            if (delivered <= 0) continue;
            src.draw(send);
            remaining -= delivered;
        }
        return requested - remaining;
    }
}
