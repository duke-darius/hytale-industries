package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class BlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    public BlockBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
            int i, 
            @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk, 
            @NonNullDecl Store<EntityStore> store, 
            @NonNullDecl CommandBuffer<EntityStore> commandBuffer, 
            @NonNullDecl BreakBlockEvent event) {
        // Resolve the block state being broken.
        var world = store.getExternalData().getWorld();
        var pos = event.getTargetBlock();


        var cableComponentType = HytaleIndustriesPlugin.INSTANCE.getBasicPowerCableComponentType();
        var updateComponentType = HytaleIndustriesPlugin.INSTANCE.getUpdatePowerCableComponentType();

        var pipeComponentType = HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
        var updatePipeComponentType = HytaleIndustriesPlugin.INSTANCE.getUpdatePipeComponentType();

        // Check all 6 sides for neighbor cables or pipes that might need updating
        for (var dir : Vector3i.BLOCK_SIDES) {
            int nx = pos.x + dir.getX();
            int ny = pos.y + dir.getY();
            int nz = pos.z + dir.getZ();

            if (ny < 0 || ny >= 320) continue;

            var nChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (nChunk == null) {
                nChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
            }

            if (nChunk != null) {
                var nEntity = nChunk.getBlockComponentEntity(nx & 31, ny, nz & 31);
                if (nEntity != null) {
                    var nEntityChunkStore = (com.hypixel.hytale.component.Ref<ChunkStore>) (Object) nEntity;
                    var neighborChunkStore = nEntityChunkStore.getStore();

                    // Check for Power Cable
                    if (nEntityChunkStore.getStore().getComponent(nEntityChunkStore, cableComponentType) != null) {
                        HytaleIndustriesPlugin.LOGGER.atFine().log("Found neighbor cable at (%d,%d,%d), marking for update", nx, ny, nz);
                        neighborChunkStore.ensureComponent(nEntityChunkStore, updateComponentType);
                    }


                    // Check for Item Pipe
                    if (nEntityChunkStore.getStore().getComponent(nEntityChunkStore, pipeComponentType) != null) {
                        HytaleIndustriesPlugin.LOGGER.atFine().log("Found neighbor pipe at (%d,%d,%d), marking for update", nx, ny, nz);
                        neighborChunkStore.ensureComponent(nEntityChunkStore, updatePipeComponentType);
                    }
                }
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

}
