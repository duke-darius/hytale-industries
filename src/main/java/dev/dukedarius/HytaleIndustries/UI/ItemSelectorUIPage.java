package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ItemSelectorUIPage extends InteractiveCustomUIPage<ItemSelectorUIPage.UIEventData> {
    private final List<String> allItems;
    private final LinkedHashSet<String> selected;
    private String searchText = "";

    public ItemSelectorUIPage(@Nonnull PlayerRef playerRef,
                              @Nonnull Collection<String> items,
                              @Nullable Collection<String> preselected) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, UIEventData.CODEC);

        List<String> tmp = new ArrayList<>(items.size());
        for (String id : items) {
            if (id == null) continue;
            String norm = id.trim();
            if (!norm.isEmpty()) {
                tmp.add(norm);
            }
        }
        tmp.sort(String.CASE_INSENSITIVE_ORDER);
        this.allItems = Collections.unmodifiableList(tmp);

        this.selected = new LinkedHashSet<>();
        if (preselected != null) {
            for (String id : preselected) {
                if (id != null && this.allItems.contains(id)) {
                    this.selected.add(id);
                }
            }
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_ItemSelector.ui");
        render(cmd, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        HytaleIndustriesPlugin.LOGGER.atInfo().log("ItemSelectorUI: handleDataEvent action=" + data.action);
        boolean changed = false;

        if(data.action == null) {
            return;
        }

        if(data.action == ItemSelectorUIPage.UIEventData.ACTION_CONFIRM) {

        }
        if(data.action == ItemSelectorUIPage.UIEventData.ACTION_CANCEL) {

        }
        if(data.action.equals(UIEventData.ACTION_TOGGLE_INDEX)) {
            String id = data.id;
            if(id == null) return;
            if(selected.contains(id)) {
                selected.remove(id);
            } else {
                selected.add(id);
            }
            changed = true;
        }
        if(data.action.equals(UIEventData.ACTION_SET_SEARCH)){
            this.searchText = data.searchText;
            changed = true;
        }

        HytaleIndustriesPlugin.LOGGER.atInfo().log("ItemSelectorUI: searchText=" + searchText);

        if (changed) {
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            render(cmd, events);
            sendUpdate(cmd, events, false);
        }
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
        List<String> filtered = getFiltered();
        int total = filtered.size();

//        cmd.set("#SearchBox.Value", searchText);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchBox",
                EventData.of(UIEventData.KEY_ACTION, UIEventData.ACTION_SET_SEARCH)
                        .append(ItemSelectorUIPage.UIEventData.KEY_SEARCH_TEXT, "#SearchBox.Value"),
                false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_CONFIRM), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                new EventData().append(UIEventData.KEY_ACTION, UIEventData.ACTION_CANCEL), false);

        cmd.set("#SummaryLabel.Text", String.format("Items: %d  Selected: %d", total, selected.size()));

        cmd.clear("#RowsContainer");

        for (int i = 0; i < total; i++) {
            var itemId = filtered.get(i);
            cmd.append("#RowsContainer", "Pages/HytaleIndustries_ItemSelectorSlot.ui");

            var item = Item.getAssetMap().getAsset(itemId);
            if(item == null) continue;
            String selector = "#RowsContainer[" + i + "] ";
            cmd.set(selector + "#ItemSlot.ItemId", itemId);
            if(selected.contains(itemId)) {
                cmd.set(selector + "#ItemBorder.Background", "#05DC00");
            }

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + "#ItemButton",
                    new EventData()
                            .append(UIEventData.KEY_ACTION, UIEventData.ACTION_TOGGLE_INDEX)
                            .append(UIEventData.KEY_INDEX, filtered.get(i)),
                    false
            );

        }

    }

    private List<String> getFiltered() {
        if (searchText == null || searchText.isEmpty()) {
            return allItems;
        }
        String needle = searchText.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String id : allItems) {
            if (id.toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(id);
            }
        }
        return out;
    }

    private void onConfirm(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        if (playerRef != null) {
            try {
                ItemSelectorHelper.onSelectionConfirmed(playerRef, new ArrayList<>(selected));
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("ItemSelector: selection callback failed");
            }
        }
        if (player != null) {
            closePage(ref, store);
        }
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        try {
            player.getPageManager().handleEvent(ref, store, new CustomPageEvent(CustomPageEventType.Dismiss, ""));
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("ItemSelector: failed to dismiss page");
        }
    }

    public static final class UIEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_SEARCH_TEXT = "@Search";
        static final String KEY_INDEX = "Index";

        static final String ACTION_SET_SEARCH = "SetSearch";
        static final String ACTION_CONFIRM = "Confirm";
        static final String ACTION_CANCEL = "Cancel";
        static final String ACTION_TOGGLE_INDEX = "Toggle";

        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>(KEY_SEARCH_TEXT, Codec.STRING), (d, v) -> d.searchText = v, d -> d.searchText)
                .add()
                .append(new KeyedCodec<>(KEY_INDEX, Codec.STRING), (d, v) -> d.id = v, d -> d.id)
                .add()
                .build();

        @Nullable
        String action;

        @Nullable
        String searchText;

        @Nullable
        String id;
    }
}
