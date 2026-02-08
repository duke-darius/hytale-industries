package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Very simple UI for the Basic Item Cache.
 * Shows a title and the cached item/count as text (backed by the logical
 * counter rather than the physical slot contents).
 */
public class BasicItemCacheUIPage extends InteractiveCustomUIPage<BasicItemCacheUIPage.BasicItemCacheUIEventData> {

    private final int x;
    private final int y;
    private final int z;

    public BasicItemCacheUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, BasicItemCacheUIEventData.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
                      @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events,
                      @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_BasicItemCache.ui");

        // Basic title
        cmd.set("#TitleText.Text", "Basic Item Cache");

        // Show simple text about what is stored
        updateContentsText(cmd, store);
    }

    private void updateContentsText(@NonNullDecl UICommandBuilder cmd,
                                    @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            cmd.set("#Contents.Text", "No world");
            return;
        }

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            cmd.set("#Contents.Text", "Chunk not loaded");
            return;
        }

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) {
            cmd.set("#Contents.Text", "No cache entity");
            return;
        }

        // Prefer reading directly from the BasicItemCache component's logical
        // state (cached item id + logical count).
        BasicItemCacheComponent cache = entity.getStore().getComponent(
                entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType()
        );

        // Opportunistically absorb any items sitting in the INPUT slot into
        // the logical counter so that even if the ECS system hasn't ticked
        // yet, opening the UI will reconcile state.
        if (cache != null && cache.input != null && cache.input.getCapacity() > 0) {
            ItemStack in = cache.input.getItemStack((short) 0);
            if (in != null && !ItemStack.isEmpty(in)) {
                String inId = in.getItemId();
                if (inId != null) {
                    if (cache.cachedItemId == null || cache.cachedCount <= 0) {
                        cache.cachedItemId = inId;
                        cache.cachedCount = 0L;
                        // Compute maxCount similar to BasicItemCacheSystem
                        try {
                            var item = in.getItem();
                            int base = (item != null && item.getMaxStack() > 0) ? item.getMaxStack() : 64;
                            cache.maxCount = 16L * (long) base;
                        } catch (Throwable t) {
                            cache.maxCount = 16L * 64L;
                        }
                    }
                    if (cache.cachedItemId != null && cache.cachedItemId.equals(inId)) {
                        if (cache.maxCount <= 0) {
                            try {
                                var item = in.getItem();
                                int base = (item != null && item.getMaxStack() > 0) ? item.getMaxStack() : 64;
                                cache.maxCount = 16L * (long) base;
                            } catch (Throwable t) {
                                cache.maxCount = 16L * 64L;
                            }
                        }
                        long space = cache.maxCount - cache.cachedCount;
                        if (space > 0) {
                            int qty = in.getQuantity();
                            long toAdd = Math.min(space, qty);
                            if (toAdd > 0) {
                                cache.cachedCount += toAdd;
                                int remaining = (int) (qty - toAdd);
                                cache.input.setItemStackForSlot((short) 0,
                                        remaining <= 0 ? ItemStack.EMPTY : in.withQuantity(remaining));
                            }
                        }
                    }
                }
                // Persist any changes back to the chunk-store.
                entity.getStore().replaceComponent(entity,
                        HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType(), cache);
            }
        }
        boolean summarySet = false;

        // 1) Summary text from logical state if available
        if (cache != null && cache.cachedItemId != null && cache.cachedCount > 0) {
            cmd.set("#Contents.Text", String.format("%s x %d", cache.cachedItemId, cache.cachedCount));
            summarySet = true;
        }

        // 2) Fill input/output slot visuals from the component's containers if present
        boolean inputHas = false;
        boolean outputHas = false;
        if (cache != null) {
            // Input slot (physical container)
            if (cache.input != null && cache.input.getCapacity() > 0) {
                ItemStack in = cache.input.getItemStack((short) 0);
                if (in != null && !ItemStack.isEmpty(in)) {
                    cmd.set("#InputSlot.ItemId", in.getItemId());
                    cmd.set("#InputQty.Text", String.format("%d", in.getQuantity()));
                    inputHas = true;
                }
            }
            // Output slot (physical container maintained by BasicItemCacheSystem)
            if (cache.output != null && cache.output.getCapacity() > 0) {
                ItemStack out = cache.output.getItemStack((short) 0);
                if (out != null && !ItemStack.isEmpty(out)) {
                    cmd.set("#OutputSlot.ItemId", out.getItemId());
                    cmd.set("#OutputQty.Text", String.format("%d", out.getQuantity()));
                    outputHas = true;
                    // If we didn't have a logical summary, at least show what the
                    // player can take right now.
                    if (!summarySet) {
                        cmd.set("#Contents.Text", String.format("%s x %d", out.getItemId(), out.getQuantity()));
                        summarySet = true;
                    }
                }
            }
        }

        // 3) Fallback: use adapter if component/containers are missing
        if (!inputHas || !outputHas || !summarySet) {
            var inventories = InventoryAdapters.find(world, entity.getStore(), x, y, z);
            if (!inventories.isEmpty()) {
                MachineInventory inv = inventories.get(0);
                ItemContainer container = inv.getContainer();
                if (container != null && container.getCapacity() > 0) {
                    // Input from slot 0
                    if (!inputHas && container.getCapacity() > 0) {
                        ItemStack in2 = container.getItemStack((short) 0);
                        if (in2 != null && !ItemStack.isEmpty(in2)) {
                            cmd.set("#InputSlot.ItemId", in2.getItemId());
                            cmd.set("#InputQty.Text", String.format("%d", in2.getQuantity()));
                            inputHas = true;
                        }
                    }
                    // Output from slot 1 if it exists, otherwise slot 0
                    short outSlot = (short) (container.getCapacity() > 1 ? 1 : 0);
                    if (!outputHas) {
                        ItemStack out2 = container.getItemStack(outSlot);
                        if (out2 != null && !ItemStack.isEmpty(out2)) {
                            cmd.set("#OutputSlot.ItemId", out2.getItemId());
                            cmd.set("#OutputQty.Text", String.format("%d", out2.getQuantity()));
                            outputHas = true;
                            if (!summarySet) {
                                cmd.set("#Contents.Text", String.format("%s x %d", out2.getItemId(), out2.getQuantity()));
                                summarySet = true;
                            }
                        }
                    }
                }
            }
        }

        // 4) Clear any slots that don't have items
        if (!inputHas) {
            cmd.set("#InputSlot.ItemId", "");
            cmd.set("#InputQty.Text", "");
        }
        if (!outputHas) {
            cmd.set("#OutputSlot.ItemId", "");
            cmd.set("#OutputQty.Text", "");
        }
        if (!summarySet) {
            cmd.set("#Contents.Text", "Empty");
        }
    }

    @Override
    public void onDismiss(@NonNullDecl Ref<EntityStore> ref,
                          @NonNullDecl Store<EntityStore> store) {
        super.onDismiss(ref, store);
    }

    public static final class BasicItemCacheUIEventData {
        public static final BuilderCodec<BasicItemCacheUIEventData> CODEC =
                BuilderCodec.builder(BasicItemCacheUIEventData.class, BasicItemCacheUIEventData::new)
                        .build();
    }
}
