package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class ConsumesHE implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<ConsumesHE> CODEC = BuilderCodec.builder(
                    ConsumesHE.class,
                    ConsumesHE::new
            )
            .append(new KeyedCodec<>("HEConsumption", Codec.LONG),
                    (o, val) -> o.heConsumption = val,
                    o -> o.heConsumption
            )
            .add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, val) -> o.enabled = val,
                    o -> o.enabled
            )
            .add()
            .build();

    public long heConsumption;
    public boolean enabled;

    /** Attempts to consume HE. */
    public boolean consume(StoresHE storage) {
        if (!enabled) return false;
        if (storage.current >= heConsumption) {
            storage.current -= heConsumption;
            return true;
        }
        return false;
    }

    @Override
    public ConsumesHE clone() {
        try {
            return (ConsumesHE) super.clone();
        } catch (CloneNotSupportedException e) {
            ConsumesHE copy = new ConsumesHE();
            copy.heConsumption = this.heConsumption;
            copy.enabled = this.enabled;
            return copy;
        }
    }
}
