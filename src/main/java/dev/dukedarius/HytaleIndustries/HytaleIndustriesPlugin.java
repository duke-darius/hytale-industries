package dev.dukedarius.HytaleIndustries;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;
import dev.dukedarius.HytaleIndustries.ConnectedBlockRuleSets.PipeConnectedBlockRuleSet;
import dev.dukedarius.HytaleIndustries.Interactions.ConfigurePipeInteraction;

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
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public HytaleIndustriesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        // interaction registers
        Interaction.CODEC.register("HytaleIndustries_ConfigurePipe", ConfigurePipeInteraction.class, ConfigurePipeInteraction.CODEC);

        this.getBlockStateRegistry().registerBlockState(ItemPipeBlockState.class, ItemPipeBlockState.STATE_ID, ItemPipeBlockState.CODEC);

    }

    @Override
    protected void shutdown() {

    }
}