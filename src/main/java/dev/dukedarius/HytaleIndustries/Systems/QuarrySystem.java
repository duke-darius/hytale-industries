package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Quarry.QuarryComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class QuarrySystem extends EntityTickingSystem<ChunkStore> {

    private static final double HE_CONSUMPTION_PER_TICK = 200.0;  // 200 HE/t
    private static final double HE_PER_BLOCK = 600.0;  // 600 HE to mine solid
    private static final double HE_PER_AIR = 60.0;  // 60 HE to skip air

    private final ComponentType<ChunkStore, QuarryComponent> quarryType;
    private final ComponentType<ChunkStore, StoresHE> storesHeType;
    private final Query<ChunkStore> query;

    public QuarrySystem(ComponentType<ChunkStore, QuarryComponent> quarryType,
                        ComponentType<ChunkStore, StoresHE> storesHeType,
                        ComponentType<ChunkStore, ConsumesHE> consumesHeType) {
        this.quarryType = quarryType;
        this.storesHeType = storesHeType;
        this.query = Query.and(quarryType, storesHeType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        QuarryComponent quarry = chunk.getComponent(index, quarryType);
        StoresHE energy = chunk.getComponent(index, storesHeType);

        if (quarry == null || energy == null) return;


        if (quarry.currentStatus != QuarryComponent.QuarryStatus.ACTIVE) {
//
//            if (quarry.showArea) {
//                World world = store.getExternalData().getWorld();
//                if (world != null) {
//                    // Work area is [startX, endX) × [startZ, endZ) in world coords.
//                    int minX = Math.min(quarry.startX, quarry.endX);
//                    int maxX = Math.max(quarry.startX, quarry.endX);
//                    int minZ = Math.min(quarry.startZ, quarry.endZ);
//                    int maxZ = Math.max(quarry.startZ, quarry.endZ);
//
//                    // Vertical extent: from y=0 up to yStart (inclusive).
//                    int yStart = quarry.getYStart();
//                    int minY = 0;
//                    int maxY = yStart + 1; // +1 so the top face is at yStart
//
//                    // Center of the box in world space.
//                    double centerX = (minX + maxX) / 2.0;
//                    double centerY = (minY + maxY) / 2.0;
//                    double centerZ = (minZ + maxZ) / 2.0;
//
//                    // Half-extents (size / 2) along each axis.
//                    double halfX = (maxX - minX) / 2.0;
//                    double halfY = (maxY - minY) / 2.0;
//                    double halfZ = (maxZ - minZ) / 2.0;
//
//                    HytaleIndustriesPlugin.LOGGER.atInfo().log(
//                            "[Quarry] showArea bounds X=[%d,%d) Y=[%d,%d) Z=[%d,%d) center=(%.2f,%.2f,%.2f) half=(%.2f,%.2f,%.2f)",
//                            minX, maxX, minY, maxY, minZ, maxZ,
//                            centerX, centerY, centerZ,
//                            halfX, halfY, halfZ
//                    );
//
//                    Matrix4d matrix = new Matrix4d();
//                    matrix.identity();
//                    matrix.translate(new Vector3d(centerX, centerY, centerZ));
//                    matrix.scale(halfX, halfY, halfZ);
//
//                    DebugUtils.add(
//                            world,
//                            DebugShape.Cube,
//                            matrix,
//                            new Vector3f(0f, 0f, 1f),
//                            1f,
//                            true
//                    );
//
//                }
//            }
            return;
        }


        if (!quarry.hasScanBounds() || !quarry.hasCurrentPos()) {
            HytaleIndustriesPlugin.LOGGER.atInfo().log(
                    "[Quarry] Missing bounds or current pos; hasScanBounds=%s hasCurrentPos=%s. Stopping.",
                    quarry.hasScanBounds(), quarry.hasCurrentPos()
            );
            quarry.currentStatus = QuarryComponent.QuarryStatus.IDLE;
            quarry.clearMiningState(true);
            buffer.replaceComponent(chunk.getReferenceTo(index), quarryType, quarry);
            return;
        }

        var info = store.getComponent(chunk.getReferenceTo(index), BlockStateInfo.getComponentType());
        if (info == null) return;
        var chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;
        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        WorldChunk selfChunk = store.getComponent(chunkRef, WorldChunk.getComponentType());
        if (blockChunk == null || selfChunk == null) return;

        World world = selfChunk.getWorld();
        if (world == null) return;

        int quarryX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int quarryY = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int quarryZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), ChunkUtil.zFromBlockInColumn(info.getIndex()));

        // Keep quarry chunk loaded/ticking
        buffer.run(consumer -> {
            var c = consumer.getComponent(chunkRef, WorldChunk.getComponentType());
            if( c == null) return;

            c.addKeepLoaded();
            c.setFlag(ChunkFlag.TICKING, true);
            c.resetKeepAlive();
        });


        // Keep current mining chunk loaded/ticking
        if (quarry.hasCurrentPos()) {
            buffer.run(consumer -> {
                var workChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(quarry.currentX, quarry.currentZ));
                if (workChunk == null) workChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(quarry.currentX, quarry.currentZ));
                if (workChunk == null) return;

                workChunk.addKeepLoaded();
                workChunk.setFlag(ChunkFlag.TICKING, true);
                workChunk.resetKeepAlive();
            });

        }

        // Get output container above quarry - REQUIRED to mine
        ItemContainer outputContainer = getOutputContainerAbove(world, store, quarryX, quarryY, quarryZ);
        if (outputContainer == null) {
            HytaleIndustriesPlugin.LOGGER.atFiner().log(
                    "[Quarry] No output container above at (%d,%d,%d); skipping tick.",
                    quarryX, quarryY + 1, quarryZ
            );
            return;  // No output container, cannot mine
        }

        // Check HE consumption
        float speedMul = Math.max(0.0001f, quarry.speed);
        float effMul = Math.max(0.0001f, quarry.efficiency);

        double heCost = (HE_CONSUMPTION_PER_TICK * dt) / effMul;
        if (energy.current < heCost) {
            HytaleIndustriesPlugin.LOGGER.atFiner().log(
                    "[Quarry] Insufficient HE for tick: have=%d need=%.2f",
                    energy.current, heCost
            );
            return;
        }

        energy.current -= heCost;
        quarry.progress += dt;

        // Mine blocks
        // Air blocks should consume only 0.1 progress (10x faster scan through air)
        while (quarry.progress >= 0.1f && energy.current >= HE_PER_AIR) {
            // Get current target chunk; must already be loaded (do not trigger new chunk loads here)
            var targetChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(quarry.currentX, quarry.currentZ));
            if (targetChunk == null) targetChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(quarry.currentX, quarry.currentZ));
            if (targetChunk == null) {
                HytaleIndustriesPlugin.LOGGER.atFiner().log(
                        "[Quarry] Target chunk not loaded for (%d,%d); stopping.",
                        quarry.currentX, quarry.currentZ
                );
                break;  // Can't access chunk
            }

            int lx = quarry.currentX & 31;
            int lz = quarry.currentZ & 31;
            int blockId = targetChunk.getBlock(lx, quarry.currentY, lz);
            boolean isAir = blockId == 0;
            BlockType targetBlock = isAir ? null : BlockType.getAssetMap().getAsset(blockId);

            // Do not mine bedrock
            if (!isAir && targetBlock != null && "Rock_Bedrock".equals(targetBlock.getId())) {
                HytaleIndustriesPlugin.LOGGER.atFiner().log(
                        "[Quarry] Skipping bedrock at (%d,%d,%d)",
                        quarry.currentX, quarry.currentY, quarry.currentZ
                );
                // Advance without consuming energy/progress beyond this loop iteration
                if (!advancePosition(quarry, quarryX, quarryY, quarryZ)) {
                    HytaleIndustriesPlugin.LOGGER.atInfo().log(
                            "[Quarry] Finished scan volume for quarry at (%d,%d,%d)",
                            quarryX, quarryY, quarryZ
                    );
                    quarry.currentStatus = QuarryComponent.QuarryStatus.IDLE;
                    quarry.clearMiningState(true);
                    quarry.progress = 0.0f;
                    break;
                }
                continue;
            }

            // Required progress per cell: 1.0 for solid, 0.1 for air
            float requiredProgress = (isAir ? 0.1f : 1.0f) / speedMul;
            if (quarry.progress < requiredProgress) {
                break; // not enough accumulated work yet
            }
            double heCostPerBlock = (isAir ? HE_PER_AIR : HE_PER_BLOCK) / effMul;
            if (quarry.gentle && !isAir) {
                heCostPerBlock *= 10.0; // gentle (silk-touch) costs 10x HE for solid blocks
            }

            if (energy.current < heCostPerBlock) {
                HytaleIndustriesPlugin.LOGGER.atFiner().log(
                        "[Quarry] Not enough HE for block at (%d,%d,%d): have=%d need=%.2f (air=%s)",
                        quarry.currentX, quarry.currentY, quarry.currentZ,
                        energy.current, heCostPerBlock, isAir
                );
                break;  // Insufficient energy for next block
            }

            energy.current -= heCostPerBlock;
            quarry.progress -= requiredProgress;

            HytaleIndustriesPlugin.LOGGER.atFiner().log(
                    "[Quarry] Mining block at (%d,%d,%d) air=%s cost=%.2f remainingHE=%d progress=%.3f block=%s",
                    quarry.currentX, quarry.currentY, quarry.currentZ,
                    isAir, heCostPerBlock, energy.current, quarry.progress, targetBlock != null ? targetBlock.getId() : "null"
            );

            if (!isAir && targetBlock != null) {

                // Collect drops
                List<ItemStack> drops = quarry.gentle
                        // silk-touch style
                        ? BlockHarvestUtils.getDrops(targetBlock, 1, null, null)
                        : getBlockDrops(targetBlock); // normal (using gathering config)
                if (!drops.isEmpty()) {
                    if (!outputContainer.canAddItemStacks(drops, false, false)) {
                        HytaleIndustriesPlugin.LOGGER.atFiner().log(
                                "[Quarry] Output full; cannot add drops at (%d,%d,%d)",
                                quarry.currentX, quarry.currentY, quarry.currentZ
                        );
                        break;
                    }
                    ListTransaction<ItemStackTransaction> addTx = outputContainer.addItemStacks(drops, false, false, false);
                    if (addTx == null || !addTx.succeeded()) {
                        HytaleIndustriesPlugin.LOGGER.atFiner().log(
                                "[Quarry] Failed to add drops at (%d,%d,%d)",
                                quarry.currentX, quarry.currentY, quarry.currentZ
                        );
                        break;
                    }
                }

                // Defer block breaking via CommandBuffer.run so it happens outside Store processing.
                final int fx = quarry.currentX;
                final int fy = quarry.currentY;
                final int fz = quarry.currentZ;
                buffer.run(_store -> {
                    World w = _store.getExternalData().getWorld();
                    if (w == null) return;
                    WorldChunk ch = w.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(fx, fz));
                    if (ch == null) ch = w.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(fx, fz));
                    if (ch == null) return;
                    int lx2 = fx & 31;
                    int lz2 = fz & 31;
                    ch.breakBlock(lx2, fy, lz2);
                });
            }

            // Advance to next block
            if (!advancePosition(quarry, quarryX, quarryY, quarryZ)) {
                // Finished mining
                HytaleIndustriesPlugin.LOGGER.atInfo().log(
                        "[Quarry] Finished scan volume for quarry at (%d,%d,%d)",
                        quarryX, quarryY, quarryZ
                );
                quarry.currentStatus = QuarryComponent.QuarryStatus.IDLE;
                quarry.clearMiningState(true);
                quarry.progress = 0.0f;
                break;
            }
        }

        buffer.replaceComponent(chunk.getReferenceTo(index), quarryType, quarry);
        buffer.replaceComponent(chunk.getReferenceTo(index), storesHeType, energy);
    }

    @Nonnull
    private static List<ItemStack> getBlockDrops(@Nonnull BlockType blockType) {
        int quantity = 1;
        String itemId = null;
        String dropListId = null;

        BlockGathering gathering = blockType.getGathering();
        if (gathering != null) {
            PhysicsDropType physics = gathering.getPhysics();
            BlockBreakingDropType breaking = gathering.getBreaking();
            SoftBlockDropType soft = gathering.getSoft();
            HarvestingDropType harvest = gathering.getHarvest();

            if (breaking != null) {
                quantity = breaking.getQuantity();
                itemId = breaking.getItemId();
                dropListId = breaking.getDropListId();
            } else if (physics != null) {
                itemId = physics.getItemId();
                dropListId = physics.getDropListId();
            } else if (soft != null) {
                itemId = soft.getItemId();
                dropListId = soft.getDropListId();
            } else if (harvest != null) {
                itemId = harvest.getItemId();
                dropListId = harvest.getDropListId();
            }
        }

        return BlockHarvestUtils.getDrops(blockType, quantity, itemId, dropListId);
    }

    private boolean advancePosition(QuarryComponent quarry, int quarryX, int quarryY, int quarryZ) {
        if (!quarry.hasCurrentPos() || !quarry.hasScanBounds()) return false;

        // Advance to next position - use exclusive upper bounds like BlockState (>= instead of >)
        quarry.currentX++;
        if (quarry.currentX >= quarry.endX) {
            quarry.currentX = quarry.startX;
            quarry.currentZ++;
            if (quarry.currentZ >= quarry.endZ) {
                quarry.currentZ = quarry.startZ; // reset to startZ, not startX
                quarry.currentY--;
                if (quarry.currentY < 0) {
                    return false;
                }
            }
        }

        // Safety check: don't break quarry itself or output container above it
        if (quarry.currentX == quarryX && quarry.currentZ == quarryZ && 
            (quarry.currentY == quarryY || quarry.currentY == quarryY + 1)) {
            return advancePosition(quarry, quarryX, quarryY, quarryZ);
        }

        return true;
    }

    @Nullable
    private ItemContainer getOutputContainerAbove(World world, Store<ChunkStore> store, int quarryX, int quarryY, int quarryZ) {
        int aboveY = quarryY + 1;
        if (aboveY < 0 || aboveY >= 320) return null;

        // Use InventoryAdapters to detect a real machine inventory above the quarry.
        List<MachineInventory> inventories = InventoryAdapters.find(world, store, quarryX, aboveY, quarryZ);
        if (inventories == null || inventories.isEmpty()) {
            return null;
        }
        MachineInventory inv = inventories.get(0);
        return inv != null ? inv.getContainer() : null;
    }

    // No world.getBlockType here to avoid loading chunks while a Store is processing.
    private boolean isAir(World world, int x, int y, int z) {
        if (y < 0 || y >= 320) return true;
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return true;
        int lx = x & 31;
        int lz = z & 31;
        int blockId = chunk.getBlock(lx, y, lz);
        return blockId == 0;
    }
}
