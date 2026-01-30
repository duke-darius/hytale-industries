package dev.dukedarius.HytaleIndustries;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import dev.dukedarius.HytaleIndustries.Components.ChunkLoading.ChunkLoaderComponent;
import dev.dukedarius.HytaleIndustries.Components.Quarry.QuarryComponent;
import dev.dukedarius.HytaleIndustries.BlockStates.QuarryBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.WindTurbineBlockState;
import dev.dukedarius.HytaleIndustries.ChunkLoading.ChunkLoaderManager;
import dev.dukedarius.HytaleIndustries.ChunkLoading.ChunkLoaderRegistry;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.BasicItemPipeComponent;
import dev.dukedarius.HytaleIndustries.Components.ItemPipes.UpdatePipeComponent;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.BasicPowerCableComponent;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.UpdatePowerCableComponent;
import dev.dukedarius.HytaleIndustries.Components.Energy.CableEndpoint;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.FuelInventory;
import dev.dukedarius.HytaleIndustries.Components.Energy.ProducesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.HEProcessing;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredCrusherInventory;
import dev.dukedarius.HytaleIndustries.Interactions.ConfigurePipeInteraction;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredFurnaceInventory;

import dev.dukedarius.HytaleIndustries.Interactions.OpenBurningGeneratorInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenChunkLoaderInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenSmallBatteryInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenPoweredCrusherInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenPoweredFurnaceInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenQuarryInteraction;

import dev.dukedarius.HytaleIndustries.Interactions.OpenWindTurbineInteraction;
import dev.dukedarius.HytaleIndustries.Systems.BlockBreakSystem;
import dev.dukedarius.HytaleIndustries.Systems.BlockPlaceSystem;
import dev.dukedarius.HytaleIndustries.Systems.InventoryDropOnBreakSystem;
import dev.dukedarius.HytaleIndustries.Systems.EnergyNeighborUpdateOnPlaceSystem;
import dev.dukedarius.HytaleIndustries.Systems.InventoryNeighborUpdateOnPlaceSystem;
import dev.dukedarius.HytaleIndustries.Systems.BasicPowerCableTransferSystem;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapters;
import dev.dukedarius.HytaleIndustries.Inventory.adapters.BlockStateItemContainerAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.adapters.FuelInventoryAdapter;

import javax.annotation.Nonnull;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class HytaleIndustriesPlugin extends JavaPlugin {

    public static HytaleIndustriesPlugin INSTANCE;
    public static final int TPS = 30;

    static {

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
    private ComponentType<ChunkStore, BasicItemPipeComponent> basicItemPipeComponentType;
    private ComponentType<ChunkStore, UpdatePipeComponent> updatePipeComponentType;
    
    // ECS Component types for basic power cables
    private ComponentType<ChunkStore, BasicPowerCableComponent> basicPowerCableComponentType;
    private ComponentType<ChunkStore, UpdatePowerCableComponent> updatePowerCableComponentType;

    // HE component types
    private ComponentType<ChunkStore, StoresHE> storesHeType;
    private ComponentType<ChunkStore, ConsumesHE> consumesHeType;
    private ComponentType<ChunkStore, ProducesHE> producesHeType;
    private ComponentType<ChunkStore, HEProcessing> heProcessingType;
    private ComponentType<ChunkStore, CableEndpoint> cableEndpointType;
    private ComponentType<ChunkStore, FuelInventory> fuelInventoryType;
    private ComponentType<ChunkStore, PoweredFurnaceInventory> poweredFurnaceInventoryType;
    private ComponentType<ChunkStore, PoweredCrusherInventory> poweredCrusherInventoryType;
    private ComponentType<ChunkStore, ChunkLoaderComponent> chunkLoaderComponentType;
    private ComponentType<ChunkStore, QuarryComponent> quarryComponentType;

    public HytaleIndustriesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
        LOGGER.setLevel(Level.FINE);

        // IMPORTANT: withConfig() must be called BEFORE setup().
        this.chunkLoaderConfig = this.withConfig("chunk_loaders", ChunkLoaderRegistry.CODEC);
    }

    public ChunkLoaderManager getChunkLoaderManager() {
        return chunkLoaderManager;
    }

    public ComponentType<ChunkStore, BasicItemPipeComponent> getBasicItemPipeComponentType() {
        return basicItemPipeComponentType;
    }

    public ComponentType<ChunkStore, UpdatePipeComponent> getUpdatePipeComponentType() {
        return updatePipeComponentType;
    }
    
    public ComponentType<ChunkStore, BasicPowerCableComponent> getBasicPowerCableComponentType() {
        return basicPowerCableComponentType;
    }
    
    public ComponentType<ChunkStore, UpdatePowerCableComponent> getUpdatePowerCableComponentType() {
        return updatePowerCableComponentType;
    }
    public ComponentType<ChunkStore, StoresHE> getStoresHeType() { return storesHeType; }
    public ComponentType<ChunkStore, ConsumesHE> getConsumesHeType() { return consumesHeType; }
    public ComponentType<ChunkStore, ProducesHE> getProducesHeType() { return producesHeType; }
    public ComponentType<ChunkStore, HEProcessing> getHeProcessingType() { return heProcessingType; }
    public ComponentType<ChunkStore, CableEndpoint> getCableEndpointType() { return cableEndpointType; }
    public ComponentType<ChunkStore, FuelInventory> getFuelInventoryType() { return fuelInventoryType; }
    public ComponentType<ChunkStore, PoweredFurnaceInventory> getPoweredFurnaceInventoryType() { return poweredFurnaceInventoryType; }
    public ComponentType<ChunkStore, PoweredCrusherInventory> getPoweredCrusherInventoryType() { return poweredCrusherInventoryType; }
    public ComponentType<ChunkStore, ChunkLoaderComponent> getChunkLoaderComponentType() { return chunkLoaderComponentType; }
    public ComponentType<ChunkStore, QuarryComponent> getQuarryComponentType() { return quarryComponentType; }


    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        chunkLoaderManager = new ChunkLoaderManager(this, chunkLoaderConfig);

        this.getCommandRegistry().registerCommand(new dev.dukedarius.HytaleIndustries.Commands.DebugItemSelectorCommand());

        this.getCommandRegistry().registerCommand(new dev.dukedarius.HytaleIndustries.Commands.ShowChunksCommand());

        this.getBlockStateRegistry().registerBlockState(WindTurbineBlockState.class, WindTurbineBlockState.STATE_ID, WindTurbineBlockState.CODEC);

        // Register inventory adapters for pipes
        InventoryAdapters.register(new BlockStateItemContainerAdapter());
        InventoryAdapters.register(new FuelInventoryAdapter());
        InventoryAdapters.register(new dev.dukedarius.HytaleIndustries.Inventory.adapters.PoweredFurnaceInventoryAdapter());
        InventoryAdapters.register(new dev.dukedarius.HytaleIndustries.Inventory.adapters.PoweredCrusherInventoryAdapter());

        // Register ECS components for basic item pipes
        this.basicItemPipeComponentType = this.getChunkStoreRegistry().registerComponent(
                BasicItemPipeComponent.class,
                "BasicItemPipe",
                BasicItemPipeComponent.CODEC
        );
        this.updatePipeComponentType = this.getChunkStoreRegistry().registerComponent(
                UpdatePipeComponent.class,
                "UpdatePipeComponent",
                UpdatePipeComponent.CODEC
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
                BasicPowerCableComponent.class,
                "BasicPowerCable",
                BasicPowerCableComponent.CODEC
        );
        this.updatePowerCableComponentType = this.getChunkStoreRegistry().registerComponent(
                UpdatePowerCableComponent.class,
                "UpdatePowerCableComponent",
                UpdatePowerCableComponent.CODEC
        );

        // Register HE components
        this.storesHeType = this.getChunkStoreRegistry().registerComponent(
                StoresHE.class,
                "StoresHE",
                StoresHE.CODEC
        );
        this.consumesHeType = this.getChunkStoreRegistry().registerComponent(
                ConsumesHE.class,
                "ConsumesHE",
                ConsumesHE.CODEC
        );
        this.producesHeType = this.getChunkStoreRegistry().registerComponent(
                ProducesHE.class,
                "ProducesHE",
                ProducesHE.CODEC
        );
        this.heProcessingType = this.getChunkStoreRegistry().registerComponent(
                HEProcessing.class,
                "HEProcessing",
                HEProcessing.CODEC
        );
        this.cableEndpointType = this.getChunkStoreRegistry().registerComponent(
                CableEndpoint.class,
                "CableEndpoint",
                CableEndpoint.CODEC
        );
        this.fuelInventoryType = this.getChunkStoreRegistry().registerComponent(
                FuelInventory.class,
                "FuelInventory",
                FuelInventory.CODEC
        );
        this.chunkLoaderComponentType = this.getChunkStoreRegistry().registerComponent(
                ChunkLoaderComponent.class,
                "ChunkLoader",
                ChunkLoaderComponent.CODEC
        );
        this.poweredFurnaceInventoryType = this.getChunkStoreRegistry().registerComponent(
                PoweredFurnaceInventory.class,
                "PoweredFurnaceInventory",
                PoweredFurnaceInventory.CODEC
        );
        this.poweredCrusherInventoryType = this.getChunkStoreRegistry().registerComponent(
                PoweredCrusherInventory.class,
                "PoweredCrusherInventory",
                PoweredCrusherInventory.CODEC
        );
        this.quarryComponentType = this.getChunkStoreRegistry().registerComponent(
                QuarryComponent.class,
                "Quarry",
                QuarryComponent.CODEC
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
                new dev.dukedarius.HytaleIndustries.Systems.UpdatePowerCableSystem(
                        this.updatePowerCableComponentType
                )
        );

        // Register HE systems
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.Energy.HEProductionSystem(
                        this.producesHeType,
                        this.storesHeType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.Energy.HEConsumptionSystem(
                        this.consumesHeType,
                        this.storesHeType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.Energy.HEProcessingSystem(
                        this.heProcessingType,
                        this.consumesHeType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new BasicPowerCableTransferSystem(
                        this.basicPowerCableComponentType,
                        this.cableEndpointType,
                        this.storesHeType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.Energy.FuelBurnSystem(
                        this.fuelInventoryType,
                        this.producesHeType,
                        this.storesHeType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.PoweredFurnaceInitSystem()
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.PoweredFurnaceProcessingSystem(
                        this.poweredFurnaceInventoryType,
                        this.storesHeType,
                        this.consumesHeType,
                        this.heProcessingType
                )
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.PoweredCrusherInitSystem()
        );
        this.getChunkStoreRegistry().registerSystem(
                new dev.dukedarius.HytaleIndustries.Systems.PoweredCrusherProcessingSystem(
                        this.poweredCrusherInventoryType,
                        this.storesHeType,
                        this.consumesHeType,
                        this.heProcessingType
                )
        );

        this.getEntityStoreRegistry().registerSystem(new BlockBreakSystem());
        this.getEntityStoreRegistry().registerSystem(new BlockPlaceSystem());

        // Chunk-store systems
        this.getChunkStoreRegistry().registerSystem(new EnergyNeighborUpdateOnPlaceSystem());
        this.getChunkStoreRegistry().registerSystem(new InventoryNeighborUpdateOnPlaceSystem());
        this.getChunkStoreRegistry().registerSystem(new dev.dukedarius.HytaleIndustries.Systems.ChunkLoaderSystem(
                this.chunkLoaderComponentType
        ));
        this.getChunkStoreRegistry().registerSystem(new dev.dukedarius.HytaleIndustries.Systems.ChunkLoaderInitSystem(
                this.chunkLoaderComponentType
        ));
        this.getChunkStoreRegistry().registerSystem(new dev.dukedarius.HytaleIndustries.Systems.QuarrySystem(
                this.quarryComponentType,
                this.storesHeType,
                this.consumesHeType
        ));

        // Ensure machine inventories drop when the block is broken.
        this.getEntityStoreRegistry().registerSystem(new InventoryDropOnBreakSystem());

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
