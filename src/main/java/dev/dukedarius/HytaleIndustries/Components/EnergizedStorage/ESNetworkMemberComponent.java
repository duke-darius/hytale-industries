package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ESNetworkMemberComponent implements Component<ChunkStore> {
    public static final BuilderCodec<ESNetworkMemberComponent> CODEC = BuilderCodec.builder(
            ESNetworkMemberComponent.class, ESNetworkMemberComponent::new).build();

    @Override
    public ESNetworkMemberComponent clone() {
        return new ESNetworkMemberComponent();
    }
}
