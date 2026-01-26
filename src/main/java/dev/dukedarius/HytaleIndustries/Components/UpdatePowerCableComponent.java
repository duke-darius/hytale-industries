package dev.dukedarius.HytaleIndustries.Components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class UpdatePowerCableComponent implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<UpdatePowerCableComponent> CODEC = BuilderCodec.builder(
            UpdatePowerCableComponent.class,
            UpdatePowerCableComponent::new
    )
            .append(
                    new KeyedCodec<>("HasUpdated", Codec.BOOLEAN),
                    (o, state) -> o.hasUpdated = state,
                    o -> o.hasUpdated
            )
            .add()
            .build();

    private boolean hasUpdated;

    public UpdatePowerCableComponent() {
        this(false);
    }

    public UpdatePowerCableComponent(boolean hasUpdated) {
        this.hasUpdated = hasUpdated;
    }

    public boolean hasUpdated() {
        return hasUpdated;
    }

    public void setHasUpdated(boolean hasUpdated) {
        this.hasUpdated = hasUpdated;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new UpdatePowerCableComponent(hasUpdated);
    }
}
