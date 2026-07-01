package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESControllerComponent;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Systems.EnergizedStorage.ESNetworkSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ESGridUIPage extends InteractiveCustomUIPage<ESGridUIPage.ESGridEventData> {
    private final int x, y, z;

    public ESGridUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl org.joml.Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ESGridEventData.CODEC);
        this.x = pos.x; this.y = pos.y; this.z = pos.z;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref,
                      @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events,
                      @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/HytaleIndustries_ESGrid.ui");
        render(ref, cmd, events, store);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref,
                                @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl ESGridEventData data) {
        if (data.action == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;
        var chunkStore = world.getChunkStore().getStore();

        ESControllerComponent controller = ESNetworkSystem.findController(
                world, chunkStore, x, y, z,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType(),
                HytaleIndustriesPlugin.INSTANCE.getEsControllerType());
        if (controller == null || !controller.networkOnline) return;

        List<ESDiskHousingComponent> housings = ESNetworkSystem.findDiskHousings(
                world, chunkStore, x, y, z,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType(),
                HytaleIndustriesPlugin.INSTANCE.getEsDiskHousingType());

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (ESGridEventData.ACTION_EXTRACT.equals(data.action) && data.itemId != null) {
            int amount = data.isShift() ? resolveMaxStack(data.itemId) : 1;
            for (ESDiskHousingComponent h : housings) {
                List<ItemStack> got = h.extractItem(data.itemId, amount);
                for (ItemStack extracted : got) {
                    Player.giveItem(extracted, ref, store);
                    amount -= extracted.getQuantity();
                }
                if (amount <= 0) break;
            }
        } else if (ESGridEventData.ACTION_INSERT.equals(data.action) && data.getSlotIndex() >= 0) {
            CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.STORAGE_FIRST);
            if (inv == null) return;
            short slot = (short) data.getSlotIndex();
            ItemStack stack = inv.getItemStack(slot);
            if (stack == null || ItemStack.isEmpty(stack)) return;

            int amount = data.isShift() ? stack.getQuantity() : 1;
            ItemStack toInsert = stack.withQuantity(amount);
            int inserted = 0;
            for (ESDiskHousingComponent h : housings) {
                inserted += h.insertItem(toInsert.withQuantity(amount - inserted));
                if (inserted >= amount) break;
            }
            if (inserted > 0) {
                int remaining = stack.getQuantity() - inserted;
                inv.setItemStackForSlot(slot, remaining <= 0 ? ItemStack.EMPTY : stack.withQuantity(remaining));
            }
        }

        // Rebuild controller cache from housings so re-render shows current state
        controller.itemIndex.clear();
        controller.totalStored = 0;
        controller.maxCapacity = 0;
        for (ESDiskHousingComponent h : housings) {
            controller.maxCapacity += h.getTotalCapacity();
            controller.totalStored += h.getTotalStored();
            controller.itemIndex.addAll(h.aggregateItems());
        }

        // Re-render with updated state
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(ref, cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    private void render(@NonNullDecl Ref<EntityStore> ref,
                        @NonNullDecl UICommandBuilder cmd,
                        @NonNullDecl UIEventBuilder events,
                        @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            cmd.set("#StorageLabel.Text", "No world");
            return;
        }

        var chunkStore = world.getChunkStore().getStore();
        ESControllerComponent controller = ESNetworkSystem.findController(
                world, chunkStore, x, y, z,
                HytaleIndustriesPlugin.INSTANCE.getEsNetworkMemberType(),
                HytaleIndustriesPlugin.INSTANCE.getEsControllerType());

        if (controller == null || !controller.networkOnline) {
            cmd.set("#StorageLabel.Text", "Network Offline");
            return;
        }

        cmd.set("#StorageLabel.Text",
                String.format("Storage: %d / %d", controller.totalStored, controller.maxCapacity));

        // Populate network items grid
        cmd.clear("#NetworkContainer");
        List<ItemStack> items = controller.itemIndex;
        for (int i = 0; i < items.size(); i++) {
            ItemStack entry = items.get(i);
            cmd.append("#NetworkContainer", "Pages/HytaleIndustries_ESGridSlot.ui");
            String sel = "#NetworkContainer[" + i + "] ";
            cmd.set(sel + "#SlotIcon.ItemId", entry.getItemId());
            cmd.set(sel + "#SlotQty.Text", formatQty(entry.getQuantity()));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + "#SlotButton",
                    new EventData()
                            .append(ESGridEventData.KEY_ACTION, ESGridEventData.ACTION_EXTRACT)
                            .append(ESGridEventData.KEY_ITEM_ID, entry.getItemId())
                            .append(ESGridEventData.KEY_SHIFT, "false"),
                    false);
            events.addEventBinding(CustomUIEventBindingType.RightClicking, sel + "#SlotButton",
                    new EventData()
                            .append(ESGridEventData.KEY_ACTION, ESGridEventData.ACTION_EXTRACT)
                            .append(ESGridEventData.KEY_ITEM_ID, entry.getItemId())
                            .append(ESGridEventData.KEY_SHIFT, "true"),
                    false);
        }

        // Populate player inventory grid
        cmd.clear("#InventoryContainer");
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.STORAGE_FIRST);
            if (inv != null) {
                for (short s = 0; s < inv.getCapacity(); s++) {
                    ItemStack stack = inv.getItemStack(s);
                    cmd.append("#InventoryContainer", "Pages/HytaleIndustries_ESGridSlot.ui");
                    String sel = "#InventoryContainer[" + s + "] ";
                    if (stack != null && !ItemStack.isEmpty(stack)) {
                        cmd.set(sel + "#SlotIcon.ItemId", stack.getItemId());
                        cmd.set(sel + "#SlotQty.Text", formatQty(stack.getQuantity()));

                        events.addEventBinding(CustomUIEventBindingType.Activating, sel + "#SlotButton",
                                new EventData()
                                        .append(ESGridEventData.KEY_ACTION, ESGridEventData.ACTION_INSERT)
                                        .append(ESGridEventData.KEY_SLOT_INDEX, String.valueOf(s))
                                        .append(ESGridEventData.KEY_SHIFT, "false"),
                                false);
                        events.addEventBinding(CustomUIEventBindingType.RightClicking, sel + "#SlotButton",
                                new EventData()
                                        .append(ESGridEventData.KEY_ACTION, ESGridEventData.ACTION_INSERT)
                                        .append(ESGridEventData.KEY_SLOT_INDEX, String.valueOf(s))
                                        .append(ESGridEventData.KEY_SHIFT, "true"),
                                false);
                    }
                }
            }
        }
    }

    private static String formatQty(long qty) {
        if (qty >= 1_000_000) return String.format("%.1fM", qty / 1_000_000.0);
        if (qty >= 10_000) return String.format("%.1fK", qty / 1_000.0);
        if (qty >= 1_000) return String.format("%.1fK", qty / 1_000.0);
        return String.valueOf(qty);
    }

    private static int resolveMaxStack(String itemId) {
        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null) return Math.max(1, item.getMaxStack());
        } catch (Throwable ignored) {}
        return 64;
    }

    public static final class ESGridEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_ITEM_ID = "ItemId";
        static final String KEY_SLOT_INDEX = "SlotIndex";
        static final String KEY_SHIFT = "Shift";

        static final String ACTION_EXTRACT = "extract";
        static final String ACTION_INSERT = "insert";

        public String action;
        public String itemId;
        public String slotIndexStr;
        public String shiftStr;

        public int getSlotIndex() {
            try { return slotIndexStr != null ? Integer.parseInt(slotIndexStr) : -1; }
            catch (NumberFormatException e) { return -1; }
        }

        public boolean isShift() {
            return "true".equals(shiftStr);
        }

        public static final BuilderCodec<ESGridEventData> CODEC =
                BuilderCodec.builder(ESGridEventData.class, ESGridEventData::new)
                        .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                                (o, v) -> o.action = v, o -> o.action).add()
                        .append(new KeyedCodec<>(KEY_ITEM_ID, Codec.STRING),
                                (o, v) -> o.itemId = v, o -> o.itemId).add()
                        .append(new KeyedCodec<>(KEY_SLOT_INDEX, Codec.STRING),
                                (o, v) -> o.slotIndexStr = v, o -> o.slotIndexStr).add()
                        .append(new KeyedCodec<>(KEY_SHIFT, Codec.STRING),
                                (o, v) -> o.shiftStr = v, o -> o.shiftStr).add()
                        .build();
    }
}
