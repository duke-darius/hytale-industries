package dev.dukedarius.HytaleIndustries.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3i;

public class ESDiskHousingInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<ESDiskHousingInteraction> CODEC = BuilderCodec.builder(
            ESDiskHousingInteraction.class, ESDiskHousingInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens the ES Disk Housing slot UI").build();

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

        WorldChunk chunk = world.getChunkIfInMemory(
                com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) chunk = world.getChunkIfLoaded(
                com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) { ctx.getState().state = InteractionState.Skip; return; }

        var blockEntity = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (blockEntity == null) { ctx.getState().state = InteractionState.Skip; return; }

        ESDiskHousingComponent housing = blockEntity.getStore().getComponent(
                blockEntity, HytaleIndustriesPlugin.INSTANCE.getEsDiskHousingType());
        if (housing == null) { ctx.getState().state = InteractionState.Skip; return; }

        ContainerWindow win = new ContainerWindow(housing.diskSlots);
        player.getPageManager().setPageWithWindows(ref, ref.getStore(),
                com.hypixel.hytale.protocol.packets.interface_.Page.Inventory, true, win);
    }

    @Override
    protected void simulateInteractWithBlock(
            @NonNullDecl InteractionType t, @NonNullDecl InteractionContext ctx,
            @NullableDecl ItemStack s, @NonNullDecl World w, @NonNullDecl Vector3i p) {
        ctx.getState().state = InteractionState.Finished;
    }
}
