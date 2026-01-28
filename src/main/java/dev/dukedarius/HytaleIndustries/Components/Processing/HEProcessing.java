package dev.dukedarius.HytaleIndustries.Components.Processing;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class HEProcessing implements Component<ChunkStore> {
    public static final BuilderCodec<HEProcessing> CODEC = BuilderCodec.builder(
                    HEProcessing.class,
                    HEProcessing::new
            )
            .append(new KeyedCodec<>("WorkRequired", Codec.FLOAT),
                    (o, val) -> o.workRequired = val,
                    o -> o.workRequired
            )
            .add()
            .append(new KeyedCodec<>("CurrentWork", Codec.FLOAT),
                    (o, val) -> o.currentWork = val,
                    o -> o.currentWork
            )
            .add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (o, val) -> o.enabled = val,
                    o -> o.enabled)
            .add()
            .build();

    private float workRequired;
    private float currentWork;
    private boolean enabled;

    public float getWorkRequired() {
        return workRequired;
    }

    public void setWorkRequired(float workRequired) {
        this.workRequired = workRequired;
    }

    public float getCurrentWork() {
        return currentWork;
    }

    public void setCurrentWork(float currentWork) {
        this.currentWork = currentWork;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public HEProcessing clone() {
        try {
            return (HEProcessing) super.clone();
        } catch (CloneNotSupportedException e) {
            HEProcessing copy = new HEProcessing();
            copy.workRequired = this.workRequired;
            copy.currentWork = this.currentWork;
            copy.enabled = this.enabled;
            return copy;
        }
    }
}
