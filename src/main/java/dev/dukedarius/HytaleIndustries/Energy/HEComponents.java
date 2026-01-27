package dev.dukedarius.HytaleIndustries.Energy;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;


public final class HEComponents {

    private HEComponents() {
    }

    @NullableDecl
    public static StoresHE stores(World world, int x, int y, int z) {
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
        
        var blockState = chunk.getState(x & 31, y, z & 31);
        return (blockState instanceof StoresHE s) ? s : null;
    }

    @NullableDecl
    public static ReceivesHE receives(World world, int x, int y, int z) {
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
        
        var blockState = chunk.getState(x & 31, y, z & 31);
        return (blockState instanceof ReceivesHE r) ? r : null;
    }

    @NullableDecl
    public static TransfersHE transfers(World world, int x, int y, int z) {
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
        
        var blockState = chunk.getState(x & 31, y, z & 31);
        return (blockState instanceof TransfersHE t) ? t : null;
    }
}
