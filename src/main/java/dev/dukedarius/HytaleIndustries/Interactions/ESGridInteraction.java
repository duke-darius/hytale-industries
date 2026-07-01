package dev.dukedarius.HytaleIndustries.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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
import dev.dukedarius.HytaleIndustries.UI.ESGridUIPage;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3i;

public class ESGridInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<ESGridInteraction> CODEC = BuilderCodec.builder(
            ESGridInteraction.class, ESGridInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens the ES Grid terminal UI").build();

    @Override
    protected void interactWithBlock(
            @NonNullDecl World world, @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
            @NonNullDecl InteractionType interactionType, @NonNullDecl InteractionContext ctx,
            @NullableDecl ItemStack itemStack, @NonNullDecl Vector3i pos,
            @NonNullDecl CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = ctx.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) { ctx.getState().state = InteractionState.Failed; return; }
        if (player.getPageManager().getCustomPage() != null) { ctx.getState().state = InteractionState.Skip; return; }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) { ctx.getState().state = InteractionState.Failed; return; }

        var page = new ESGridUIPage(playerRef, pos);
        player.getPageManager().openCustomPageWithWindows(ref, ref.getStore(), page);
    }

    @Override
    protected void simulateInteractWithBlock(
            @NonNullDecl InteractionType t, @NonNullDecl InteractionContext ctx,
            @NullableDecl ItemStack s, @NonNullDecl World w, @NonNullDecl Vector3i p) {
        ctx.getState().state = InteractionState.Finished;
    }
}
