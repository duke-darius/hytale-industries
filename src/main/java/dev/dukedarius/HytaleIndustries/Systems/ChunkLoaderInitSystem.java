package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.ChunkLoading.ChunkLoaderComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

/**
 * Ensures chunk loaders are registered and their chunk kept loaded immediately on spawn/load.
 */
public class ChunkLoaderInitSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ChunkLoaderComponent> loaderType;
    private final Query<ChunkStore> query;

    public ChunkLoaderInitSystem(ComponentType<ChunkStore, ChunkLoaderComponent> loaderType) {
        this.loaderType = loaderType;
        this.query = Query.and(loaderType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref,
                              @Nonnull AddReason addReason,
                              @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> buffer) {
        if (addReason != AddReason.SPAWN && addReason != AddReason.LOAD) return;

        ChunkLoaderComponent comp = store.getComponent(ref, loaderType);
        if (comp == null) return;

        var info = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (info == null) return;
        var chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;
        WorldChunk wc = store.getComponent(chunkRef, WorldChunk.getComponentType());
        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (wc == null || blockChunk == null) return;

        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), ChunkUtil.zFromBlockInColumn(info.getIndex()));
        buffer.run(_store -> {

            if (wc != null) {
                wc.addKeepLoaded();
                wc.setFlag(com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag.TICKING, true);
                wc.resetKeepAlive();
            }
            if (HytaleIndustriesPlugin.INSTANCE != null &&
                    HytaleIndustriesPlugin.INSTANCE.getChunkLoaderManager() != null &&
                    wc != null && wc.getWorld() != null) {
                HytaleIndustriesPlugin.INSTANCE.getChunkLoaderManager()
                        .registerLoader(wc.getWorld().getName(), x, y, z);
            }
        });
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull com.hypixel.hytale.component.RemoveReason removeReason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // no-op
    }
}
