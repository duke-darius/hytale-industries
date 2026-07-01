package dev.dukedarius.HytaleIndustries.Systems.EnergizedStorage;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESControllerComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESGridComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESNetworkMemberComponent;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ESNetworkSystem extends EntityTickingSystem<ChunkStore> {

    private static final Vector3i[] DIRECTIONS = {
            new Vector3i(0, 0, -1), new Vector3i(0, 0, 1),
            new Vector3i(-1, 0, 0), new Vector3i(1, 0, 0),
            new Vector3i(0, 1, 0),  new Vector3i(0, -1, 0)
    };

    private final ComponentType<ChunkStore, ESControllerComponent> controllerType;
    private final ComponentType<ChunkStore, ESNetworkMemberComponent> networkMemberType;
    private final ComponentType<ChunkStore, ESDiskHousingComponent> diskHousingType;
    private final ComponentType<ChunkStore, ESGridComponent> gridType;
    private final ComponentType<ChunkStore, ConsumesHE> consumesHeType;
    private final ComponentType<ChunkStore, StoresHE> storesHeType;
    private final Query<ChunkStore> query;

    public ESNetworkSystem(ComponentType<ChunkStore, ESControllerComponent> controllerType,
                           ComponentType<ChunkStore, ESNetworkMemberComponent> networkMemberType,
                           ComponentType<ChunkStore, ESDiskHousingComponent> diskHousingType,
                           ComponentType<ChunkStore, ESGridComponent> gridType,
                           ComponentType<ChunkStore, ConsumesHE> consumesHeType,
                           ComponentType<ChunkStore, StoresHE> storesHeType) {
        this.controllerType = controllerType;
        this.networkMemberType = networkMemberType;
        this.diskHousingType = diskHousingType;
        this.gridType = gridType;
        this.consumesHeType = consumesHeType;
        this.storesHeType = storesHeType;
        this.query = Query.and(controllerType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<ChunkStore> chunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> buffer) {
        ESControllerComponent controller = chunk.getComponent(index, controllerType);
        if (controller == null) return;

        Ref<ChunkStore> ref = chunk.getReferenceTo(index);

        // 1) Power check — HEConsumptionSystem handles the actual drain,
        //    we just check if there's energy remaining
        StoresHE energy = store.getComponent(ref, storesHeType);
        boolean powered = energy != null && (energy.creative || energy.current > 0);

        if (!powered) {
            if (controller.networkOnline) {
                controller.networkOnline = false;
                controller.itemIndex.clear();
                controller.totalStored = 0;
                controller.maxCapacity = 0;
                buffer.replaceComponent(ref, controllerType, controller);
            }
            return;
        }

        // 2) Get world position of this controller
        World world = store.getExternalData().getWorld();
        if (world == null) return;
        var info = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (info == null) return;
        var chunkRef = info.getChunkRef();
        var blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;
        int wx = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int wy = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int wz = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(info.getIndex()));

        // 3) BFS to find all connected devices (and check for duplicate controllers)
        List<ESDiskHousingComponent> housings = new ArrayList<>();
        boolean duplicateController = false;
        int gridCount = 0;
        int totalDisks = 0;
        long totalPowerUsage = 0;

        // Count controller's own power usage
        ConsumesHE controllerCons = store.getComponent(ref, consumesHeType);
        if (controllerCons != null) totalPowerUsage += controllerCons.heConsumption;
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long startKey = packPos(wx, wy, wz);
        visited.add(startKey);
        queue.enqueue(startKey);

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int cx = unpackX(current), cy = unpackY(current), cz = unpackZ(current);

            for (Vector3i dir : DIRECTIONS) {
                int nx = cx + dir.x, ny = cy + dir.y, nz = cz + dir.z;
                if (ny < 0 || ny >= 320) continue;
                long nkey = packPos(nx, ny, nz);
                if (!visited.add(nkey)) continue;

                WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) wc = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) continue;

                var nEntity = wc.getBlockComponentEntity(nx & 31, ny, nz & 31);
                if (nEntity == null) continue;

                var nStore = nEntity.getStore();
                if (nStore.getComponent(nEntity, networkMemberType) == null) continue;

                queue.enqueue(nkey);

                // Check for disk housing
                ESDiskHousingComponent housing = nStore.getComponent(nEntity, diskHousingType);
                if (housing != null) {
                    housings.add(housing);
                    totalDisks += housing.getActiveDiskCount();
                }

                // Check for grid
                if (nStore.getComponent(nEntity, gridType) != null) gridCount++;

                // Sum power usage from this device
                ConsumesHE nCons = nStore.getComponent(nEntity, consumesHeType);
                if (nCons != null) totalPowerUsage += nCons.heConsumption;

                // Check for duplicate controller
                ESControllerComponent otherController = nStore.getComponent(nEntity, controllerType);
                if (otherController != null && !(nx == wx && ny == wy && nz == wz)) {
                    duplicateController = true;
                }
            }
        }

        // 4) Duplicate controller = conflict, go offline
        if (duplicateController) {
            controller.networkOnline = false;
            controller.itemIndex.clear();
            controller.totalStored = 0;
            controller.maxCapacity = 0;
            buffer.replaceComponent(ref, controllerType, controller);
            return;
        }

        // 5) Aggregate from all housings
        List<ItemStack> newIndex = new ArrayList<>();
        long newTotalStored = 0;
        long newMaxCapacity = 0;
        for (ESDiskHousingComponent h : housings) {
            newMaxCapacity += h.getTotalCapacity();
            newTotalStored += h.getTotalStored();
            for (ItemStack stack : h.aggregateItems()) {
                boolean merged = false;
                for (int ai = 0; ai < newIndex.size(); ai++) {
                    ItemStack existing = newIndex.get(ai);
                    if (existing.isStackableWith(stack)) {
                        newIndex.set(ai, existing.withQuantity(existing.getQuantity() + stack.getQuantity()));
                        merged = true;
                        break;
                    }
                }
                if (!merged) newIndex.add(stack);
            }
        }

        controller.itemIndex = newIndex;
        controller.totalStored = newTotalStored;
        controller.maxCapacity = newMaxCapacity;
        controller.networkOnline = true;
        controller.gridCount = gridCount;
        controller.diskHousingCount = housings.size();
        controller.totalDiskCount = totalDisks;
        controller.energyStored = energy != null ? energy.current : 0;
        controller.energyMax = energy != null ? energy.max : 0;
        controller.totalPowerUsage = totalPowerUsage;
        buffer.replaceComponent(ref, controllerType, controller);
    }

    /**
     * BFS from a position to find the nearest ESControllerComponent.
     * Used by the Grid interaction to locate the network's controller.
     */
    @Nullable
    public static ESControllerComponent findController(World world, Store<ChunkStore> store,
                                                        int startX, int startY, int startZ,
                                                        ComponentType<ChunkStore, ESNetworkMemberComponent> memberType,
                                                        ComponentType<ChunkStore, ESControllerComponent> ctrlType) {
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long startKey = packPos(startX, startY, startZ);
        visited.add(startKey);
        queue.enqueue(startKey);

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int cx = unpackX(current), cy = unpackY(current), cz = unpackZ(current);

            // Check current position for controller
            WorldChunk curChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cx, cz));
            if (curChunk == null) curChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(cx, cz));
            if (curChunk != null) {
                var curEntity = curChunk.getBlockComponentEntity(cx & 31, cy, cz & 31);
                if (curEntity != null) {
                    ESControllerComponent ctrl = curEntity.getStore().getComponent(curEntity, ctrlType);
                    if (ctrl != null) return ctrl;
                }
            }

            for (Vector3i dir : DIRECTIONS) {
                int nx = cx + dir.x, ny = cy + dir.y, nz = cz + dir.z;
                if (ny < 0 || ny >= 320) continue;
                long nkey = packPos(nx, ny, nz);
                if (!visited.add(nkey)) continue;

                WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) wc = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) continue;

                var nEntity = wc.getBlockComponentEntity(nx & 31, ny, nz & 31);
                if (nEntity == null) continue;
                var nStore = nEntity.getStore();
                if (nStore.getComponent(nEntity, memberType) == null) continue;

                queue.enqueue(nkey);
            }
        }
        return null;
    }

    // Also need to find Disk Housings for insert/extract operations from the Grid
    public static List<ESDiskHousingComponent> findDiskHousings(
            World world, Store<ChunkStore> store,
            int startX, int startY, int startZ,
            ComponentType<ChunkStore, ESNetworkMemberComponent> memberType,
            ComponentType<ChunkStore, ESDiskHousingComponent> housingType) {
        List<ESDiskHousingComponent> result = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long startKey = packPos(startX, startY, startZ);
        visited.add(startKey);
        queue.enqueue(startKey);

        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            int cx = unpackX(current), cy = unpackY(current), cz = unpackZ(current);

            // Check current position for disk housing
            WorldChunk curChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cx, cz));
            if (curChunk == null) curChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(cx, cz));
            if (curChunk != null) {
                var curEntity = curChunk.getBlockComponentEntity(cx & 31, cy, cz & 31);
                if (curEntity != null) {
                    ESDiskHousingComponent housing = curEntity.getStore().getComponent(curEntity, housingType);
                    if (housing != null) result.add(housing);
                }
            }

            for (Vector3i dir : DIRECTIONS) {
                int nx = cx + dir.x, ny = cy + dir.y, nz = cz + dir.z;
                if (ny < 0 || ny >= 320) continue;
                long nkey = packPos(nx, ny, nz);
                if (!visited.add(nkey)) continue;

                WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) wc = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (wc == null) continue;

                var nEntity = wc.getBlockComponentEntity(nx & 31, ny, nz & 31);
                if (nEntity == null) continue;
                var nStore = nEntity.getStore();
                if (nStore.getComponent(nEntity, memberType) == null) continue;

                queue.enqueue(nkey);
            }
        }
        return result;
    }

    private static long packPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }
    private static int unpackX(long packed) {
        int x = (int)(packed >> 38); return x >= 0x2000000 ? x - 0x4000000 : x;
    }
    private static int unpackY(long packed) { return (int)(packed >> 26) & 0xFFF; }
    private static int unpackZ(long packed) {
        int z = (int)(packed & 0x3FFFFFFL); return z >= 0x2000000 ? z - 0x4000000 : z;
    }
}
