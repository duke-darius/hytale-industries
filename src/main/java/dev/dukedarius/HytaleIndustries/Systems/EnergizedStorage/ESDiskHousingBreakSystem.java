package dev.dukedarius.HytaleIndustries.Systems.EnergizedStorage;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.Utils.DiskHousingDisplayManager;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * RefSystem that catches ALL disk housing removals (player break, support loss, etc.)
 * and drops the inserted disks + cleans up display entities.
 */
public class ESDiskHousingBreakSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ESDiskHousingComponent> housingType;
    private final Query<ChunkStore> query;

    public ESDiskHousingBreakSystem(ComponentType<ChunkStore, ESDiskHousingComponent> housingType) {
        this.housingType = housingType;
        this.query = Query.and(housingType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason addReason,
                              @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> buffer) {
        // no-op on add
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason removeReason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> buffer) {
        ESDiskHousingComponent housing = store.getComponent(ref, housingType);
        if (housing == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Get world position
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

        Vector3i pos = new Vector3i(wx, wy, wz);

        // Save disk contents to their ItemStack metadata before dropping
        housing.saveAllDisksMetadata();

        // Drop all disks as item entities
        Vector3d dropPos = new Vector3d(wx + 0.5, wy + 0.5, wz + 0.5);
        world.execute(() -> {
            try {
                var entityStore = world.getEntityStore().getStore();
                for (short slot = 0; slot < ESDiskHousingComponent.DISK_SLOT_COUNT; slot++) {
                    ItemStack stack = housing.diskSlots.getItemStack(slot);
                    if (stack != null && !ItemStack.isEmpty(stack)) {
                        var holder = ItemComponent.generateItemDrop(
                                entityStore, stack, dropPos, Rotation3f.ZERO, 0f, 0.2f, 0f);
                        if (holder != null) {
                            entityStore.addEntity(holder, AddReason.SPAWN);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });

        // Clean up display entities
        DiskHousingDisplayManager.removeAll(world, pos);
    }
}
