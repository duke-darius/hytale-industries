package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Vector2i;
import com.hypixel.hytale.protocol.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class QuarryBlockState extends BlockState implements TickableBlockState, ItemContainerBlockState, StoresHE, ReceivesHE {

    public static final String STATE_ID = "quarry";
    private static final double HE_CAPACITY = 10_000.0;
    private static final double HE_CONSUMPTION_PER_SECOND = 10;

    private SimpleItemContainer inventory = new SimpleItemContainer((short) 20);

    private double heStored = 0.0;
    private float progress = 0.0f;

    /**
     * Exclusive start position of quarry
     */
    private Vector2i startPos;
    /**
     * Exclusive end position of quarry
     */
    private Vector2i endPos;

    private Vector3i currentPos;
    private float speed = 8f;

    private transient int lastKeptChunkX = Integer.MIN_VALUE;
    private transient int lastKeptChunkZ = Integer.MIN_VALUE;

    public static final Codec<QuarryBlockState> CODEC = BuilderCodec.builder(
            QuarryBlockState.class,
            QuarryBlockState::new,
            BlockState.BASE_CODEC
    ).append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (s, v) -> s.heStored = v, s -> s.heStored)
            .add()
            .append(new KeyedCodec<>("Progress", Codec.FLOAT), (s, v) -> s.progress = v, s -> s.progress)
            .add()
            .append(new KeyedCodec<>("Inventory", SimpleItemContainer.CODEC), (s, v) -> s.inventory = v, s -> s.inventory)
            .add()
            .build();

    @Override
    public void tick(float v, int i, ArchetypeChunk<ChunkStore> archetypeChunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        if (v <= 0) return;

        if (startPos == null || endPos == null) {
            var pos = this.getBlockPosition();
            var rot = this.getRotationIndex();

            // 10 blocks to the left, 20 blocks forwards
            // 10 blocks to the right, 0 blocks forwards
            switch (rot) {
                case 0 -> { // North? Assuming 0 is North, -Z is forward
                    startPos = new Vector2i(pos.x - 10, pos.z - 20);
                    endPos = new Vector2i(pos.x + 11, pos.z);
                }
                case 1 -> { // East? +X is forward
                    startPos = new Vector2i(pos.x + 1, pos.z - 10);
                    endPos = new Vector2i(pos.x + 21, pos.z + 11);
                }
                case 2 -> { // South? +Z is forward
                    startPos = new Vector2i(pos.x - 10, pos.z + 1);
                    endPos = new Vector2i(pos.x + 11, pos.z + 21);
                }
                case 3 -> { // West? -X is forward
                    startPos = new Vector2i(pos.x - 20, pos.z - 10);
                    endPos = new Vector2i(pos.x, pos.z + 11);
                }
                default -> {
                    startPos = new Vector2i(pos.x - 10, pos.z - 10);
                    endPos = new Vector2i(pos.x + 11, pos.z + 11);
                }
            }
            currentPos = new Vector3i(startPos.x, WORLD_MAX_Y_EXCLUSIVE - 1, startPos.y);
            persistSelf();
            return;
        }

        WorldChunk selfChunk = getChunk();
        if (selfChunk == null) return;
        World world = selfChunk.getWorld();
        if (world == null) return;

        // Keep the chunk containing the quarry itself alive/ticking.
        selfChunk.setKeepLoaded(true);
        selfChunk.setFlag(ChunkFlag.TICKING, true);
        selfChunk.resetKeepAlive();

        // Also keep the currently processed chunk loaded/ticking.
        if (currentPos != null) {
            int cx = Math.floorDiv(currentPos.x, 32);
            int cz = Math.floorDiv(currentPos.z, 32);
            if (cx != lastKeptChunkX || cz != lastKeptChunkZ) {
                lastKeptChunkX = cx;
                lastKeptChunkZ = cz;
                world.getChunkAsync(cx, cz).thenAccept(ch -> {
                    if (ch != null) {
                        ch.setKeepLoaded(true);
                        ch.setFlag(ChunkFlag.TICKING, true);
                        ch.resetKeepAlive();
                    }
                });
            }
        }

        // Output inventory must exist directly above the quarry.
        ItemContainer outputContainer = getOutputContainerAbove(world);
        if (outputContainer == null) {
            return;
        }

        if (heStored < HE_CONSUMPTION_PER_SECOND * v) {
            return;
        }

        heStored -= HE_CONSUMPTION_PER_SECOND * v;
        progress += v;

        if (progress >= 1f / speed) {
            int blocksProcessed = 0;
            while (blocksProcessed < 100 && progress >= 1f / speed) {
                AdvanceResult result = advancePosition(world, outputContainer);
                if (result == AdvanceResult.FINISHED) {
                    progress = 0;
                    break;
                }
                if (result == AdvanceResult.SUCCESS) {
                    progress -= 1f / speed;
                    blocksProcessed++;
                } else if (result == AdvanceResult.AIR) {
                    blocksProcessed++;
                } else if (result == AdvanceResult.FAILURE) {
                    // Can't safely continue (e.g., no target chunk loaded or output inventory rejected drops).
                    break;
                }
            }
            persistSelf();
        }
    }

    private void persistSelf() {
        var chunk = getChunk();
        var pos = getBlockPosition();
        if (chunk == null || pos == null) return;
        var stateRef = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (stateRef == null) return;
        ComponentType<ChunkStore, Component<ChunkStore>> type =
                (ComponentType) BlockStateModule.get().getComponentType((Class) this.getClass());
        if (type == null) return;
        stateRef.getStore().replaceComponent(stateRef, type, this);
        chunk.markNeedsSaving();
    }

    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    @Nullable
    private ItemContainer getOutputContainerAbove(@Nonnull World world) {
        var selfPos = getBlockPosition();
        if (selfPos == null) return null;

        int aboveY = selfPos.y + 1;
        if (aboveY < WORLD_MIN_Y || aboveY >= WORLD_MAX_Y_EXCLUSIVE) {
            return null;
        }

        int[] origin = resolveFillerOrigin(world, selfPos.x, aboveY, selfPos.z);
        int ox = origin[0], oy = origin[1], oz = origin[2];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(ox, oz));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(ox, oz));
        }
        if (chunk == null) {
            return null;
        }

        int lx = ox & 31;
        int lz = oz & 31;

        BlockState state = chunk.getState(lx, oy, lz);
        ItemContainerBlockState inv = null;
        if (state instanceof ItemContainerBlockState icbs) {
            inv = icbs;
        } else {
            // Fallback: load the block state from the component entity if present.
            Ref<ChunkStore> ref = chunk.getBlockComponentEntity(lx, oy, lz);
            if (ref != null) {
                BlockState bs = BlockState.getBlockState(ref, ref.getStore());
                if (bs instanceof ItemContainerBlockState ic) {
                    inv = ic;
                }
            }
        }

        if (inv == null) {
            return null;
        }

        ItemContainer container = inv.getItemContainer();
        return container == null ? null : container;
    }

    // Resolve filler blocks to their origin block coordinates; returns {x,y,z}.
    private static int[] resolveFillerOrigin(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) {
            return new int[]{x, y, z};
        }
        int filler = chunk.getFiller(x & 31, y, z & 31);
        if (filler == 0) {
            return new int[]{x, y, z};
        }
        int dx = FillerBlockUtil.unpackX(filler);
        int dy = FillerBlockUtil.unpackY(filler);
        int dz = FillerBlockUtil.unpackZ(filler);
        return new int[]{x - dx, y - dy, z - dz};
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

    private static boolean insertedAll(@Nullable ListTransaction<ItemStackTransaction> tx) {
        if (tx == null || !tx.succeeded()) {
            return false;
        }
        for (ItemStackTransaction t : tx.getList()) {
            if (t == null || !t.succeeded()) {
                return false;
            }
            if (!ItemStack.isEmpty(t.getRemainder())) {
                return false;
            }
        }
        return true;
    }

    private AdvanceResult advancePosition(@Nonnull World world, @Nonnull ItemContainer outputContainer) {
        if (currentPos == null) return AdvanceResult.FAILURE;
        if (startPos == null || endPos == null) return AdvanceResult.FAILURE;

        // We may cross chunk boundaries while scanning.
        WorldChunk targetChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(currentPos.x, currentPos.z));
        if (targetChunk == null) {
            targetChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(currentPos.x, currentPos.z));
        }
        if (targetChunk == null) {
            return AdvanceResult.FAILURE;
        }

        int blockId = world.getBlock(currentPos.x, currentPos.y, currentPos.z);
        boolean isAir = blockId == 0;

        if (!isAir) {
            BlockType blockType = world.getBlockType(currentPos.x, currentPos.y, currentPos.z);
            if (blockType != null) {
                List<ItemStack> drops = getBlockDrops(blockType);
                // Ensure drops fit before breaking the block.
                if (!outputContainer.canAddItemStacks(drops)) {
                    return AdvanceResult.FAILURE;
                }
                ListTransaction<ItemStackTransaction> addTx = outputContainer.addItemStacks(drops, true, false, true);
                if (!insertedAll(addTx)) {
                    return AdvanceResult.FAILURE;
                }
            }

            int lx = currentPos.x & 31;
            int lz = currentPos.z & 31;
            targetChunk.breakBlock(lx, currentPos.y, lz);
        }

        // Advance to next position, strictly within startPos and endPos
        currentPos.x++;
        if (currentPos.x >= endPos.x) {
            currentPos.x = startPos.x;
            currentPos.z++;
            if (currentPos.z >= endPos.y) {
                currentPos.z = startPos.y;
                currentPos.y--;
                HytaleIndustriesPlugin.LOGGER.atInfo().log("Quarry reached y=%d", currentPos.y);
                if (currentPos.y < WORLD_MIN_Y) {
                    return AdvanceResult.FINISHED;
                }
            }
        }

        // Safety check: ensure we are NOT breaking the quarry block itself or the output container above it.
        var selfPos = getBlockPosition();
        if (selfPos != null) {
            if (currentPos.x == selfPos.x && currentPos.z == selfPos.z && (currentPos.y == selfPos.y || currentPos.y == selfPos.y + 1)) {
                HytaleIndustriesPlugin.LOGGER.atFine().log("Quarry reached self position");
                // If by some logic we ended up here, skip this position.
                return AdvanceResult.AIR;
            }
        }

        return isAir ? AdvanceResult.AIR : AdvanceResult.SUCCESS;
    }

    @Override
    public ItemContainer getItemContainer() {
        return inventory;
    }

    @Override
    public double getHeStored() {
        return heStored;
    }

    @Override
    public void setHeStored(double he) {
        this.heStored = Math.min(he, HE_CAPACITY);
    }

    @Override
    public double getHeCapacity() {
        return HE_CAPACITY;
    }

    @Override
    public double receiveHe(double he) {
        double canReceive = HE_CAPACITY - heStored;
        double toReceive = Math.min(canReceive, he);
        heStored += toReceive;
        return toReceive;
    }

    public enum Status {
        IDLE,
        ACTIVE,
        PAUSED,
        ERROR
    }

    public enum AdvanceResult {
        AIR, // Air blocks will be skipped (up to 10 times a tick)
        SUCCESS,
        FINISHED,
        FAILURE
    }
}
