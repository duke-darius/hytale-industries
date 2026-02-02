package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class WindTurbineComponent implements Component<ChunkStore> {

    public static final BuilderCodec<WindTurbineComponent> CODEC = BuilderCodec.builder(
                    WindTurbineComponent.class,
                    WindTurbineComponent::new
            )
            .append(new KeyedCodec<>("Initialized", Codec.BOOLEAN),
                    (c, v) -> c.initialized = v != null && v,
                    c -> c.initialized)
            .add()
            .append(new KeyedCodec<>("LastProduction", Codec.DOUBLE),
                    (c, v) -> c.lastProductionPerSecond = v != null ? v : 0.0,
                    c -> c.lastProductionPerSecond)
            .add()
            .append(new KeyedCodec<>("EnergyRemainder", Codec.DOUBLE),
                    (c, v) -> c.energyRemainder = v != null ? v : 0.0,
                    c -> c.energyRemainder)
            .add()
            .build();

    public boolean initialized = false;
    /** Last production rate in HE per second, used only for UI. */
    public double lastProductionPerSecond = 0.0;
    /** Fractional HE carried over between ticks when converting per-second to per-tick. */
    public double energyRemainder = 0.0;

    @Override
    public WindTurbineComponent clone() {
        try {
            return (WindTurbineComponent) super.clone();
        } catch (CloneNotSupportedException e) {
            WindTurbineComponent copy = new WindTurbineComponent();
            copy.initialized = this.initialized;
            copy.lastProductionPerSecond = this.lastProductionPerSecond;
            copy.energyRemainder = this.energyRemainder;
            return copy;
        }
    }
}
