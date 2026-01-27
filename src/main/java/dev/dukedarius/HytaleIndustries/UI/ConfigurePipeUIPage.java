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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.ConnectionState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState.Direction;
import dev.dukedarius.HytaleIndustries.Pipes.SideConfigurableConduit;
import dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent;
import dev.dukedarius.HytaleIndustries.Components.UpdatePowerCableComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
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

        SideConfigurableConduit conduit = null;
        
        // Check if it's an ECS BasicItemPipe first
        ComponentType<ChunkStore, BasicItemPipeComponent> basicPipeType = HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
        BasicItemPipeComponent basicPipe = stateRef.getStore().getComponent(stateRef, basicPipeType);
        
        if (basicPipe != null) {
            // Handle BasicItemPipe component
            Vector3i dirVec = directionToVector(dir);
            BasicItemPipeComponent.ConnectionState currentState = basicPipe.getConnectionState(dirVec);
            BasicItemPipeComponent.ConnectionState nextState = switch (currentState) {
                case Default -> BasicItemPipeComponent.ConnectionState.Extract;
                case Extract -> BasicItemPipeComponent.ConnectionState.None;
                case None -> BasicItemPipeComponent.ConnectionState.Default;
            };
            basicPipe.setConnectionState(dirVec, nextState, true); // Mark as manual
            stateRef.getStore().replaceComponent(stateRef, basicPipeType, basicPipe);
            
            // Mark for visual update
            ComponentType<ChunkStore, UpdatePipeComponent> updateType = HytaleIndustriesPlugin.INSTANCE.getUpdatePipeComponentType();
            stateRef.getStore().ensureComponent(stateRef, updateType);
            
            // Propagate None and Default states to adjacent pipe
            if (nextState == BasicItemPipeComponent.ConnectionState.None || nextState == BasicItemPipeComponent.ConnectionState.Default) {
                int nx = x + dir.dx;
                int ny = y + dir.dy;
                int nz = z + dir.dz;
                WorldChunk neighborChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (neighborChunk == null) {
                    neighborChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                }
                if (neighborChunk != null) {
                    int nlx = nx & 31;
                    int nlz = nz & 31;
                    Ref<ChunkStore> neighborRef = neighborChunk.getBlockComponentEntity(nlx, ny, nlz);
                    if (neighborRef != null) {
                        BasicItemPipeComponent neighborPipe = neighborRef.getStore().getComponent(neighborRef, basicPipeType);
                        if (neighborPipe != null) {
                            // Set neighbor's opposite side to the same state
                            Vector3i oppositeDir = new Vector3i(-dirVec.x, -dirVec.y, -dirVec.z);
                            neighborPipe.setConnectionState(oppositeDir, nextState, true); // Mark as manual to prevent auto-restore
                            neighborRef.getStore().replaceComponent(neighborRef, basicPipeType, neighborPipe);
                            neighborRef.getStore().ensureComponent(neighborRef, updateType);
                            neighborChunk.markNeedsSaving();
                            LOGGER.atFine().log("Propagated state " + nextState + " to neighbor pipe at (" + nx + "," + ny + "," + nz + ")");
                        }
                    }
                }
            } else {
                // For Extract state, just mark neighbor for update
                int nx = x + dir.dx;
                int ny = y + dir.dy;
                int nz = z + dir.dz;
                WorldChunk neighborChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (neighborChunk == null) {
                    neighborChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                }
                if (neighborChunk != null) {
                    int nlx = nx & 31;
                    int nlz = nz & 31;
                    Ref<ChunkStore> neighborRef = neighborChunk.getBlockComponentEntity(nlx, ny, nlz);
                    if (neighborRef != null) {
                        BasicItemPipeComponent neighborPipe = neighborRef.getStore().getComponent(neighborRef, basicPipeType);
                        if (neighborPipe != null) {
                            neighborRef.getStore().ensureComponent(neighborRef, updateType);
                            LOGGER.atFine().log("Marked neighbor pipe at (" + nx + "," + ny + "," + nz + ") for update");
                        }
                    }
                }
            }
            
            chunk.markNeedsSaving();
            LOGGER.atFine().log("BasicItemPipe UI changed " + dir + " at (" + x + "," + y + "," + z + ") state now " + nextState);
            
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            render(commands, events, store);
            this.sendUpdate(commands, events, false);
            return;
        }
        
        // Check if it's an ECS BasicPowerCable
        ComponentType<ChunkStore, BasicPowerCableComponent> basicCableType = HytaleIndustriesPlugin.INSTANCE.getBasicPowerCableComponentType();
        BasicPowerCableComponent basicCable = stateRef.getStore().getComponent(stateRef, basicCableType);
        
        if (basicCable != null) {
            // Handle BasicPowerCable component
            Vector3i dirVec = directionToVector(dir);
            BasicPowerCableComponent.ConnectionState currentState = basicCable.getConnectionState(dirVec);
            BasicPowerCableComponent.ConnectionState nextState = switch (currentState) {
                case Default -> BasicPowerCableComponent.ConnectionState.Extract;
                case Extract -> BasicPowerCableComponent.ConnectionState.None;
                case None -> BasicPowerCableComponent.ConnectionState.Default;
            };
            basicCable.setConnectionState(dirVec, nextState, true); // Mark as manual
            stateRef.getStore().replaceComponent(stateRef, basicCableType, basicCable);
            
            // Mark this cable for visual update
            ComponentType<ChunkStore, UpdatePowerCableComponent> updateCableType = HytaleIndustriesPlugin.INSTANCE.getUpdatePowerCableComponentType();
            stateRef.getStore().ensureComponent(stateRef, updateCableType);
            
            // Propagate None and Default states to adjacent cable
            if (nextState == BasicPowerCableComponent.ConnectionState.None || nextState == BasicPowerCableComponent.ConnectionState.Default) {
                int nx = x + dir.dx;
                int ny = y + dir.dy;
                int nz = z + dir.dz;
                WorldChunk neighborChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (neighborChunk == null) {
                    neighborChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                }
                if (neighborChunk != null) {
                    int nlx = nx & 31;
                    int nlz = nz & 31;
                    Ref<ChunkStore> neighborRef = neighborChunk.getBlockComponentEntity(nlx, ny, nlz);
                    if (neighborRef != null) {
                        BasicPowerCableComponent neighborCable = neighborRef.getStore().getComponent(neighborRef, basicCableType);
                        if (neighborCable != null) {
                            // Set neighbor's opposite side to the same state
                            Vector3i oppositeDir = new Vector3i(-dirVec.x, -dirVec.y, -dirVec.z);
                            neighborCable.setConnectionState(oppositeDir, nextState, true); // Mark as manual to prevent auto-restore
                            neighborRef.getStore().replaceComponent(neighborRef, basicCableType, neighborCable);
                            neighborRef.getStore().ensureComponent(neighborRef, updateCableType);
                            neighborChunk.markNeedsSaving();
                            LOGGER.atFine().log("Propagated state " + nextState + " to neighbor cable at (" + nx + "," + ny + "," + nz + ")");
                        }
                    }
                }
            } else {
                // For Extract state, just mark neighbor for update
                int nx = x + dir.dx;
                int ny = y + dir.dy;
                int nz = z + dir.dz;
                WorldChunk neighborChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
                if (neighborChunk == null) {
                    neighborChunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(nx, nz));
                }
                if (neighborChunk != null) {
                    int nlx = nx & 31;
                    int nlz = nz & 31;
                    Ref<ChunkStore> neighborRef = neighborChunk.getBlockComponentEntity(nlx, ny, nlz);
                    if (neighborRef != null) {
                        BasicPowerCableComponent neighborCable = neighborRef.getStore().getComponent(neighborRef, basicCableType);
                        if (neighborCable != null) {
                            neighborRef.getStore().ensureComponent(neighborRef, updateCableType);
                            LOGGER.atFine().log("Marked neighbor cable at (" + nx + "," + ny + "," + nz + ") for update");
                        }
                    }
                }
            }
            
            chunk.markNeedsSaving();
            LOGGER.atFine().log("BasicPowerCable UI changed " + dir + " at (" + x + "," + y + "," + z + ") state now " + nextState);
            
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            render(commands, events, store);
            this.sendUpdate(commands, events, false);
            return;
        }

        // Only ECS components are supported; deprecated BlockState-based pipes are no longer handled
        return;
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

        // Color the border/background based on the current conduit BlockState component.
        SideConfigurableConduit pipe = getConduitState(world, x, y, z);
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
            @Nullable SideConfigurableConduit conduit,
            @NonNullDecl Direction dir,
            @NonNullDecl String borderSelector
    ) {
        String color = COLOR_DEFAULT;
        if (conduit != null) {
            ConnectionState state = conduit.getConnectionState(dir);
            if (state == ConnectionState.Extract) {
                color = COLOR_EXTRACT;
            } else if (state == ConnectionState.None) {
                color = COLOR_NONE;
            }
        }

        cmd.set(borderSelector + ".Background", color);
    }

    private static Vector3i directionToVector(Direction dir) {
        return new Vector3i(dir.dx, dir.dy, dir.dz);
    }
    
    private static ConnectionState basicToBlockStateConnectionState(BasicItemPipeComponent.ConnectionState state) {
        return switch (state) {
            case Default -> ConnectionState.Default;
            case Extract -> ConnectionState.Extract;
            case None -> ConnectionState.None;
        };
    }
    
    private static ConnectionState basicToBlockStateConnectionState(BasicPowerCableComponent.ConnectionState state) {
        return switch (state) {
            case Default -> ConnectionState.Default;
            case Extract -> ConnectionState.Extract;
            case None -> ConnectionState.None;
        };
    }
    
    @Nullable
    private static SideConfigurableConduit getConduitState(@NonNullDecl World world, int x, int y, int z) {
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
        
        // Check for BasicItemPipe ECS component first
        ComponentType<ChunkStore, BasicItemPipeComponent> basicPipeType = HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
        BasicItemPipeComponent basicPipe = stateRef.getStore().getComponent(stateRef, basicPipeType);
        if (basicPipe != null) {
            // Wrap BasicItemPipeComponent to present as SideConfigurableConduit
            return new SideConfigurableConduit() {
                @Override
                public ConnectionState getConnectionState(Direction dir) {
                    return basicToBlockStateConnectionState(basicPipe.getConnectionState(directionToVector(dir)));
                }
                
                @Override
                public void setConnectionState(Direction dir, ConnectionState state) {
                    // Not used in rendering, only in handleDataEvent
                }
                
                @Override
                public ConnectionState cycleConnectionState(Direction dir) {
                    // Not used - handled in handleDataEvent
                    return ConnectionState.Default;
                }
                
                @Override
                public boolean isSideConnected(Direction dir) {
                    return basicPipe.isSideConnected(directionToVector(dir));
                }
                
                @Override
                public int getRawSideConfig() {
                    return basicPipe.getSideConfig();
                }
                
                @Override
                public void setRawSideConfig(int raw) {
                    basicPipe.setSideConfig(raw);
                }
            };
        }
        
        // Check for BasicPowerCable ECS component
        ComponentType<ChunkStore, BasicPowerCableComponent> basicCableType = HytaleIndustriesPlugin.INSTANCE.getBasicPowerCableComponentType();
        BasicPowerCableComponent basicCable = stateRef.getStore().getComponent(stateRef, basicCableType);
        if (basicCable != null) {
            // Wrap BasicPowerCableComponent to present as SideConfigurableConduit
            return new SideConfigurableConduit() {
                @Override
                public ConnectionState getConnectionState(Direction dir) {
                    return basicToBlockStateConnectionState(basicCable.getConnectionState(directionToVector(dir)));
                }
                
                @Override
                public void setConnectionState(Direction dir, ConnectionState state) {
                    // Not used in rendering, only in handleDataEvent
                }
                
                @Override
                public ConnectionState cycleConnectionState(Direction dir) {
                    // Not used - handled in handleDataEvent
                    return ConnectionState.Default;
                }
                
                @Override
                public boolean isSideConnected(Direction dir) {
                    return basicCable.isSideConnected(directionToVector(dir));
                }
                
                @Override
                public int getRawSideConfig() {
                    return basicCable.getSideConfig();
                }
                
                @Override
                public void setRawSideConfig(int raw) {
                    basicCable.setSideConfig(raw);
                }
            };
        }

        // Only ECS components are supported; deprecated BlockState-based pipes are no longer handled
        return null;
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


