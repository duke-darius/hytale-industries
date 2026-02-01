package dev.dukedarius.HytaleIndustries.Inventory.adapters;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;
import dev.dukedarius.HytaleIndustries.Inventory.containers.ContainerMachineInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import java.util.Collections;
import java.util.List;

public class BlockStateItemContainerAdapter implements InventoryAdapter {
    @Override
    public List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z) {
        var state = world.getState(x, y, z, true);
        if (!(state instanceof ItemContainerBlockState containerState)) {
            return Collections.emptyList();
        }
        ItemContainer container = containerState.getItemContainer();
        if (container == null) {
            return Collections.emptyList();
        }

        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        }
        BlockType blockType = null;
        if (chunk != null) {
            blockType = chunk.getBlockType(x & 31, y, z & 31);
        }

        // Normalize block id (strip leading '*' and state suffix) so we can special-case our own conduit blocks.
        String baseId = null;
        if (blockType != null) {
            baseId = blockType.getId();
            if (baseId != null) {
                if (baseId.startsWith("*")) {
                    baseId = baseId.substring(1);
                }
                int stateIdx = baseId.indexOf("_State_");
                if (stateIdx > 0) {
                    baseId = baseId.substring(0, stateIdx);
                }
            }
        }

        // Pipes and power cables use ItemContainerBlockState internally for visuals, but they do NOT have real inventories.
        // Never expose them as MachineInventory, otherwise item pipes will think cables are destinations.
        if ("HytaleIndustries_BasicItemPipe".equals(baseId) || "HytaleIndustries_BasicPowerCable".equals(baseId)) {
            HytaleIndustriesPlugin.LOGGER.atFine().log(
                    "[BlockStateItemContainerAdapter] Skipping conduit block at (%d,%d,%d) id=%s as inventory",
                    x, y, z, baseId);
            return Collections.emptyList();
        }

        BlockType finalBlockType = blockType;
        return Collections.singletonList(new ContainerMachineInventory(container, slot -> mapSlotIO(finalBlockType, container, slot)));
    }

    private SlotIO mapSlotIO(BlockType blockType, ItemContainer container, int slot) {
        int cap = container.getCapacity();
        if (slot < 0 || slot >= cap) return SlotIO.NONE;

        // Powered furnace (mod asset)
        if (blockType != null && blockType.getState() != null && "poweredFurnace".equals(blockType.getState().getId())) {
            if (slot == 0) return SlotIO.INPUT;
            if (slot == 1) return SlotIO.OUTPUT;
        }

        // Vanilla processing benches: [inputs][fuel][outputs]
        if (blockType != null) {
            Bench bench = blockType.getBench();
            if (bench instanceof ProcessingBench processing) {
                int tierLevel = 1;
                int inputCount = 0;
                int fuelCount = 0;
                int outputCount = 1;
                try { inputCount = processing.getInput(tierLevel).length; } catch (Throwable ignored) {}
                try { fuelCount = processing.getFuel() != null ? processing.getFuel().length : 0; } catch (Throwable ignored) {}
                try { outputCount = Math.max(1, processing.getOutputSlotsCount(tierLevel)); } catch (Throwable ignored) {}

                int inputEnd = inputCount;
                int fuelEnd = inputEnd + fuelCount;
                int outputEnd = fuelEnd + outputCount;

                if (slot < inputEnd) return SlotIO.INPUT;
                if (slot < fuelEnd) return SlotIO.INPUT;
                if (slot < outputEnd) return SlotIO.OUTPUT;
            }
        }

        // Default: treat as both directions allowed.
        return SlotIO.BOTH;
    }
}
