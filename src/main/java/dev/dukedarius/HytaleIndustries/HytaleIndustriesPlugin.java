package dev.dukedarius.HytaleIndustries;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import dev.dukedarius.HytaleIndustries.BlockStates.BurningGeneratorBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ChunkLoaderBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.PowerCableBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.PoweredCrusherBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.PoweredFurnaceBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.QuarryBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.SmallBatteryBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.WindTurbineBlockState;
import dev.dukedarius.HytaleIndustries.ChunkLoading.ChunkLoaderManager;
import dev.dukedarius.HytaleIndustries.ChunkLoading.ChunkLoaderRegistry;
import dev.dukedarius.HytaleIndustries.Commands.GetPipeStateCommand;
import dev.dukedarius.HytaleIndustries.Commands.PipeHUDCommand;
import dev.dukedarius.HytaleIndustries.ConnectedBlockRuleSets.PipeConnectedBlockRuleSet;
import dev.dukedarius.HytaleIndustries.Interactions.ConfigurePipeInteraction;

import dev.dukedarius.HytaleIndustries.Commands.SetPipeSideCommand;
import dev.dukedarius.HytaleIndustries.Commands.SetGeneratorStateCommand;
import dev.dukedarius.HytaleIndustries.Interactions.OpenBurningGeneratorInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenChunkLoaderInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenSmallBatteryInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenPoweredCrusherInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenPoweredFurnaceInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenQuarryInteraction;

import dev.dukedarius.HytaleIndustries.Interactions.OpenWindTurbineInteraction;
import dev.dukedarius.HytaleIndustries.Systems.InventoryDropOnBreakSystem;

import javax.annotation.Nonnull;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;

public class HytaleIndustriesPlugin extends JavaPlugin {

    public static HytaleIndustriesPlugin INSTANCE;

    static {
        // Ensure our ConnectedBlockRuleSet type is registered BEFORE any assets are decoded.
        // If this runs too late, BlockType assets referencing {"Type":"Pipe"} will fail to load.
        com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet.CODEC.register(
                "Pipe",
                PipeConnectedBlockRuleSet.class,
                PipeConnectedBlockRuleSet.CODEC
        );

        // Ensure custom Interaction types are registered BEFORE any assets are decoded.
        Interaction.CODEC.register("HytaleIndustries_ConfigurePipe", ConfigurePipeInteraction.class, ConfigurePipeInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_ConfigurePowerCable", ConfigurePipeInteraction.class, ConfigurePipeInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenBurningGenerator", OpenBurningGeneratorInteraction.class, OpenBurningGeneratorInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenSmallBattery", OpenSmallBatteryInteraction.class, OpenSmallBatteryInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenPoweredFurnace", OpenPoweredFurnaceInteraction.class, OpenPoweredFurnaceInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenPoweredCrusher", OpenPoweredCrusherInteraction.class, OpenPoweredCrusherInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenChunkLoader", OpenChunkLoaderInteraction.class, OpenChunkLoaderInteraction.CODEC);

        Interaction.CODEC.register("HytaleIndustries_OpenQuarry", OpenQuarryInteraction.class, OpenQuarryInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenWindTurbine", OpenWindTurbineInteraction.class, OpenWindTurbineInteraction.CODEC);
    }


    private CopyOnWriteArrayList<LogRecord> logs = new CopyOnWriteArrayList<>();
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Config<ChunkLoaderRegistry> chunkLoaderConfig;
    private ChunkLoaderManager chunkLoaderManager;

    // ECS Component types for basic item pipes
    private ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent> basicItemPipeComponentType;
    private ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent> updatePipeComponentType;
    
    // ECS Component types for basic power cables
    private ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent> basicPowerCableComponentType;
    private ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.UpdatePowerCableComponent> updatePowerCableComponentType;

    public HytaleIndustriesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;

        // IMPORTANT: withConfig() must be called BEFORE setup().
        this.chunkLoaderConfig = this.withConfig("chunk_loaders", ChunkLoaderRegistry.CODEC);
    }

    public ChunkLoaderManager getChunkLoaderManager() {
        return chunkLoaderManager;
    }

    public ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent> getBasicItemPipeComponentType() {
        return basicItemPipeComponentType;
    }

    public ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent> getUpdatePipeComponentType() {
        return updatePipeComponentType;
    }
    
    public ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent> getBasicPowerCableComponentType() {
        return basicPowerCableComponentType;
    }
    
    public ComponentType<ChunkStore, dev.dukedarius.HytaleIndustries.Components.UpdatePowerCableComponent> getUpdatePowerCableComponentType() {
        return updatePowerCableComponentType;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        chunkLoaderManager = new ChunkLoaderManager(this, chunkLoaderConfig);


        this.getBlockStateRegistry().registerBlockState(ItemPipeBlockState.class, ItemPipeBlockState.STATE_ID, ItemPipeBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(PowerCableBlockState.class, PowerCableBlockState.STATE_ID, PowerCableBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(BurningGeneratorBlockState.class, BurningGeneratorBlockState.STATE_ID, BurningGeneratorBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(SmallBatteryBlockState.class, SmallBatteryBlockState.STATE_ID, SmallBatteryBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(PoweredFurnaceBlockState.class, PoweredFurnaceBlockState.STATE_ID, PoweredFurnaceBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(PoweredCrusherBlockState.class, PoweredCrusherBlockState.STATE_ID, PoweredCrusherBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(QuarryBlockState.class, QuarryBlockState.STATE_ID, QuarryBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(WindTurbineBlockState.class, WindTurbineBlockState.STATE_ID, WindTurbineBlockState.CODEC);

        this.getBlockStateRegistry().registerBlockState(ChunkLoaderBlockState.class, ChunkLoaderBlockState.STATE_ID, ChunkLoaderBlockState.CODEC);

        // Register ECS components for basic item pipes
        this.basicItemPipeComponentType = this.getChunkStoreRegistry().registerComponent(
                dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent.class,
                "BasicItemPipe",
                dev.dukedarius.HytaleIndustries.Components.BasicItemPipeComponent.CODEC
        );
        this.updatePipeComponentType = this.getChunkStoreRegistry().registerComponent(
                dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent.class,
                "UpdatePipeComponent",
                dev.dukedarius.HytaleIndustries.Components.UpdatePipeComponent.CODEC
        );

        // Register ECS systems for basic item pipes
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicItemPipeSystem(
                        this.basicItemPipeComponentType,
                        this.updatePipeComponentType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicItemPipeUpdateSystem(
                        this.basicItemPipeComponentType,
                        this.updatePipeComponentType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicItemPipeExtractionSystem(
                        this.basicItemPipeComponentType
                )
        );
        
        // Register ECS components for basic power cables
        this.basicPowerCableComponentType = this.getChunkStoreRegistry().registerComponent(
                dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent.class,
                "BasicPowerCable",
                dev.dukedarius.HytaleIndustries.Components.BasicPowerCableComponent.CODEC
        );
        this.updatePowerCableComponentType = this.getChunkStoreRegistry().registerComponent(
                dev.dukedarius.HytaleIndustries.Components.UpdatePowerCableComponent.class,
                "UpdatePowerCableComponent",
                dev.dukedarius.HytaleIndustries.Components.UpdatePowerCableComponent.CODEC
        );
        
        // Register ECS systems for basic power cables
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicPowerCableSystem(
                        this.basicPowerCableComponentType,
                        this.updatePowerCableComponentType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicPowerCableUpdateSystem(
                        this.basicPowerCableComponentType,
                        this.updatePowerCableComponentType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.BasicPowerCableTransferSystem(
                        this.basicPowerCableComponentType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.UpdatePowerCableSystem(
                        this.updatePowerCableComponentType
                )
        );

        // Ensure machine inventories drop when the block is broken.
        this.getEntityStoreRegistry().registerSystem(new InventoryDropOnBreakSystem());

        this.getCommandRegistry().registerCommand(new GetPipeStateCommand());
        this.getCommandRegistry().registerCommand(new SetPipeSideCommand());
        this.getCommandRegistry().registerCommand(new PipeHUDCommand());
        this.getCommandRegistry().registerCommand(new SetGeneratorStateCommand());

    }

    @Override
    protected void start() {
        if (chunkLoaderManager != null) {
            chunkLoaderManager.start();
        }
    }

    @Override
    protected void shutdown() {
        if (chunkLoaderManager != null) {
            chunkLoaderManager.stop();
        }

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var packetHandler = playerRef.getPacketHandler();

            if (packetHandler.stillActive()) {
                packetHandler.disconnect("Server is shutting down");
            }
        }
    }
}
