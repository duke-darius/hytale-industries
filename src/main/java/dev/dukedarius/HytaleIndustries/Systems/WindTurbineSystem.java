package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.WindTurbineComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

/**
 * ECS replacement for {@link dev.dukedarius.HytaleIndustries.BlockStates.WindTurbineBlockState}.
 *
 * - On first tick it builds the 4-block-tall filler column.
 * - Every tick it generates HE based on Y-level and stores it in a StoresHE component.
 */
public class WindTurbineSystem extends EntityTickingSystem<ChunkStore> {

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    private final ComponentType<ChunkStore, WindTurbineComponent> turbineType;
    private final ComponentType<ChunkStore, StoresHE> storesType;
    private final Query<ChunkStore> query;

    public WindTurbineSystem(ComponentType<ChunkStore, WindTurbineComponent> turbineType,
                             ComponentType<ChunkStore, StoresHE> storesType) {
        this.turbineType = turbineType;
        this.storesType = storesType;
        this.query = Query.and(turbineType, storesType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        WindTurbineComponent turbine = chunk.getComponent(index, turbineType);
        StoresHE energy = chunk.getComponent(index, storesType);
        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        if (turbine == null || energy == null || ref == null) {
            return;
        }

        BlockStateInfo info = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (info == null || info.getChunkRef() == null || !info.getChunkRef().isValid()) {
            return;
        }

        BlockChunk blockChunk = store.getComponent(info.getChunkRef(), BlockChunk.getComponentType());
        WorldChunk worldChunk = store.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
        if (blockChunk == null || worldChunk == null) {
            return;
        }

        World world = worldChunk.getWorld();
        if (world == null) {
            return;
        }

        double windSpeed = 1.0;
        if (HytaleIndustriesPlugin.INSTANCE != null && HytaleIndustriesPlugin.INSTANCE.getWindManager() != null) {
            try {
                // Advance wind for this world based on dt, then read current speed.
                HytaleIndustriesPlugin.INSTANCE.getWindManager().tickWorld(world, dt);
                windSpeed = HytaleIndustriesPlugin.INSTANCE.getWindManager().getSpeed(world);
            } catch (Throwable ignored) {
                windSpeed = 1.0;
            }
        }

        final int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), ChunkUtil.xFromBlockInColumn(info.getIndex()));
        final int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        final int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), ChunkUtil.zFromBlockInColumn(info.getIndex()));

        if (y < WORLD_MIN_Y || y >= WORLD_MAX_Y_EXCLUSIVE) {
            return;
        }

        // One-time filler setup to make the turbine a 4-block-tall structure.
        if (!turbine.initialized) {
            buffer.run(s -> {
                World w = s.getExternalData().getWorld();
                if (w != null) {
                    setupFillers(w, x, y, z);
                }
            });
            turbine.initialized = true;
            buffer.replaceComponent(ref, turbineType, turbine);
            return;
        }

        // 1. Compute generation rate (HE per second), matching old BlockState logic,
        //    then apply global wind speed modifier.
        double baseGeneration = 10.0; // HE/s at or below Y=120
        double currentGeneration = baseGeneration;
        if (y > 120) {
            currentGeneration += (y - 120) / 10.0; // +1 HE/s per 10 blocks above 120
        }
        if (currentGeneration < 0.0) {
            currentGeneration = 0.0;
        }
        currentGeneration *= windSpeed;
        turbine.lastProductionPerSecond = currentGeneration;

        if (energy.max <= 0) {
            buffer.replaceComponent(ref, turbineType, turbine);
            return;
        }

        // 2. Convert per-second rate to per-tick budget using TPS, keeping fractional remainder.
        double perTick = currentGeneration / HytaleIndustriesPlugin.TPS;
        turbine.energyRemainder += perTick;
        long toAdd = (long) Math.floor(turbine.energyRemainder);
        if (toAdd > 0) {
            turbine.energyRemainder -= toAdd;
            long space = energy.max - energy.current;
            if (space > 0) {
                long added = Math.min(space, toAdd);
                if (added > 0) {
                    energy.current += added;
                    buffer.replaceComponent(ref, storesType, energy);
                }
            }
        }

        buffer.replaceComponent(ref, turbineType, turbine);
    }

    private static void setupFillers(@Nonnull World world, int x, int y, int z) {
        // Wind turbine is 4 blocks tall.
        // Origin is at the bottom (y).
        // Fillers at y+1, y+2, y+3.
        for (int dy = 1; dy <= 3; dy++) {
            int targetY = y + dy;
            if (targetY >= WORLD_MAX_Y_EXCLUSIVE) continue;

            WorldChunk targetChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (targetChunk == null) {
                targetChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            }
            if (targetChunk == null) continue;

            int filler = FillerBlockUtil.pack(0, dy, 0);

            int lx = x & 31;
            int lz = z & 31;
            int blockId = targetChunk.getBlock(lx, y, lz);
            var blockType = targetChunk.getBlockType(lx, y, lz);
            if (blockType == null) continue;

            targetChunk.setBlock(lx, targetY, lz, blockId, blockType, 0, filler, 0);
        }
    }
}
