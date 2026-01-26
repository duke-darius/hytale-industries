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
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.SmallBatteryBlockState;
import dev.dukedarius.HytaleIndustries.UI.SmallBatteryUIPage;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class OpenSmallBatteryInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<OpenSmallBatteryInteraction> CODEC = BuilderCodec.builder(
                    OpenSmallBatteryInteraction.class,
                    OpenSmallBatteryInteraction::new,
                    SimpleBlockInteraction.CODEC
            )
            .documentation("Opens the small battery UI")
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

        BlockState state = world.getState(pos.x, pos.y, pos.z, true);
        if (!(state instanceof SmallBatteryBlockState)) {
            interactionContext.getState().state = InteractionState.Skip;
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            interactionContext.getState().state = InteractionState.Failed;
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        var page = new SmallBatteryUIPage(playerRef, pos);
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
