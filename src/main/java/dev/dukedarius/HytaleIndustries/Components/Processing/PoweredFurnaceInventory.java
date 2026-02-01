package dev.dukedarius.HytaleIndustries.Components.Processing;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class PoweredFurnaceInventory implements Component<ChunkStore> {

    public static final BuilderCodec<PoweredFurnaceInventory> CODEC = BuilderCodec.builder(
                    PoweredFurnaceInventory.class,
                    PoweredFurnaceInventory::new
            )
            .append(new KeyedCodec<>("Input", SimpleItemContainer.CODEC),
                    (o, v) -> o.input = v,
                    o -> o.input)
            .add()
            .append(new KeyedCodec<>("Output", SimpleItemContainer.CODEC),
                    (o, v) -> o.output = v,
                    o -> o.output)
            .add()
            .append(new KeyedCodec<>("WorkRequired", Codec.FLOAT),
                    (o, v) -> o.workRequired = v,
                    o -> o.workRequired)
            .add()
            .append(new KeyedCodec<>("CurrentWork", Codec.FLOAT),
                    (o, v) -> o.currentWork = v,
                    o -> o.currentWork)
            .add()
            .build();

    public SimpleItemContainer input = new SimpleItemContainer((short) 1);
    public SimpleItemContainer output = new SimpleItemContainer((short) 1);

    public float workRequired = 0f;
    public float currentWork = 0f;

    @Override
    public PoweredFurnaceInventory clone() {
        PoweredFurnaceInventory copy = new PoweredFurnaceInventory();
        copy.input = (SimpleItemContainer) this.input.clone();
        copy.output = (SimpleItemContainer) this.output.clone();
        copy.workRequired = this.workRequired;
        copy.currentWork = this.currentWork;
        return copy;
    }
}
