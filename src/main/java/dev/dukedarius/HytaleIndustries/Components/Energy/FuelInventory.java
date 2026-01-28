package dev.dukedarius.HytaleIndustries.Components.Energy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class FuelInventory implements Component<ChunkStore> {
    public static final BuilderCodec<FuelInventory> CODEC = BuilderCodec.builder(
                    FuelInventory.class,
                    FuelInventory::new
            )
            .append(new KeyedCodec<>("FuelContainer", SimpleItemContainer.CODEC),
                    (o, v) -> o.fuelContainer = v,
                    o -> o.fuelContainer)
            .add()
            .append(new KeyedCodec<>("FuelValueRemaining", Codec.DOUBLE),
                    (o, v) -> o.fuelValueRemaining = v,
                    o -> o.fuelValueRemaining)
            .add()
            .build();

    public SimpleItemContainer fuelContainer = new SimpleItemContainer((short) 1);
    /** Remaining burn value on the currently burning item (fuelQuality units). */
    public double fuelValueRemaining = 0.0;

    @Override
    public FuelInventory clone() {
        FuelInventory copy = new FuelInventory();
        if (this.fuelContainer != null) {
            SimpleItemContainer cloned = (SimpleItemContainer) this.fuelContainer.clone();
            copy.fuelContainer = cloned;
        }
        copy.fuelValueRemaining = this.fuelValueRemaining;
        return copy;
    }
}
