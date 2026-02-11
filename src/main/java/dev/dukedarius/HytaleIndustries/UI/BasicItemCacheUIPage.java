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
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

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
        cmd.set("#TitleText.Text", "Basic Item Cache");
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

        ensureContainer(ctx.cache);
        reconcileSlot(ctx.cache);
        ctx.entity.getStore().replaceComponent(ctx.entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType(), ctx.cache);

        ContainerWindow win = new ContainerWindow(ctx.cache.slot);
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

        BasicItemCacheComponent cache = entity.getStore().getComponent(
                entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType()
        );
        ensureContainer(cache);
        reconcileSlot(cache);
        entity.getStore().replaceComponent(entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType(), cache);

        boolean summarySet = false;
        if (cache != null && cache.cachedItemId != null && cache.cachedCount > 0) {
            cmd.set("#Contents.Text", String.format("%s x %d", cache.cachedItemId, cache.cachedCount));
            summarySet = true;
        }

        if (cache != null && cache.slot != null && cache.slot.getCapacity() > 0) {
            ItemStack s = cache.slot.getItemStack((short) 0);
            if (s != null && !ItemStack.isEmpty(s)) {
                cmd.set("#InputSlot.ItemId", s.getItemId());
                cmd.set("#InputQty.Text", String.format("%d", s.getQuantity()));
                if (!summarySet) {
                    cmd.set("#Contents.Text", String.format("%s x %d", s.getItemId(), s.getQuantity()));
                    summarySet = true;
                }
            } else {
                cmd.set("#InputSlot.ItemId", "");
                cmd.set("#InputQty.Text", "");
            }
        }

        if (!summarySet) {
            cmd.set("#Contents.Text", "Empty");
        }
    }

    private static void bindEvents(@NonNullDecl UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InputButton",
                new com.hypixel.hytale.server.core.ui.builder.EventData()
                        .append(BasicItemCacheUIEventData.KEY_ACTION, BasicItemCacheUIEventData.ACTION_VIEW_OPEN), false);
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

    private static void ensureContainer(BasicItemCacheComponent cache) {
        if (cache.slot == null || cache.slot.getCapacity() <= 0) {
            cache.slot = new dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer((short) 1, 1024);
        }
    }

    private static void reconcileSlot(BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0L;
            cache.maxCount = 0L;
            cache.lastExposedCount = 0;
            cache.slot.setItemStackForSlot((short) 0, ItemStack.EMPTY);
            return;
        }
        long desired = Math.min(cache.cachedCount, cache.maxCount);
        if (desired <= 0) {
            cache.slot.setItemStackForSlot((short) 0, ItemStack.EMPTY);
            cache.lastExposedCount = 0;
        } else {
            cache.slot.setItemStackForSlot((short) 0, new ItemStack(cache.cachedItemId, (int) desired));
            cache.lastExposedCount = (int) desired;
        }
    }

    private static long computeMaxCount(@NonNullDecl ItemStack stack) {
        try {
            var item = stack.getItem();
            if (item != null) {
                int base = item.getMaxStack();
                if (base <= 0) base = 64;
                return 16L * (long) base;
            }
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[BasicItemCacheUI] Failed to compute maxCount; defaulting.");
        }
        return 16L * 64L;
    }

    public static final class BasicItemCacheUIEventData {
        static final String KEY_ACTION = "Action";
        static final String ACTION_VIEW_OPEN = "open";

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
