package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.UI.ItemSelectorHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple debug command to open the generic Item Selector UI
 * and print the final selection to chat when confirmed.
 */
public class DebugItemSelectorCommand extends AbstractPlayerCommand {

    public DebugItemSelectorCommand() {
        super("hi_item_selector", "Open the HytaleIndustries item selector test UI.");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        // For now, just show a small hard-coded list of example item IDs.
        List<String> candidates = Item.getAssetMap().getAssetMap().keySet().stream().toList();
        HytaleIndustriesPlugin.LOGGER.atInfo().log("ItemSelector candidates: " + candidates.size());

        ItemSelectorHelper.open(ref, store, candidates, null, (pRef, selected) -> {
            player.sendMessage(Message.raw("asdasd: " + selected.size() + " item(s): " + String.join(", ", selected)));
        });
    }
}