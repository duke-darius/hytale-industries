package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ESGridComponent implements Component<ChunkStore> {
    public static final BuilderCodec<ESGridComponent> CODEC = BuilderCodec.builder(
            ESGridComponent.class, ESGridComponent::new).build();

    @Override
    public ESGridComponent clone() {
        return new ESGridComponent();
    }
}
