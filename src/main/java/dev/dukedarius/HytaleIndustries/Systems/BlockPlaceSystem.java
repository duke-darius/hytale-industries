package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class BlockPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    public BlockPlaceSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
            int i,
            @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
            @NonNullDecl Store<EntityStore> store,
            @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
            @NonNullDecl PlaceBlockEvent event) {

        var world = store.getExternalData().getWorld();
        var pos = event.getTargetBlock();

        var cableComponentType = HytaleIndustriesPlugin.INSTANCE.getBasicPowerCableComponentType();
        var updateCableType = HytaleIndustriesPlugin.INSTANCE.getUpdatePowerCableComponentType();

        var pipeComponentType = HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
        var updatePipeType = HytaleIndustriesPlugin.INSTANCE.getUpdatePipeComponentType();

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
                    var neighborStore = nEntity.getStore();

                    if (neighborStore.getComponent(nEntity, cableComponentType) != null) {
                        neighborStore.ensureComponent(nEntity, updateCableType);
                    }

                    if (neighborStore.getComponent(nEntity, pipeComponentType) != null) {
                        neighborStore.ensureComponent(nEntity, updatePipeType);
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
