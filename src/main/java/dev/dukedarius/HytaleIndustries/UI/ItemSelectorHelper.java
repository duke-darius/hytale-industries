package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for opening the ItemSelectorUIPage and receiving the result.
 *
 * Typical usage from an interaction or UI handler:
 * <pre>
 *   ItemSelectorHelper.open(
 *       playerEntityRef,
 *       store,
 *       allItemIds,
 *       preselectedIds,
 *       (playerRef, selected) -> { ... handle result ... }
 *   );
 * </pre>
 */
public final class ItemSelectorHelper {

    private ItemSelectorHelper() {
    }

    /**
     * Callback invoked when the user confirms their selection.
     */
    public interface SelectionCallback {
        void onSelection(@Nonnull PlayerRef playerRef, @Nonnull List<String> selectedItemIds);
    }

    private static final Map<UUID, SelectionCallback> CALLBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<String>> LAST_SELECTION = new ConcurrentHashMap<>();

    /**
     * Open the item selector UI for a player.
     *
     * @param playerEntityRef the player's entity ref (from InteractionContext.getEntity()).
     * @param store           the entity store.
     * @param candidateItemIds list of candidate item ids to display.
     * @param preselected      optional collection of item ids that should start selected.
     * @param callback         optional callback to invoke when the player confirms their selection.
     */
    public static void open(@Nonnull Ref<EntityStore> playerEntityRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull List<String> candidateItemIds,
                            @Nullable Collection<String> preselected,
                            @Nullable SelectionCallback callback) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        if (callback != null) {
            CALLBACKS.put(playerId, callback);
        } else {
            CALLBACKS.remove(playerId);
        }

        ItemSelectorUIPage page = new ItemSelectorUIPage(playerRef, candidateItemIds, preselected);
        player.getPageManager().openCustomPage(playerEntityRef, store, page);

    }

    /**
     * Internal hook used by ItemSelectorUIPage when the user presses Confirm.
     */
    static void onSelectionConfirmed(@Nonnull PlayerRef playerRef, @Nonnull List<String> selectedIds) {
        UUID playerId = playerRef.getUuid();
        LAST_SELECTION.put(playerId, Collections.unmodifiableList(new ArrayList<>(selectedIds)));

        SelectionCallback cb = CALLBACKS.remove(playerId);
        if (cb != null) {
            try {
                cb.onSelection(playerRef, selectedIds);
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t).log("ItemSelectorHelper: selection callback threw");
            }
        }
    }

    /**
     * Get the last confirmed selection for a player, if any.
     */
    @Nullable
    public static List<String> getLastSelection(@Nonnull UUID playerId) {
        List<String> v = LAST_SELECTION.get(playerId);
        return v != null ? new ArrayList<>(v) : null;
    }

    /**
     * Clear any stored selection and callback for a player.
     */
    public static void clearSelection(@Nonnull UUID playerId) {
        LAST_SELECTION.remove(playerId);
        CALLBACKS.remove(playerId);
    }
}
