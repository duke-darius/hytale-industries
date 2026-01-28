package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ProducesHE implements Component<ChunkStore> {
    public static final BuilderCodec<ProducesHE> CODEC = BuilderCodec.builder(
                    ProducesHE.class,
                    ProducesHE::new
            )
            .append(new KeyedCodec<>("ProducedPerTick", Codec.LONG),
                    (o, val) -> o.producedPerTick = val,
                    o -> o.producedPerTick
            )
            .add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, val) -> o.enabled = val,
                    o -> o.enabled
            )
            .add()
            .append(new KeyedCodec<>("Efficiency", Codec.DOUBLE),
                    (o, val) -> o.efficiency = val,
                    o -> o.efficiency
            )
            .add()
            .append(new KeyedCodec<>("ProductionMultiplier", Codec.DOUBLE),
                    (o, val) -> o.productionMultiplier = val != null ? val : 1.0,
                    o -> o.productionMultiplier
            )
            .add()
            .build();

    public long producedPerTick;
    public boolean enabled;
    public double efficiency = 1.0;
    public double productionMultiplier = 1.0;

    @Override
    public ProducesHE clone() {
        try {
            return (ProducesHE) super.clone();
        } catch (CloneNotSupportedException e) {
            ProducesHE copy = new ProducesHE();
            copy.producedPerTick = this.producedPerTick;
            copy.enabled = this.enabled;
            copy.efficiency = this.efficiency;
            copy.productionMultiplier = this.productionMultiplier;
            return copy;
        }
    }
}
