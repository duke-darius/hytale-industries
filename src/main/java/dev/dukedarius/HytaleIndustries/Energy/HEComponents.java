package dev.dukedarius.HytaleIndustries.Energy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.ProducesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;

@Deprecated(forRemoval = true)
public final class HEComponents {

    @NullableDecl
    public static ConsumesHE receives(World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) {
            return null;
        }
        
        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return null;
        var store = entity.getStore();
        return store.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getConsumesHeType());
    }

    @NullableDecl
    public static ProducesHE transfers(World world, int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return null;
        }
        
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) {
            return null;
        }
        
        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return null;
        var store = entity.getStore();
        return store.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getProducesHeType());
    }
}
