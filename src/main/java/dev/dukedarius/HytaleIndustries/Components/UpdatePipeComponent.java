package dev.dukedarius.HytaleIndustries.Components;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

public class UpdatePipeComponent implements Component<ChunkStore> {

    public static final BuilderCodec<UpdatePipeComponent> CODEC = BuilderCodec.builder(
            UpdatePipeComponent.class,
            UpdatePipeComponent::new
    ).build();

    private boolean hasUpdated = false;

    public UpdatePipeComponent() {
    }

    public UpdatePipeComponent(UpdatePipeComponent other) {
        this.hasUpdated = other.hasUpdated;
    }

    @NullableDecl
    @Override
    public Component<ChunkStore> clone() {
        return new UpdatePipeComponent(this);
    }

    public static ComponentType<ChunkStore, UpdatePipeComponent> getComponentType() {
        return HytaleIndustriesPlugin.INSTANCE.getUpdatePipeComponentType();
    }

    public boolean hasUpdated() {
        return this.hasUpdated;
    }

    public void setHasUpdated(boolean updated) {
        this.hasUpdated = updated;
    }
}
