package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
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

        bindEvents(events);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
                                @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl BasicItemCacheUIEventData data) {
        var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null) return;

        World world = store.getExternalData().getWorld();
        var ctx = resolve(world);
        if (ctx == null || ctx.cache == null) return;
        // Require the adapter to be present (keeps pipe-facing contract), but open the specific slot container.
        var invs = dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters.find(
                world, ctx.entity.getStore(), x, y, z);
        if (invs.isEmpty()) return;
        ensureContainers(ctx.cache);
        dev.dukedarius.HytaleIndustries.Inventory.MachineInventory mi = invs.get(0);
        if (BasicItemCacheUIEventData.ACTION_VIEW_OUTPUT.equals(data.action)) {
            reconcileOutput(ctx.cache);
            ctx.entity.getStore().replaceComponent(ctx.entity,
                    HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType(), ctx.cache);
        }
        ContainerWindow win = new ContainerWindow(mi.getContainer());

        player.getPageManager().clearCustomPageAcknowledgements();
        player.getPageManager().setPageWithWindows(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.Inventory, true, win);
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
    private static void bindEvents(@NonNullDecl UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InputButton",
                new com.hypixel.hytale.server.core.ui.builder.EventData()
                        .append(BasicItemCacheUIEventData.KEY_ACTION, BasicItemCacheUIEventData.ACTION_VIEW_INPUT), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OutputButton",
                new com.hypixel.hytale.server.core.ui.builder.EventData()
                        .append(BasicItemCacheUIEventData.KEY_ACTION, BasicItemCacheUIEventData.ACTION_VIEW_OUTPUT), false);
    }

    private Context resolve(World world) {
        if (world == null) return null;
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return null;
        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return null;
        BasicItemCacheComponent cache = entity.getStore().getComponent(
                entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType()
        );
        return new Context(cache, entity);
    }

    private static void ensureContainers(BasicItemCacheComponent cache) {
        if ((cache.input == null || cache.input.getCapacity() <= 0) &&
                cache.inventory != null && cache.inventory.getCapacity() > 0) {
            cache.input = cache.inventory;
        }
        if (cache.input == null || cache.input.getCapacity() <= 0) {
            cache.input = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
        if (cache.output == null || cache.output.getCapacity() <= 0) {
            cache.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
    }

    private static void reconcileOutput(BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0L;
            cache.maxCount = 0L;
            cache.lastOutputCount = 0;
            cache.output.setItemStackForSlot((short) 0, ItemStack.EMPTY);
            return;
        }

        int maxStack = resolveBaseMaxStack(cache);
        if (maxStack <= 0) maxStack = 64;
        int desired = (int) Math.min(cache.cachedCount, (long) maxStack);
        if (desired <= 0) {
            cache.output.setItemStackForSlot((short) 0, ItemStack.EMPTY);
            cache.lastOutputCount = 0;
        } else {
            ItemStack newOut = new ItemStack(cache.cachedItemId, desired);
            cache.output.setItemStackForSlot((short) 0, newOut);
            cache.lastOutputCount = desired;
        }
    }

    private static int resolveBaseMaxStack(@NonNullDecl BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null) return 0;
        try {
            com.hypixel.hytale.server.core.asset.type.item.config.Item item =
                    com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(cache.cachedItemId);
            if (item != null) return item.getMaxStack();
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[BasicItemCacheUI] Failed to resolve item for id '%s'", cache.cachedItemId);
        }
        if (cache.maxCount > 0) {
            long approx = cache.maxCount / 16L;
            if (approx > 0 && approx <= Integer.MAX_VALUE) return (int) approx;
        }
        return 64;
    }

    @Override
    public void onDismiss(@NonNullDecl Ref<EntityStore> ref,
                          @NonNullDecl Store<EntityStore> store) {
        super.onDismiss(ref, store);
    }

    public static final class BasicItemCacheUIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_VIEW_INPUT = "in";
        static final String ACTION_VIEW_OUTPUT = "out";

        public String action;
        public static final BuilderCodec<BasicItemCacheUIEventData> CODEC =
                BuilderCodec.builder(BasicItemCacheUIEventData.class, BasicItemCacheUIEventData::new)
                        .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                                (o, v) -> o.action = v,
                                o -> o.action)
                        .add()
                        .build();
    }

    private record Context(BasicItemCacheComponent cache, Ref<ChunkStore> entity) { }
}
