package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.ChunkLoading.ChunkLoaderComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class ChunkLoaderSystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ChunkLoaderComponent> loaderType;
    private final Query<ChunkStore> query;

    public ChunkLoaderSystem(ComponentType<ChunkStore, ChunkLoaderComponent> loaderType) {
        this.loaderType = loaderType;
        this.query = Query.and(loaderType, BlockStateInfo.getComponentType(), BlockChunk.getComponentType());
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }


    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        apply(ref, store, buffer, false);
    }

    private void apply(Ref<ChunkStore> ref,
                       Store<ChunkStore> store,
                       CommandBuffer<ChunkStore> buffer,
                       boolean forceRegister) {
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

        comp.ensureKeepLoaded(wc);

        long now = System.nanoTime();
        if (forceRegister || now - comp.lastRegisterNanos > 5_000_000_000L) { // 5s
            var world = wc.getWorld();
            if (world != null && HytaleIndustriesPlugin.INSTANCE != null && HytaleIndustriesPlugin.INSTANCE.getChunkLoaderManager() != null) {
                HytaleIndustriesPlugin.INSTANCE.getChunkLoaderManager()
                        .registerLoader(world.getName(), x, y, z);
            }
            comp.lastRegisterNanos = now;
            buffer.replaceComponent(ref, loaderType, comp);
        }
    }
}
