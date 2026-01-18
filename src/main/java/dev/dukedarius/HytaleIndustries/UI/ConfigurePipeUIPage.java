package dev.dukedarius.HytaleIndustries.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlocksUtil;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.ConnectionState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;

public class ConfigurePipeUIPage extends InteractiveCustomUIPage<ConfigurePipeUIPage.ConfigurePipeUIEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();


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
            dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore.clear(x, y, z);
            return;
        }

        // Mutate the BlockState component safely and persist it.
        int lx = x & 31;
        int lz = z & 31;

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
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
        stateRef.getStore().replaceComponent(stateRef, type, pipe);
        chunk.markNeedsSaving();

        LOGGER.atInfo().log("Pipe UI changed " + dir + " at (" + x + "," + y + "," + z + ") state sideConfig now " + pipe.getConnectionState(dir));
        // Refresh just this pipe's connected-block model, while restoring its sideConfig afterward.
        refreshSinglePipeWithRestore(world, x, y, z);
        // Do not force connected-block swap here; models will update on a natural block update.

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

        // Color the border/background based on the current pipe BlockState component.
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

        int lx = x & 31;
        int lz = z & 31;

        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef == null) {
            dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore.clear(x, y, z);
            return null;
        }

        ComponentType<ChunkStore, ItemPipeBlockState> type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
        if (type == null) {
            return null;
        }
        ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
        if (pipe == null) {
            dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore.clear(x, y, z);
            return null;
        }
        return pipe;
    }

    private static void refreshSinglePipeWithRestore(@NonNullDecl World world, int x, int y, int z) {
        Vector3i pos = new Vector3i(x, y, z);
        Vector3i placementNormal = new Vector3i(0, 1, 0);

        // Save sideConfig for this pipe.
        int savedCfg = dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore.get(x, y, z);
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }
        int lx = x & 31;
        int lz = z & 31;

        // Force a full setBlock with settings to trigger connected-block evaluation.
        BlockType current = chunk.getBlockType(lx, y, lz);
        if (current != null) {
            int blockId = chunk.getBlock(lx, y, lz);
            int rotIndex = chunk.getRotationIndex(lx, y, lz);
            int settings = 64 | 256 | 4 | 2; // invalidate, block update, no particles, keep state
            boolean changed = chunk.setBlock(lx, y, lz, blockId, current, rotIndex, 0, settings);
            LOGGER.atInfo().log("Force WorldChunk.setBlock at " + pos + " changed=" + changed + " rot=" + rotIndex);

            if (!changed) {
                // Compute desired variant and apply directly.
                var desiredOpt = ConnectedBlocksUtil.getDesiredConnectedBlockType(world, pos, current, rotIndex, new Vector3i(0, 1, 0), false);
                if (desiredOpt.isPresent()) {
                    var r = desiredOpt.get();
                    int newId = BlockType.getAssetMap().getIndex(r.blockTypeKey());
                    if (newId != Integer.MIN_VALUE) {
                        BlockType newType = BlockType.getAssetMap().getAsset(newId);
                        int newRot = r.rotationIndex();
                        boolean forced = chunk.setBlock(lx, y, lz, newId, newType, newRot, 0, settings);
                        LOGGER.atInfo().log("Force apply desired variant at " + pos + " type=" + r.blockTypeKey() + " rot=" + newRot + " changed=" + forced);
                    }
                }
            }
        }

        // Restore saved sideConfig to this pipe (in case block swap recreated BlockState) and put back in store.
        Ref<ChunkStore> stateRef = chunk.getBlockComponentEntity(lx, y, lz);
        if (stateRef != null) {
            ComponentType<ChunkStore, ItemPipeBlockState> type = BlockStateModule.get().getComponentType(ItemPipeBlockState.class);
            if (type != null) {
                ItemPipeBlockState pipe = stateRef.getStore().getComponent(stateRef, type);
                if (pipe != null) {
                    pipe.setRawSideConfig(savedCfg);
                    stateRef.getStore().replaceComponent(stateRef, type, pipe);
                }
            }
        }
        dev.dukedarius.HytaleIndustries.Pipes.PipeSideConfigStore.set(x, y, z, savedCfg);
    }

    private static void applyConnectedResult(
            @NonNullDecl World world,
            @NonNullDecl Vector3i placementNormal,
            @NonNullDecl Vector3i pos,
            @NonNullDecl ConnectedBlocksUtil.ConnectedBlockResult result
    ) {
        // Apply main block.
        int blockId = BlockType.getAssetMap().getIndex(result.blockTypeKey());
        if (blockId == Integer.MIN_VALUE) {
            return;
        }

        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            return;
        }

        RotationTuple rot = RotationTuple.get(result.rotationIndex());
        ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(blockId, rot, pos, placementNormal, chunk, chunk.getBlockChunk());

        // Apply any additional blocks (some connected-block rulesets place extra blocks).
        var extras = result.getAdditionalConnectedBlocks();
        if (extras == null || extras.isEmpty()) {
            return;
        }

        for (var e : extras.entrySet()) {
            Vector3i p = e.getKey();
            var pair = e.getValue();
            if (p == null || pair == null) {
                continue;
            }

            String key = pair.left();
            int rIndex = pair.rightInt();

            int id = BlockType.getAssetMap().getIndex(key);
            if (id == Integer.MIN_VALUE) {
                continue;
            }

            WorldChunk c = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(p.x, p.z));
            if (c == null) {
                continue;
            }

            RotationTuple rt = RotationTuple.get(rIndex);
            ConnectedBlocksUtil.setConnectedBlockAndNotifyNeighbors(id, rt, p, placementNormal, c, c.getBlockChunk());
        }
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
            cmd.set(slotSelector + ".Visible", false);
            cmd.set(slotSelector + ".ItemId", "");
            cmd.set(slotSelector + ".Quantity", 0);
            return;
        }

        var blockType = world.getBlockType(nx, ny, nz);
        String id = (blockType != null) ? blockType.getId() : null;

        if (id == null) {
            cmd.set(slotSelector + ".ItemId", "");
            cmd.set(slotSelector + ".Quantity", 0);
            return;
        }
        // Normalize: remove leading '*' (variant indicator) and strip state/animation suffix.
        if (id.startsWith("*")) {
            id = id.substring(1);
        }
        int stateIdx = id.indexOf("_State_");
        if (stateIdx > 0) {
            id = id.substring(0, stateIdx);
        }



        // In Hytale, ItemSlot expects an ItemId. BlockType ids are backed by an Item, so this works in practice.
        cmd.set(slotSelector + ".ItemId", id);
//        cmd.set(slotSelector + ".ItemId", blockType.getId());
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


