package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Marks a block/entity as part of the HE cable network.
 * capacityPerTick is the maximum HE this node can move (in or out) per tick.
 * lossPerMeter is fractional loss applied per block traversed.
 * priority can be used by transfer to bias sinks/sources (lower = higher priority).
 */
public class CableEndpoint implements Component<ChunkStore> {
    public static final BuilderCodec<CableEndpoint> CODEC = BuilderCodec.builder(
                    CableEndpoint.class,
                    CableEndpoint::new
            )
            .append(new KeyedCodec<>("CapacityPerTick", Codec.LONG),
                    (o, v) -> o.capacityPerTick = v,
                    o -> o.capacityPerTick)
            .add()
            .append(new KeyedCodec<>("LossPerMeter", Codec.FLOAT),
                    (o, v) -> o.lossPerMeter = v,
                    o -> o.lossPerMeter)
            .add()
            .append(new KeyedCodec<>("Priority", Codec.INTEGER),
                    (o, v) -> o.priority = v,
                    o -> o.priority)
            .add()
            .build();

    public long capacityPerTick = 0;
    public float lossPerMeter = 0f;
    public int priority = 0;

    @Override
    public CableEndpoint clone() {
        try {
            return (CableEndpoint) super.clone();
        } catch (CloneNotSupportedException e) {
            CableEndpoint copy = new CableEndpoint();
            copy.capacityPerTick = this.capacityPerTick;
            copy.lossPerMeter = this.lossPerMeter;
            copy.priority = this.priority;
            return copy;
        }
    }
}
