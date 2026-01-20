package dev.dukedarius.HytaleIndustries;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.dukedarius.HytaleIndustries.Commands.GetPipeStateCommand;
import dev.dukedarius.HytaleIndustries.Commands.PipeHUDCommand;
import dev.dukedarius.HytaleIndustries.BlockStates.BurningGeneratorBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.BlockStates.SmallBatteryBlockState;
import dev.dukedarius.HytaleIndustries.ConnectedBlockRuleSets.PipeConnectedBlockRuleSet;
import dev.dukedarius.HytaleIndustries.Interactions.ConfigurePipeInteraction;

import dev.dukedarius.HytaleIndustries.Commands.SetPipeSideCommand;
import dev.dukedarius.HytaleIndustries.Commands.SetGeneratorStateCommand;
import dev.dukedarius.HytaleIndustries.Interactions.OpenBurningGeneratorInteraction;
import dev.dukedarius.HytaleIndustries.Interactions.OpenSmallBatteryInteraction;

import javax.annotation.Nonnull;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.LogRecord;

public class HytaleIndustriesPlugin extends JavaPlugin {

    static {
        // Ensure our ConnectedBlockRuleSet type is registered BEFORE any assets are decoded.
        // If this runs too late, BlockType assets referencing {"Type":"Pipe"} will fail to load.
        com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet.CODEC.register(
                "Pipe",
                PipeConnectedBlockRuleSet.class,
                PipeConnectedBlockRuleSet.CODEC
        );
    }


    private CopyOnWriteArrayList<LogRecord> logs = new CopyOnWriteArrayList<>();
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytaleIndustriesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        // interaction registers
        Interaction.CODEC.register("HytaleIndustries_ConfigurePipe", ConfigurePipeInteraction.class, ConfigurePipeInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenBurningGenerator", OpenBurningGeneratorInteraction.class, OpenBurningGeneratorInteraction.CODEC);
        Interaction.CODEC.register("HytaleIndustries_OpenSmallBattery", OpenSmallBatteryInteraction.class, OpenSmallBatteryInteraction.CODEC);

        this.getBlockStateRegistry().registerBlockState(ItemPipeBlockState.class, ItemPipeBlockState.STATE_ID, ItemPipeBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(BurningGeneratorBlockState.class, BurningGeneratorBlockState.STATE_ID, BurningGeneratorBlockState.CODEC);
        this.getBlockStateRegistry().registerBlockState(SmallBatteryBlockState.class, SmallBatteryBlockState.STATE_ID, SmallBatteryBlockState.CODEC);

        this.getCommandRegistry().registerCommand(new GetPipeStateCommand());
        this.getCommandRegistry().registerCommand(new SetPipeSideCommand());
        this.getCommandRegistry().registerCommand(new PipeHUDCommand());
        this.getCommandRegistry().registerCommand(new SetGeneratorStateCommand());

    }

    @Override
    protected void shutdown() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            var packetHandler = playerRef.getPacketHandler();

            if(packetHandler.stillActive()) {
                packetHandler.disconnect("Server is shutting down");
            }
        }
    }
}