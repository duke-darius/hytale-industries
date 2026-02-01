package dev.dukedarius.HytaleIndustries.Components.Processing;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class PoweredCrusherInventory implements Component<ChunkStore> {

    public static final BuilderCodec<PoweredCrusherInventory> CODEC = BuilderCodec.builder(
                    PoweredCrusherInventory.class,
                    PoweredCrusherInventory::new
            )
            .append(new KeyedCodec<>("Input", SimpleItemContainer.CODEC),
                    (o, v) -> o.input = v,
                    o -> o.input)
            .add()
            .append(new KeyedCodec<>("Output", SimpleItemContainer.CODEC),
                    (o, v) -> o.output = v,
                    o -> o.output)
            .add()
            .append(new KeyedCodec<>("WorkRequired", com.hypixel.hytale.codec.Codec.FLOAT),
                    (o, v) -> o.workRequired = v,
                    o -> o.workRequired)
            .add()
            .append(new KeyedCodec<>("CurrentWork", com.hypixel.hytale.codec.Codec.FLOAT),
                    (o, v) -> o.currentWork = v,
                    o -> o.currentWork)
            .add()
            .build();

    public SimpleItemContainer input = new SimpleItemContainer((short) 1);
    public SimpleItemContainer output = new SimpleItemContainer((short) 1);

    public float workRequired = 0f;
    public float currentWork = 0f;

    @Override
    public PoweredCrusherInventory clone() {
        PoweredCrusherInventory copy = new PoweredCrusherInventory();
        copy.input = (SimpleItemContainer) this.input.clone();
        copy.output = (SimpleItemContainer) this.output.clone();
        copy.workRequired = this.workRequired;
        copy.currentWork = this.currentWork;
        return copy;
    }
}
