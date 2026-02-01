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
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent.FilterMode;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-side filter configuration UI for a single pipe face.
 * Opened from ConfigurePipeUIPage via right-click.
 */
public class PipeFilterUIPage extends InteractiveCustomUIPage<PipeFilterUIPage.PipeFilterUIEventData> {

    private final int x;
    private final int y;
    private final int z;
    private final Vector3i dirVec;
    private final String sideName;

    public PipeFilterUIPage(@Nonnull PlayerRef playerRef,
                            int x,
                            int y,
                            int z,
                            @Nonnull Vector3i dirVec,
                            @Nonnull String sideName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PipeFilterUIEventData.CODEC);
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirVec = new Vector3i(dirVec.x, dirVec.y, dirVec.z);
        this.sideName = sideName;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_PipeFilter.ui");
        render(cmd, events, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull PipeFilterUIEventData data) {
        if (data.action == null) {
            return;
        }

        var changed = false;
        switch (data.action) {
            case PipeFilterUIEventData.ACTION_SET_MODE -> {
                if (data.mode == null) {
                    return;
                }
                FilterMode mode;
                try {
                    mode = FilterMode.valueOf(data.mode);
                } catch (IllegalArgumentException ex) {
                    return;
                }
                changed = true;
                updateFilterMode(store, mode, null);
            }
            case PipeFilterUIEventData.ACTION_EDIT_ITEMS -> {
                openItemSelector(ref, store);
            }
            case PipeFilterUIEventData.ACTION_CLEAR -> {
                HytaleIndustriesPlugin.LOGGER.atInfo().log("Clearing filter items");
                updateFilterMode(store, FilterMode.None, new String[0]);
                changed = true;
            }
            case PipeFilterUIEventData.ACTION_BACK -> {
                // Re-open main pipe config page
                Player player = store.getComponent(ref, Player.getComponentType());
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (player != null && playerRef != null) {
                    var page = new ConfigurePipeUIPage(playerRef, new Vector3i(x, y, z));
                    player.getPageManager().openCustomPage(ref, store, page);
                }
            }
        }

        // After any change (except Back or EditItems which open another page), re-render this page
        if (changed) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            render(cmd, events, store);
            sendUpdate(cmd, events, false);
        }
    }

    private void render(@Nonnull UICommandBuilder cmd,
                        @Nonnull UIEventBuilder events,
                        @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        FilterMode mode = FilterMode.None;
        String[] items = new String[0];

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            if (stateRef != null) {
                BasicItemPipeComponent pipe = stateRef.getStore().getComponent(stateRef, BasicItemPipeComponent.getComponentType());
                if (pipe != null) {
                    mode = pipe.getFilterMode(dirVec);
                    String[] arr = pipe.getFilterItems(dirVec);
                    if (arr != null) {
                        items = arr;
                    }
                }
            }
        }

        // Side label and summary
        cmd.set("#SideLabel.Text", "Side: " + sideName + ", Filter: " + mode.name());
        cmd.set("#ItemsSummary.Text", "Items: " + items.length);

        // Rebuild the visual list of items (clear first so removed items disappear immediately)
        cmd.clear("#ItemsListContainer");
        for (int i = 0; i < items.length; i++) {
            var itemId = items[i];
            cmd.append("#ItemsListContainer", "Pages/HytaleIndustries_ItemSelectorSlot.ui");

            var item = Item.getAssetMap().getAsset(itemId);
            if(item == null) continue;
            String selector = "#ItemsListContainer[" + i + "] ";
            cmd.set(selector + "#ItemSlot.ItemId", itemId);
            cmd.set(selector + "#ItemBorder.Background", "#00000000");
        }

        // Bind buttons
        bindModeButton(events, "#ModeNoneButton", FilterMode.None);
        bindModeButton(events, "#ModeWhitelistButton", FilterMode.Whitelist);
        bindModeButton(events, "#ModeBlacklistButton", FilterMode.Blacklist);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#EditItemsButton",
                new EventData().append(PipeFilterUIEventData.KEY_ACTION, PipeFilterUIEventData.ACTION_EDIT_ITEMS),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearButton",
                new EventData().append(PipeFilterUIEventData.KEY_ACTION, PipeFilterUIEventData.ACTION_CLEAR),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackButton",
                new EventData().append(PipeFilterUIEventData.KEY_ACTION, PipeFilterUIEventData.ACTION_BACK),
                false
        );
    }

    private static void bindModeButton(@Nonnull UIEventBuilder events,
                                       @Nonnull String selector,
                                       @Nonnull FilterMode mode) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData()
                        .append(PipeFilterUIEventData.KEY_ACTION, PipeFilterUIEventData.ACTION_SET_MODE)
                        .append(PipeFilterUIEventData.KEY_MODE, mode.name()),
                false
        );
    }

    private void updateFilterMode(@Nonnull Store<EntityStore> store,
                                  @Nonnull FilterMode mode,
                                  @Nullable String[] overrideItems) {
        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) {
            return;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (stateRef == null) {
            return;
        }

        BasicItemPipeComponent pipe = stateRef.getStore().getComponent(stateRef, BasicItemPipeComponent.getComponentType());
        if (pipe == null) {
            return;
        }

        String[] items = overrideItems;
        if (items == null) {
            // Preserve existing items when only changing mode
            String[] existing = pipe.getFilterItems(dirVec);
            items = existing != null ? existing : new String[0];
        }

        pipe.setFilter(dirVec, mode, items);
        stateRef.getStore().replaceComponent(stateRef, BasicItemPipeComponent.getComponentType(), pipe);
        chunk.markNeedsSaving();
    }

    private void openItemSelector(@Nonnull Ref<EntityStore> playerEntityRef,
                                  @Nonnull Store<EntityStore> store) {
        // Build candidate list from all registered items
        List<String> candidates = Item.getAssetMap().getAssetMap().keySet().stream()
                .filter(id -> id != null && !id.isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        // Preselected: current filter items
        World world = store.getExternalData().getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        List<String> preselected = new ArrayList<>();
        if (chunk != null) {
            Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x & 31, y, z & 31);
            if (stateRef != null) {
                BasicItemPipeComponent pipe = stateRef.getStore().getComponent(stateRef, BasicItemPipeComponent.getComponentType());
                if (pipe != null) {
                    String[] existing = pipe.getFilterItems(dirVec);
                    if (existing != null) {
                        preselected.addAll(Arrays.asList(existing));
                    }
                }
            }
        }

        ItemSelectorHelper.open(
                playerEntityRef,
                store,
                candidates,
                preselected,
                (playerRef, selected) -> {
                    // Apply new items without touching UI
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null) {
                        return;
                    }
                    Store<EntityStore> s = ref.getStore();
                    World w = s.getExternalData().getWorld();
                    WorldChunk c = w.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                    if (c == null) {
                        c = w.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                    }
                    if (c == null) {
                        return;
                    }
                    Ref<ChunkStore> stateRef = c.getBlockComponentEntity(x & 31, y, z & 31);
                    if (stateRef == null) {
                        return;
                    }
                    BasicItemPipeComponent pipe = stateRef.getStore().getComponent(stateRef, BasicItemPipeComponent.getComponentType());
                    if (pipe == null) {
                        return;
                    }

                    FilterMode mode = pipe.getFilterMode(dirVec);
                    String[] arr = selected.toArray(new String[0]);
                    pipe.setFilter(dirVec, mode, arr);
                    stateRef.getStore().replaceComponent(stateRef, BasicItemPipeComponent.getComponentType(), pipe);
                    c.markNeedsSaving();
                },
                playerRef -> {
                    // Reopen this PipeFilter page after the selector closes (confirm or cancel)
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null) {
                        return;
                    }
                    Store<EntityStore> s = ref.getStore();
                    Player player = s.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        PipeFilterUIPage page = new PipeFilterUIPage(playerRef, x, y, z, dirVec, sideName);
                        player.getPageManager().openCustomPage(ref, s, page);
                    }
                }
        );
    }

    public static final class PipeFilterUIEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_MODE = "Mode";

        static final String ACTION_SET_MODE = "SetMode";
        static final String ACTION_EDIT_ITEMS = "EditItems";
        static final String ACTION_CLEAR = "Clear";
        static final String ACTION_BACK = "Back";

        public static final BuilderCodec<PipeFilterUIEventData> CODEC = BuilderCodec.builder(PipeFilterUIEventData.class, PipeFilterUIEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>(KEY_MODE, Codec.STRING), (d, v) -> d.mode = v, d -> d.mode)
                .add()
                .build();

        @Nullable
        String action;

        @Nullable
        String mode;
    }
}
