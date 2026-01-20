package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Energy.TransfersHE;


public class SmallBatteryBlockState extends BlockState implements StoresHE, ReceivesHE, TransfersHE {

    public static final String STATE_ID = "smallBattery";

    public static final double MAX_HE = 1_000_000.0;

    private double heStored = 0.0;

    public static final Codec<SmallBatteryBlockState> CODEC = BuilderCodec.builder(
                    SmallBatteryBlockState.class,
                    SmallBatteryBlockState::new,
                    BlockState.BASE_CODEC
            )
            .append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (s, v) -> s.heStored = v, s -> s.heStored)
            .add()
            .build();

    @Override
    public double getHeStored() {
        return heStored;
    }

    @Override
    public void setHeStored(double he) {
        if (he < 0.0) {
            heStored = 0.0;
            return;
        }
        heStored = Math.min(MAX_HE, he);
    }

    @Override
    public double getHeCapacity() {
        return MAX_HE;
    }
}
