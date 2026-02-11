package dev.dukedarius.HytaleIndustries.Components.Storage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.ItemSlotFilter;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Basic Item Cache
 *
 * Logical design:
 * - 1 input slot: items inserted here are converted into a logical counter.
 * - 1 output slot: always exposes up to a normal stack of the cached item.
 * - Internal fields cache the item id and total quantity (up to 16x max stack).
 *
 * The original single "Inventory" container is kept for backward-compat JSON
 * compatibility but is no longer used directly by systems.
 */
public class BasicItemCacheComponent implements Component<ChunkStore> {
    /** Single overstack slot: holds up to 16x base stack of one item. */
    public CacheItemContainer slot = new CacheItemContainer((short) 1, CacheItemContainer.DEFAULT_MAX_STACK);

    /** Cached item id; null when empty. */
    public String cachedItemId;
    /** Logical total quantity stored. */
    public long cachedCount;
    /** Maximum logical quantity (16x base stack). */
    public long maxCount;
    /** Last exposed stack size we showed in the slot (to detect delta). */
    public int lastExposedCount;

    public static final BuilderCodec<BasicItemCacheComponent> CODEC = BuilderCodec.builder(
                    BasicItemCacheComponent.class,
                    BasicItemCacheComponent::new
            )
            // Single slot
            .append(new KeyedCodec<>("Slot", SimpleItemContainer.CODEC),
                    (o, v) -> o.slot = dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer.from(v,
                            (int) (o.maxCount > 0 ? Math.min(o.maxCount, Integer.MAX_VALUE) : CacheItemContainer.DEFAULT_MAX_STACK)),
                    o -> o.slot != null ? o.slot.toSimple() : new SimpleItemContainer((short) 1))
            .add()
            .append(new KeyedCodec<>("CachedItemId", Codec.STRING),
                    (o, v) -> o.cachedItemId = v,
                    o -> o.cachedItemId)
            .add()
            .append(new KeyedCodec<>("CachedCount", Codec.LONG),
                    (o, v) -> o.cachedCount = v,
                    o -> o.cachedCount)
            .add()
            .append(new KeyedCodec<>("MaxCount", Codec.LONG),
                    (o, v) -> o.maxCount = v,
                    o -> o.maxCount)
            .add()
            .append(new KeyedCodec<>("LastExposedCount", Codec.INTEGER),
                    (o, v) -> o.lastExposedCount = v,
                    o -> o.lastExposedCount)
            .add()
            .build();

    @Override
    public BasicItemCacheComponent clone() {
        BasicItemCacheComponent copy = new BasicItemCacheComponent();

        copy.slot = this.slot != null
                ? (CacheItemContainer) this.slot.clone()
                : new CacheItemContainer((short) 1, 64);
        copy.cachedItemId = this.cachedItemId;
        copy.cachedCount = this.cachedCount;
        copy.maxCount = this.maxCount;
        copy.lastExposedCount = this.lastExposedCount;
        return copy;
    }


}
