package dev.dukedarius.HytaleIndustries.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.UI.ChunkLoaderUIPage;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class OpenChunkLoaderInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<OpenChunkLoaderInteraction> CODEC = BuilderCodec.builder(
                    OpenChunkLoaderInteraction.class,
                    OpenChunkLoaderInteraction::new,
                    SimpleBlockInteraction.CODEC
            )
            .documentation("Chunk Loader interaction")
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
        Store<EntityStore> store = ref.getStore();

        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            interactionContext.getState().state = InteractionState.Failed;
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        // Don't open if a custom page is already open.
        if (playerComponent.getPageManager().getCustomPage() != null) {
            interactionContext.getState().state = InteractionState.Skip;
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        }
        if (chunk == null) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }
        var entity = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        var loaderType = dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.INSTANCE.getChunkLoaderComponentType();
        if (entity == null || loaderType == null || entity.getStore().getComponent(entity, loaderType) == null) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            interactionContext.getState().state = InteractionState.Failed;
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        var page = new ChunkLoaderUIPage(playerRef, pos);
        playerComponent.getPageManager().openCustomPage(ref, store, page);
    }

    @Override
    protected void simulateInteractWithBlock(
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NullableDecl ItemStack itemStack,
            @NonNullDecl World world,
            @NonNullDecl Vector3i pos
    ) {
        // no-op
    }
}
