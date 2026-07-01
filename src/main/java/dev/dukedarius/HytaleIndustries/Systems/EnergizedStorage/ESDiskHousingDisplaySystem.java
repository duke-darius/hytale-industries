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
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESControllerComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESNetworkMemberComponent;
import dev.dukedarius.HytaleIndustries.Utils.DiskHousingDisplayManager;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public class ESDiskHousingDisplaySystem extends EntityTickingSystem<ChunkStore> {

    private static final int NETWORK_CHECK_INTERVAL = 8; // ticks between network state checks (~4x/sec at 30 TPS)

    private final ComponentType<ChunkStore, ESDiskHousingComponent> housingType;
    private final ComponentType<ChunkStore, ESNetworkMemberComponent> networkMemberType;
    private final ComponentType<ChunkStore, ESControllerComponent> controllerType;
    private final Query<ChunkStore> query;

    public ESDiskHousingDisplaySystem(ComponentType<ChunkStore, ESDiskHousingComponent> housingType,
                                       ComponentType<ChunkStore, ESNetworkMemberComponent> networkMemberType,
                                       ComponentType<ChunkStore, ESControllerComponent> controllerType) {
        this.housingType = housingType;
        this.networkMemberType = networkMemberType;
        this.controllerType = controllerType;
        this.query = Query.and(housingType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<ChunkStore> chunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> buffer) {
        ESDiskHousingComponent housing = chunk.getComponent(index, housingType);
        if (housing == null) return;

        // Detect disk insertions/removals and sync metadata
        housing.syncDiskSlotTransitions();

        // Throttle network state checks — BFS is expensive, only do it periodically
        housing.networkCheckCounter++;
        boolean doNetworkCheck = housing.networkCheckCounter >= NETWORK_CHECK_INTERVAL;
        if (doNetworkCheck) housing.networkCheckCounter = 0;

        // Build disk state fingerprint
        int currentMask = 0;
        int fillState = 0;
        for (int i = 0; i < ESDiskHousingComponent.DISK_SLOT_COUNT; i++) {
            if (housing.isDiskActive(i)) {
                currentMask |= (1 << i);
                long used = housing.getDiskUsed(i);
                if (used >= housing.getDiskCapacity(i)) {
                    fillState |= (2 << (i * 2));
                } else if (used > 0) {
                    fillState |= (1 << (i * 2));
                }
            }
        }

        // Get world + position (needed for both network check and display update)
        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
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

        // Check network state (periodically or on first tick)
        if (doNetworkCheck || housing.lastNetworkOnline == -1) {
            ESControllerComponent ctrl = ESNetworkSystem.findController(
                    world, store, wx, wy, wz, networkMemberType, controllerType);
            housing.lastNetworkOnline = (ctrl != null && ctrl.networkOnline) ? 1 : 0;
        }

        // Combine disk state + network state into fingerprint
        int networkBit = housing.lastNetworkOnline == 1 ? 1 : 0;
        int combinedState = currentMask | (fillState << 8) | (networkBit << 30);

        if (combinedState == housing.lastDisplayMask) return;
        housing.lastDisplayMask = combinedState;

        int yawIndex = 0;
        WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (wc != null) {
            yawIndex = wc.getRotationIndex(wx & 31, wy, wz & 31) & 3;
        }

        DiskHousingDisplayManager.updateAllSlots(world, new Vector3i(wx, wy, wz),
                housing, yawIndex, networkBit == 1);
    }
}
