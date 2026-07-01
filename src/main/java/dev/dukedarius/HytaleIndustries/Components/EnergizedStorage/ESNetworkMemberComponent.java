package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ESNetworkMemberComponent implements Component<ChunkStore> {

    public int priority = 0;

    public static final BuilderCodec<ESNetworkMemberComponent> CODEC = BuilderCodec.builder(
                    ESNetworkMemberComponent.class, ESNetworkMemberComponent::new)
            .append(new KeyedCodec<>("Priority", Codec.INTEGER),
                    (o, v) -> o.priority = v, o -> o.priority)
            .add()
            .build();

    @Override
    public ESNetworkMemberComponent clone() {
        ESNetworkMemberComponent copy = new ESNetworkMemberComponent();
        copy.priority = this.priority;
        return copy;
    }
}
