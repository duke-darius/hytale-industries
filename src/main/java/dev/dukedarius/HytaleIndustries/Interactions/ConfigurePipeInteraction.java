package dev.dukedarius.HytaleIndustries.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.UI.ConfigurePipeUIPage;
import dev.dukedarius.HytaleIndustries.Pipes.PipeSelectionStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;


public class ConfigurePipeInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<ConfigurePipeInteraction> CODEC = BuilderCodec.builder(
            ConfigurePipeInteraction.class,
            ConfigurePipeInteraction::new,
            SimpleBlockInteraction.CODEC
    )
    .documentation("Opens the pipe configuration UI")
    .build();

    @Override
    protected void interactWithBlock(
            @NonNullDecl World world,
            @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NullableDecl ItemStack itemStack,
            @NonNullDecl Vector3i pos,
            @NonNullDecl CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = interactionContext.getEntity();
        var store = ref.getStore();
        var player = commandBuffer.getComponent(ref, Player.getComponentType());
        var playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());

        if(player == null || playerRef == null){
            return;
        }

        if(player.getPageManager().getCustomPage() != null){
            return;
        }

        var state = world.getState(pos.x, pos.y, pos.z, true);
        if(!(state instanceof ItemPipeBlockState)){
            return;
        }

        var page = new ConfigurePipeUIPage(playerRef, pos);
        PipeSelectionStore.set(playerRef.getUuid(), pos);

        var pageManager = player.getPageManager();

        pageManager.openCustomPage(ref, store, page);
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType interactionType, @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl Vector3i vector3i) {

    }
}
