package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
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
            persistSelf();
            return;
        }
        heStored = Math.min(MAX_HE, he);
        persistSelf();
    }

    @Override
    public double getHeCapacity() {
        return MAX_HE;
    }

    private void persistSelf() {
        var chunk = this.getChunk();
        var pos = this.getBlockPosition();
        if (chunk == null || pos == null) return;
        var ref = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (ref == null) return;
        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type =
                (ComponentType) BlockStateModule.get().getComponentType((Class) this.getClass());
        if (type == null) return;
        ref.getStore().replaceComponent(ref, type, this);
        chunk.markNeedsSaving();
    }
}
