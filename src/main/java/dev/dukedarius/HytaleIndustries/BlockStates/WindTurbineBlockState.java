package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Energy.TransfersHE;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wind Turbine generates HE from wind.
 * Base 10 HE/s.
 * Above Y=120, increases by 1 HE/s for every 10 blocks (e.g. 11 HE/s at Y=130).
 * 4 blocks tall.
 */
public class WindTurbineBlockState extends BlockState implements TickableBlockState, StoresHE, TransfersHE {

    public static final String STATE_ID = "windTurbine";

    private double heStored = 0.0;
    public static final double MAX_HE = 5000.0;
    private static final double TRANSFER_HE_PER_SECOND = 200.0;

    private boolean initialized = false;
    private double lastProduction = 0.0;

    public static final Codec<WindTurbineBlockState> CODEC = BuilderCodec.builder(
            WindTurbineBlockState.class,
            WindTurbineBlockState::new,
            BlockState.BASE_CODEC
    ).append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (s, v) -> s.heStored = v, s -> s.heStored)
     .add()
     .append(new KeyedCodec<>("LastProduction", Codec.DOUBLE), (s, v) -> s.lastProduction = v, s -> s.lastProduction)
     .add()
     .build();

    @Override
    public double getHeStored() {
        return heStored;
    }

    public double getCurrentProduction() {
        return lastProduction;
    }

    @Override
    public void setHeStored(double he) {
        this.heStored = Math.min(MAX_HE, Math.max(0.0, he));
    }

    @Override
    public double getHeCapacity() {
        return MAX_HE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (dt <= 0) return;

        WorldChunk chunk = getChunk();
        if (chunk == null) return;
        World world = chunk.getWorld();
        var pos = getBlockPosition();
        if (pos == null) return;

        if (!initialized) {
            setupFillers(world, pos.x, pos.y, pos.z);
            initialized = true;
            persistSelf();
            return;
        }

        // 1. Generation
        double baseGeneration = 10.0;
        double currentGeneration = baseGeneration;
        if (pos.y > 120) {
            currentGeneration += (pos.y - 120) / 10.0;
        }
        lastProduction = currentGeneration;

        setHeStored(heStored + currentGeneration * dt);

        boolean dirty = tryTransferToNeighbors(dt, world, pos.x, pos.y, pos.z);

        if (dirty || currentGeneration > 0) {
            persistSelf();
        }
    }

    private void setupFillers(World world, int x, int y, int z) {
        // Wind turbine is 4 blocks tall.
        // Origin is at the bottom (y).
        // Fillers at y+1, y+2, y+3.
        for (int dy = 1; dy <= 3; dy++) {
            int targetY = y + dy;
            if (targetY >= 320) continue;
            
            // Check if we can place a filler there (only if it's air or replaceable)
            // For simplicity in this mod, we just force it if it's not the origin.
            WorldChunk targetChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (targetChunk != null) {
                // In Hytale, setBlock can take filler info.
                // filler = (dx << 8) | (dy << 4) | dz (relative to origin)
                // Actually FillerBlockUtil.pack(dx, dy, dz)
                int filler = com.hypixel.hytale.server.core.util.FillerBlockUtil.pack(0, dy, 0);
                
                // We use the same block type but with filler metadata.
                var blockType = targetChunk.getBlockType(x & 31, y, z & 31);
                if (blockType != null) {
                    targetChunk.setBlock(x & 31, targetY, z & 31, targetChunk.getBlock(x & 31, y, z & 31), blockType, 0, filler, 0);
                }
            }
        }
    }

    private boolean tryTransferToNeighbors(float dt, World world, int x, int y, int z) {
        if (heStored <= 0.0) return false;

        double budget = Math.min(heStored, TRANSFER_HE_PER_SECOND * dt);
        if (budget <= 0.0) return false;

        double before = heStored;

        // Check neighbors of the 4-block tall structure.
        // Machines in this mod typically transfer from the origin block or specific ports.
        // We will check all neighbors of ALL 4 blocks to make it easier to connect cables.
        
        for (int dy = 0; dy <= 3; dy++) {
            int cy = y + dy;
            if (cy < 0 || cy >= 320) continue;

            int[][] dirs = new int[][]{
                    {1, 0, 0}, {-1, 0, 0},
                    {0, 1, 0}, {0, -1, 0},
                    {0, 0, 1}, {0, 0, -1}
            };

            for (int[] d : dirs) {
                if (budget <= 0.0) break;

                int nx = x + d[0];
                int ny = cy + d[1];
                int nz = z + d[2];

                if (ny < 0 || ny >= 320) continue;
                
                // Don't transfer to itself (other parts of the turbine)
                if (nx == x && nz == z && ny >= y && ny <= y + 3) continue;

                BlockState neighborState = world.getState(nx, ny, nz, true);
                if (!(neighborState instanceof ReceivesHE)) continue;

                WorldChunk nChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (nChunk == null) continue;

                var stateRef = nChunk.getBlockComponentEntity(nx & 31, ny, nz & 31);
                if (stateRef == null) continue;

                @SuppressWarnings({"rawtypes", "unchecked"})
                ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) neighborState.getClass());
                if (type == null) continue;

                Component<ChunkStore> stateObj = stateRef.getStore().getComponent(stateRef, type);
                if (!(stateObj instanceof ReceivesHE receiver)) continue;

                double accepted = receiver.receiveHe(budget);
                if (accepted <= 0.0) continue;

                stateRef.getStore().replaceComponent(stateRef, type, stateObj);
                nChunk.markNeedsSaving();

                heStored -= accepted;
                budget -= accepted;
            }
        }

        return heStored != before;
    }


    private void persistSelf() {
        WorldChunk chunk = this.getChunk();
        var pos = this.getBlockPosition();
        if (chunk == null || pos == null) return;

        var stateRef = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (stateRef == null) return;

        ComponentType<ChunkStore, WindTurbineBlockState> type = BlockStateModule.get().getComponentType(WindTurbineBlockState.class);
        if (type == null) return;

        stateRef.getStore().replaceComponent(stateRef, type, this);
        chunk.markNeedsSaving();
    }
}
