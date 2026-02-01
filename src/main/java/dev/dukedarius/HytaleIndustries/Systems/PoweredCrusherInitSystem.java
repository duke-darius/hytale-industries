package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredCrusherInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class PoweredCrusherInitSystem extends RefSystem<ChunkStore> {

    private final Query<ChunkStore> query = Query.and(BlockStateInfo.getComponentType());

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> buffer) {
        if (reason != AddReason.SPAWN) return;

        var info = store.getComponent(ref, BlockStateInfo.getComponentType());
        if (info == null) return;
        var chunkRef = info.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) return;
        var blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        int x = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), ChunkUtil.xFromBlockInColumn(info.getIndex()));
        int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int z = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), ChunkUtil.zFromBlockInColumn(info.getIndex()));

        BlockType blockType = BlockType.getAssetMap().getAsset(
                blockChunk.getBlock(ChunkUtil.xFromBlockInColumn(info.getIndex()), y, ChunkUtil.zFromBlockInColumn(info.getIndex()))
        );
        if (blockType == null || blockType.getState() == null) {
            return;
        }

        StoresHE stores = store.getComponent(ref, HytaleIndustriesPlugin.INSTANCE.getStoresHeType());
        if (stores == null) {
            stores = new StoresHE();
            stores.max = 10000;
            stores.current = 0;
            buffer.addComponent(ref, HytaleIndustriesPlugin.INSTANCE.getStoresHeType(), stores);
        }

        ConsumesHE consumes = store.getComponent(ref, HytaleIndustriesPlugin.INSTANCE.getConsumesHeType());
        if (consumes == null) {
            consumes = new ConsumesHE();
            consumes.heConsumption = 20; // 20 HE per tick
            consumes.enabled = false;
            buffer.addComponent(ref, HytaleIndustriesPlugin.INSTANCE.getConsumesHeType(), consumes);
        }

        PoweredCrusherInventory inv = store.getComponent(ref, HytaleIndustriesPlugin.INSTANCE.getPoweredCrusherInventoryType());
        if (inv == null) {
            inv = new PoweredCrusherInventory();
            buffer.addComponent(ref, HytaleIndustriesPlugin.INSTANCE.getPoweredCrusherInventoryType(), inv);
        } else {
            boolean dirty = false;
            if (inv.input == null || inv.input.getCapacity() <= 0) {
                inv.input = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                dirty = true;
            }
            if (inv.output == null || inv.output.getCapacity() <= 0) {
                inv.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
                dirty = true;
            }
            if (dirty) {
                buffer.replaceComponent(ref, HytaleIndustriesPlugin.INSTANCE.getPoweredCrusherInventoryType(), inv);
            }
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull com.hypixel.hytale.component.RemoveReason removeReason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // no-op
    }
}
