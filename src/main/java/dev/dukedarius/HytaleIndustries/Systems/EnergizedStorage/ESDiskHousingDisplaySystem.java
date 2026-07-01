package dev.dukedarius.HytaleIndustries.Systems.EnergizedStorage;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.Utils.DiskHousingDisplayManager;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * Ticks on each ESDiskHousingComponent and triggers display updates
 * when disk slots change (disk inserted or removed).
 */
public class ESDiskHousingDisplaySystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ESDiskHousingComponent> housingType;
    private final Query<ChunkStore> query;

    public ESDiskHousingDisplaySystem(ComponentType<ChunkStore, ESDiskHousingComponent> housingType) {
        this.housingType = housingType;
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

        // Build a bitmask of which slots have active disks
        int currentMask = 0;
        for (int i = 0; i < ESDiskHousingComponent.DISK_SLOT_COUNT; i++) {
            if (housing.isDiskActive(i)) currentMask |= (1 << i);
        }

        // Compare with last known mask (stored in a transient field)
        if (currentMask == housing.lastDisplayMask) return;
        housing.lastDisplayMask = currentMask;

        // Get world position
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

        int yawIndex = 0;
        WorldChunk wc = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
        if (wc != null) {
            yawIndex = wc.getRotationIndex(wx & 31, wy, wz & 31) & 3;
        }

        DiskHousingDisplayManager.updateAllSlots(world, new Vector3i(wx, wy, wz), housing, yawIndex);
    }
}
