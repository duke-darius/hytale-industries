package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import dev.dukedarius.HytaleIndustries.Energy.HEComponents;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

/**
 * When an energy-capable block (generator/consumer/storage) is placed,
 * mark adjacent power cables for update so they auto-connect.
 */
public class EnergyNeighborUpdateOnPlaceSystem extends RefSystem<ChunkStore> {

    private final Query<ChunkStore> query = Query.and(BlockStateInfo.getComponentType());

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> refChunkStore,
                              @Nonnull AddReason addReason,
                              @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (addReason != AddReason.SPAWN) return;

        var info = store.getComponent(refChunkStore, BlockStateInfo.getComponentType());
        if (info == null) return;

        var chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;

        var blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        var world = store.getExternalData().getWorld();

        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(),
                ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(),
                ChunkUtil.zFromBlockInColumn(info.getIndex()));

        // Only proceed if this block provides or receives energy
        boolean isEnergy = HEComponents.transfers(world, x, y, z) != null
                || HEComponents.receives(world, x, y, z) != null
                || HEComponents.stores(world, x, y, z) != null;
        if (!isEnergy) return;

        var cableComponentType = HytaleIndustriesPlugin.INSTANCE.getBasicPowerCableComponentType();
        var updateComponentType = HytaleIndustriesPlugin.INSTANCE.getUpdatePowerCableComponentType();

        for (var dir : Vector3i.BLOCK_SIDES) {
            int nx = x + dir.getX();
            int ny = y + dir.getY();
            int nz = z + dir.getZ();
            if (ny < 0 || ny >= 320) continue;

            WorldChunk nChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (nChunk == null) {
                nChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
            }
            if (nChunk == null) continue;

            var nEntity = nChunk.getBlockComponentEntity(nx & 31, ny, nz & 31);
            if (nEntity == null) continue;

            var neighborStore = nEntity.getStore();

            if (neighborStore.getComponent(nEntity, cableComponentType) != null) {
                commandBuffer.ensureComponent(nEntity, updateComponentType);
            }
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason removeReason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // no-op
    }
}
