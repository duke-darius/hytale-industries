package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.ConnectionState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;

public class ConfigurePipeUIPage extends InteractiveCustomUIPage<ConfigurePipeUIPage.ConfigurePipeUIEventData> {


    private final int x;
    private final int y;
    private final int z;

    public ConfigurePipeUIPage(@NonNullDecl PlayerRef playerRef, @NonNullDecl Vector3i pos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfigurePipeUIEventData.CODEC);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    @Override
    public void build(
            @NonNullDecl Ref<EntityStore> ref,
            @NonNullDecl UICommandBuilder uiCommandBuilder,
            @NonNullDecl UIEventBuilder uiEventBuilder,
            @NonNullDecl Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/HytaleIndustries_ItemPipe.ui");
        render(uiCommandBuilder, uiEventBuilder, store);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl ConfigurePipeUIEventData data) {
        if (data.side == null) {
            return;
        }

        Direction dir;
        try {
            dir = Direction.valueOf(data.side);
        } catch (IllegalArgumentException ex) {
            return;
        }

        World world = store.getExternalData().getWorld();

        int x = this.x;
        int y = this.y;
        int z = this.z;

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        // Update the pipe BlockState component IN PLACE (avoid WorldChunk.setState which re-adds entities
        // and can trip a server-side NPE when BlockState.chunk is transient).
        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x, y, z);
        if (stateRef == null) {
            return;
        }

        ComponentType<ChunkStore, ItemPipeBlockState> type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            return;
        }

        ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
        if (pipe == null) {
            return;
        }

        pipe.cycleConnectionState(dir);
        chunk.markNeedsSaving();

        // Force a refresh of connected blocks visuals by invalidating this block and adjacent pipes.
        invalidateBlock(chunk, x, y, z);
        for (Direction d : Direction.values()) {
            int nx = x + d.dx;
            int ny = y + d.dy;
            int nz = z + d.dz;
            if (ny < 0 || ny >= 320) {
                continue;
            }
            BlockType bt = chunk.getWorld().getBlockType(nx, ny, nz);
            if (bt != null && bt.getState() != null && ItemPipeBlockState.STATE_ID.equals(bt.getState().getId())) {
                WorldChunk nChunk = chunk.getWorld().getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (nChunk != null) {
                    invalidateBlock(nChunk, nx, ny, nz);
                }
            }
        }

        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(commands, events, store);
        this.sendUpdate(commands, events, false);
    }

    private void render(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();

        int x = this.x;
        int y = this.y;
        int z = this.z;

        setNeighborSlot(cmd, world, x, y, z, Direction.North, "#NorthBlockButton", "#NorthBlock");
        setNeighborSlot(cmd, world, x, y, z, Direction.South, "#SouthBlockButton", "#SouthBlock");
        setNeighborSlot(cmd, world, x, y, z, Direction.West, "#WestBlockButton", "#WestBlock");
        setNeighborSlot(cmd, world, x, y, z, Direction.East, "#EastBlockButton", "#EastBlock");
        setNeighborSlot(cmd, world, x, y, z, Direction.Up, "#UpBlockButton", "#UpBlock");
        setNeighborSlot(cmd, world, x, y, z, Direction.Down, "#DownBlockButton", "#DownBlock");

        // Color the border/background for each side based on the current side config.
        ItemPipeBlockState pipe = getPipeState(world, x, y, z);
        setSideBackground(cmd, pipe, Direction.North, "#NorthBlockBorder");
        setSideBackground(cmd, pipe, Direction.South, "#SouthBlockBorder");
        setSideBackground(cmd, pipe, Direction.West, "#WestBlockBorder");
        setSideBackground(cmd, pipe, Direction.East, "#EastBlockBorder");
        setSideBackground(cmd, pipe, Direction.Up, "#UpBlockBorder");
        setSideBackground(cmd, pipe, Direction.Down, "#DownBlockBorder");

        // Make each button clickable: cycle Default -> Extract -> None.
        bindSlot(events, "#NorthBlockButton", Direction.North);
        bindSlot(events, "#SouthBlockButton", Direction.South);
        bindSlot(events, "#WestBlockButton", Direction.West);
        bindSlot(events, "#EastBlockButton", Direction.East);
        bindSlot(events, "#UpBlockButton", Direction.Up);
        bindSlot(events, "#DownBlockButton", Direction.Down);
    }

    private static void bindSlot(@NonNullDecl UIEventBuilder events, @NonNullDecl String selector, @NonNullDecl Direction dir) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData().append(ConfigurePipeUIEventData.KEY_SIDE, dir.name()),
                false
        );
    }

    private static final String COLOR_DEFAULT = "#007BFF";
    private static final String COLOR_EXTRACT = "#FFA500";
    private static final String COLOR_NONE = "#808080";

    private static void setSideBackground(
            @NonNullDecl UICommandBuilder cmd,
            @Nullable ItemPipeBlockState pipe,
            @NonNullDecl Direction dir,
            @NonNullDecl String borderSelector
    ) {
        String color = COLOR_DEFAULT;
        if (pipe != null) {
            ConnectionState state = pipe.getConnectionState(dir);
            if (state == ConnectionState.Extract) {
                color = COLOR_EXTRACT;
            } else if (state == ConnectionState.None) {
                color = COLOR_NONE;
            }
        }

        cmd.set(borderSelector + ".Background", color);
    }

    @Nullable
    private static ItemPipeBlockState getPipeState(@NonNullDecl World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(x, y, z);
        if (stateRef == null) {
            return null;
        }

        ComponentType<ChunkStore, ItemPipeBlockState> type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            return null;
        }

        return stateRef.getStore().getComponent(stateRef, type);
    }

    private static void invalidateBlock(@NonNullDecl WorldChunk chunk, int x, int y, int z) {
        int id = chunk.getBlock(x, y, z);
        if (id == 0) {
            return;
        }
        BlockType bt = chunk.getBlockType(x, y, z);
        if (bt == null) {
            return;
        }
        int rot = chunk.getRotationIndex(x, y, z);
        int filler = chunk.getFiller(x, y, z);

        // 64 = invalidate, 256 = perform block update, 4 = no particles, 2 = don't touch state, 8/16 = no filler ops.
        int settings = 64 | 256 | 4 | 2 | 8 | 16;
        chunk.setBlock(x, y, z, id, bt, rot, filler, settings);
    }

    private static void setNeighborSlot(
            @NonNullDecl UICommandBuilder cmd,
            @NonNullDecl World world,
            int x,
            int y,
            int z,
            @NonNullDecl Direction dir,
            @NonNullDecl String buttonSelector,
            @NonNullDecl String slotSelector
    ) {
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        int nz = z + dir.dz;

        // Hide out-of-bounds.
        if (ny < 0 || ny >= 320) {
            cmd.set(buttonSelector + ".Visible", false);
            return;
        }

        cmd.set(buttonSelector + ".Visible", true);
        cmd.set(slotSelector + ".Visible", true);

        var blockId = world.getBlock(nx, ny, nz);
        if (blockId == 0) {
            // Keep slot clickable, just clear its contents.
            cmd.set(slotSelector + ".ItemId", "");
            cmd.set(slotSelector + ".Quantity", 0);
            return;
        }

        var blockType = world.getBlockType(nx, ny, nz);
        if (blockType == null || blockType.getId() == null) {
            cmd.set(slotSelector + ".ItemId", "");
            cmd.set(slotSelector + ".Quantity", 0);
            return;
        }

        // In Hytale, ItemSlot expects an ItemId. BlockType ids are backed by an Item, so this works in practice.
        cmd.set(slotSelector + ".ItemId", blockType.getId());
        cmd.set(slotSelector + ".Quantity", 1);
    }

    public static final class ConfigurePipeUIEventData {
        static final String KEY_SIDE = "Side";

        public static final BuilderCodec<ConfigurePipeUIEventData> CODEC = BuilderCodec.builder(ConfigurePipeUIEventData.class, ConfigurePipeUIEventData::new)
                .append(new KeyedCodec<>(KEY_SIDE, Codec.STRING), (d, v) -> d.side = v, d -> d.side)
                .add()
                .build();

        @Nullable
        private String side;
    }
}


