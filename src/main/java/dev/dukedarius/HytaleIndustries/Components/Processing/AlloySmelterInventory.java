package dev.dukedarius.HytaleIndustries.Components.Processing;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Inventory component for the Alloy Smelter machine.
 * Holds two input slots and one output slot plus simple work tracking.
 */
public class AlloySmelterInventory implements Component<ChunkStore> {

    public static final BuilderCodec<AlloySmelterInventory> CODEC = BuilderCodec.builder(
                    AlloySmelterInventory.class,
                    AlloySmelterInventory::new
            )
            .append(new KeyedCodec<>("InputA", SimpleItemContainer.CODEC),
                    (o, v) -> o.inputA = v,
                    o -> o.inputA)
            .add()
            .append(new KeyedCodec<>("InputB", SimpleItemContainer.CODEC),
                    (o, v) -> o.inputB = v,
                    o -> o.inputB)
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

    // Two separate 1-slot input containers and one 1-slot output container
    public SimpleItemContainer inputA = new SimpleItemContainer((short) 1);
    public SimpleItemContainer inputB = new SimpleItemContainer((short) 1);
    public SimpleItemContainer output = new SimpleItemContainer((short) 1);

    public float workRequired = 0f;
    public float currentWork = 0f;

    @Override
    public AlloySmelterInventory clone() {
        AlloySmelterInventory copy = new AlloySmelterInventory();
        copy.inputA = (SimpleItemContainer) this.inputA.clone();
        copy.inputB = (SimpleItemContainer) this.inputB.clone();
        copy.output = (SimpleItemContainer) this.output.clone();
        copy.workRequired = this.workRequired;
        copy.currentWork = this.currentWork;
        return copy;
    }
}
