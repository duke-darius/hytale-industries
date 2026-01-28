package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class StoresHE implements Component<ChunkStore> {
    public static final BuilderCodec<StoresHE> CODEC = BuilderCodec.builder(
                    StoresHE.class,
                    StoresHE::new
            )
            .append(new KeyedCodec<>("Max", Codec.LONG),
                    (o, val) -> o.max = val,
                    o -> o.max
            )
            .add()
            .append(new KeyedCodec<>("Current", Codec.LONG),
                    (o, val) -> o.current = val,
                    o -> o.current
            )
            .add()
            .build();

    public long max;
    public long current;

    /** Attempts to add energy and returns leftover. */
    public long addEnergy(long amount) {
        long spaceAvailable = max - current;
        long energyToAdd = Math.min(spaceAvailable, amount);
        current += energyToAdd;
        return amount - energyToAdd;
    }

    @Override
    public StoresHE clone() {
        try {
            return (StoresHE) super.clone();
        } catch (CloneNotSupportedException e) {
            StoresHE copy = new StoresHE();
            copy.max = this.max;
            copy.current = this.current;
            return copy;
        }
    }
}
