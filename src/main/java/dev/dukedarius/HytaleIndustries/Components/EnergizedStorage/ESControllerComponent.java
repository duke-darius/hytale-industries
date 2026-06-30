package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.HashMap;
import java.util.Map;

public class ESControllerComponent implements Component<ChunkStore> {
    public static final BuilderCodec<ESControllerComponent> CODEC = BuilderCodec.builder(
            ESControllerComponent.class, ESControllerComponent::new).build();

    // transient cache — rebuilt every tick from connected Disk Housings
    public transient Map<String, Long> itemIndex = new HashMap<>();
    public transient long totalStored;
    public transient long maxCapacity;
    public transient boolean networkOnline;

    @Override
    public ESControllerComponent clone() {
        ESControllerComponent copy = new ESControllerComponent();
        copy.itemIndex = new HashMap<>(this.itemIndex);
        copy.totalStored = this.totalStored;
        copy.maxCapacity = this.maxCapacity;
        copy.networkOnline = this.networkOnline;
        return copy;
    }
}
