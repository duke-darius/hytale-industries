package dev.dukedarius.HytaleIndustries.Components.Storage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

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

    public static final BuilderCodec<BasicItemCacheComponent> CODEC = BuilderCodec.builder(
                    BasicItemCacheComponent.class,
                    BasicItemCacheComponent::new
            )
            // Legacy single container, still decoded from assets but treated as input if present.
            .append(new KeyedCodec<>("Inventory", SimpleItemContainer.CODEC),
                    (o, v) -> o.inventory = v,
                    o -> o.inventory)
            .add()
            // Dedicated input container (1 slot).
            .append(new KeyedCodec<>("Input", SimpleItemContainer.CODEC),
                    (o, v) -> o.input = v,
                    o -> o.input)
            .add()
            // Dedicated output container (1 slot).
            .append(new KeyedCodec<>("Output", SimpleItemContainer.CODEC),
                    (o, v) -> o.output = v,
                    o -> o.output)
            .add()
            // Id of the cached item type, or null if empty.
            .append(new KeyedCodec<>("CachedItemId", Codec.STRING),
                    (o, v) -> o.cachedItemId = v,
                    o -> o.cachedItemId)
            .add()
            // Total logical count cached inside this block (0 when empty).
            .append(new KeyedCodec<>("CachedCount", Codec.LONG),
                    (o, v) -> o.cachedCount = v,
                    o -> o.cachedCount)
            .add()
            // Maximum logical count allowed for the current item (16x base max stack).
            .append(new KeyedCodec<>("MaxCount", Codec.LONG),
                    (o, v) -> o.maxCount = v,
                    o -> o.maxCount)
            .add()
            // Last output slot count we exposed, used to detect withdrawals.
            .append(new KeyedCodec<>("LastOutputCount", Codec.INTEGER),
                    (o, v) -> o.lastOutputCount = v,
                    o -> o.lastOutputCount)
            .add()
            .build();

    /**
     * Legacy single-slot container kept for backward compatibility with
     * existing JSON assets that still use the "Inventory" field.
     */
    public SimpleItemContainer inventory = new SimpleItemContainer((short) 1);

    /** Input container: items placed here are absorbed into {@link #cachedCount}. */
    public SimpleItemContainer input = new SimpleItemContainer((short) 1);

    /** Output container: always exposes up to one normal stack of the cached item. */
    public SimpleItemContainer output = new SimpleItemContainer((short) 1);

    /** Cached item id; null if the cache is empty. */
    public String cachedItemId;

    /** Logical total count of items stored in this cache. */
    public long cachedCount;

    /** Maximum logical count allowed for the current item (16x its base max stack). */
    public long maxCount;

    /** Last output stack size we exposed; used to infer withdrawals. */
    public int lastOutputCount;

    @Override
    public BasicItemCacheComponent clone() {
        BasicItemCacheComponent copy = new BasicItemCacheComponent();
        copy.inventory = this.inventory != null
                ? (SimpleItemContainer) this.inventory.clone()
                : new SimpleItemContainer((short) 1);
        copy.input = this.input != null
                ? (SimpleItemContainer) this.input.clone()
                : new SimpleItemContainer((short) 1);
        copy.output = this.output != null
                ? (SimpleItemContainer) this.output.clone()
                : new SimpleItemContainer((short) 1);
        copy.cachedItemId = this.cachedItemId;
        copy.cachedCount = this.cachedCount;
        copy.maxCount = this.maxCount;
        copy.lastOutputCount = this.lastOutputCount;
        return copy;
    }
}
