package dev.dukedarius.HytaleIndustries.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Utils.CacheDisplayManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class OpenBasicItemCacheInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<OpenBasicItemCacheInteraction> CODEC = BuilderCodec.builder(
                    OpenBasicItemCacheInteraction.class,
                    OpenBasicItemCacheInteraction::new,
                    SimpleBlockInteraction.CODEC
            )
            .documentation("Drawer-style insert/extract for the Basic Item Cache")
            .build();

    @Override
    protected void interactWithBlock(
            @NonNullDecl World world,
            @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NullableDecl ItemStack itemStack,
            @NonNullDecl Vector3i pos,
            @NonNullDecl CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        WorldChunk chunk = world.getChunkIfInMemory(
                com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null)
            chunk = world.getChunkIfLoaded(
                    com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        var blockEntity = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (blockEntity == null) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        BasicItemCacheComponent cache = blockEntity.getStore().getComponent(
                blockEntity, HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType());
        if (cache == null) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        ensureContainer(cache);

        boolean sneaking = false;
        MovementStatesComponent movement = commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType());
        if (movement != null) {
            sneaking = movement.getMovementStates().crouching;
        }

        if (interactionType == InteractionType.Primary) {
            // Left click — withdraw
            if (sneaking) {
                extractStack(ref, commandBuffer, cache);
            } else {
                extractOne(ref, commandBuffer, cache);
            }
        } else {
            // Right click / Use — deposit
            ItemStack held = interactionContext.getHeldItem();
            boolean hasHeld = held != null && !ItemStack.isEmpty(held);
            if (hasHeld) {
                if (sneaking) {
                    insertAllFromHand(interactionContext, cache, held);
                } else {
                    insertOneFromHand(interactionContext, cache, held);
                }
            } else {
                interactionContext.getState().state = InteractionState.Skip;
                return;
            }
        }

        reconcileSlot(cache);
        blockEntity.getStore().replaceComponent(blockEntity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType(), cache);

        sendCacheNotification(commandBuffer, ref, cache);

        int yawIndex = 0;
        if (chunk != null) {
            yawIndex = chunk.getRotationIndex(pos.x & 31, pos.y, pos.z & 31) & 3;
        }
        CacheDisplayManager.updateFromInteraction(world, pos,
                cache.cachedItemId, cache.cachedCount, yawIndex);
    }

    // --- deposit (right click) ---

    private void insertOneFromHand(InteractionContext ctx, BasicItemCacheComponent cache, ItemStack held) {
        insertFromHand(ctx, cache, held, 1);
    }

    private void insertAllFromHand(InteractionContext ctx, BasicItemCacheComponent cache, ItemStack held) {
        insertFromHand(ctx, cache, held, held.getQuantity());
    }

    private void insertFromHand(InteractionContext ctx, BasicItemCacheComponent cache,
                                ItemStack held, int maxToInsert) {
        String heldId = held.getItemId();

        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = heldId;
            int baseMax = resolveMaxStack(heldId);
            cache.maxCount = 64L * baseMax;
            cache.slot.setLockedItemId(heldId);
            cache.slot.setMaxStack((int) Math.min(cache.maxCount, Integer.MAX_VALUE));
            cache.cachedCount = 0;
        }

        if (!heldId.equals(cache.cachedItemId)) {
            ctx.getState().state = InteractionState.Skip;
            return;
        }

        long space = cache.maxCount - cache.cachedCount;
        if (space <= 0) {
            ctx.getState().state = InteractionState.Skip;
            return;
        }

        int toInsert = (int) Math.min(Math.min(held.getQuantity(), maxToInsert), space);
        cache.cachedCount += toInsert;

        int remaining = held.getQuantity() - toInsert;
        ItemContainer heldContainer = ctx.getHeldItemContainer();
        byte heldSlot = ctx.getHeldItemSlot();
        if (heldContainer != null) {
            if (remaining <= 0) {
                heldContainer.setItemStackForSlot(heldSlot, ItemStack.EMPTY);
            } else {
                heldContainer.setItemStackForSlot(heldSlot, held.withQuantity(remaining));
            }
        }
    }

    // --- withdraw (left click) ---

    private void extractOne(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer,
                            BasicItemCacheComponent cache) {
        extractItems(ref, commandBuffer, cache, 1);
    }

    private void extractStack(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer,
                              BasicItemCacheComponent cache) {
        int baseMax = cache.cachedItemId != null ? resolveMaxStack(cache.cachedItemId) : 64;
        extractItems(ref, commandBuffer, cache, baseMax);
    }

    private void extractItems(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer,
                              BasicItemCacheComponent cache, int maxAmount) {
        if (cache.cachedItemId == null || cache.cachedCount <= 0) return;

        int toExtract = (int) Math.min(cache.cachedCount, maxAmount);
        cache.cachedCount -= toExtract;

        Player.giveItem(new ItemStack(cache.cachedItemId, toExtract), ref, commandBuffer);

        if (cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0;
            cache.maxCount = 0;
        }
    }

    // --- shared ---

    private static void sendCacheNotification(CommandBuffer<EntityStore> commandBuffer,
                                              Ref<EntityStore> ref,
                                              BasicItemCacheComponent cache) {
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), "Item Cache: Empty");
        } else {
            ItemStack display = new ItemStack(cache.cachedItemId, 1);
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw(String.format("Item Cache: %d / %d", cache.cachedCount, cache.maxCount)),
                    null, display.toPacket(), NotificationStyle.fromValue(0));
        }
    }

    private static int resolveMaxStack(String itemId) {
        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null) {
                int m = item.getMaxStack();
                return m > 0 ? m : 64;
            }
        } catch (Throwable ignored) {}
        return 64;
    }

    private static void ensureContainer(BasicItemCacheComponent cache) {
        if (cache.slot == null || cache.slot.getCapacity() <= 0) {
            cache.slot = new dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer(
                    (short) 1, 1024);
        }
    }

    private static void reconcileSlot(BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0;
            cache.maxCount = 0;
            cache.lastExposedCount = 0;
            cache.slot.setMaxStack(1024);
            cache.slot.setLockedItemId(null);
            cache.slot.setItemStackForSlot((short) 0, ItemStack.EMPTY, false);
            return;
        }
        int baseMax = resolveMaxStack(cache.cachedItemId);
        int desired = (int) Math.min(cache.cachedCount, baseMax);
        cache.slot.setMaxStack(1024);
        cache.slot.setLockedItemId(cache.cachedItemId);
        if (desired <= 0) {
            cache.slot.setItemStackForSlot((short) 0, ItemStack.EMPTY, false);
            cache.lastExposedCount = 0;
        } else {
            cache.slot.setItemStackForSlot((short) 0, new ItemStack(cache.cachedItemId, desired), false);
            cache.lastExposedCount = desired;
        }
    }

    @Override
    protected void simulateInteractWithBlock(
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NullableDecl ItemStack itemStack,
            @NonNullDecl World world,
            @NonNullDecl Vector3i pos
    ) {
        // Claim the interaction so the engine doesn't fall through to block placement
        interactionContext.getState().state = InteractionState.Finished;
    }
}
