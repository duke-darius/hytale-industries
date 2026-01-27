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
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

/**
 * When an inventory block is placed, mark adjacent item pipes for update so they auto-connect.
 */
public class InventoryNeighborUpdateOnPlaceSystem extends RefSystem<ChunkStore> {

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

        // Only continue if this block is an inventory container
        if (!isInventory(world, x, y, z)) return;

        var pipeType = HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
        var updatePipeType = HytaleIndustriesPlugin.INSTANCE.getUpdatePipeComponentType();

        for (var dir : Vector3i.BLOCK_SIDES) {
            int nx = x + dir.getX();
            int ny = y + dir.getY();
            int nz = z + dir.getZ();
            if (ny < 0 || ny >= 320) continue;

            WorldChunk nChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (nChunk == null) nChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (nChunk == null) continue;

            var nEntity = nChunk.getBlockComponentEntity(nx & 31, ny, nz & 31);
            if (nEntity == null) continue;

            var neighborStore = nEntity.getStore();
            if (neighborStore.getComponent(nEntity, pipeType) != null) {
                neighborStore.ensureComponent(nEntity, updatePipeType);
            }
        }
    }

    private static boolean isInventory(@Nonnull com.hypixel.hytale.server.core.universe.world.World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return false;
        var state = chunk.getState(x & 31, y, z & 31);
        return state instanceof ItemContainerBlockState;
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason removeReason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // no-op
    }
}
