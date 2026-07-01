package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.List;

public class ESControllerComponent implements Component<ChunkStore> {
    public static final BuilderCodec<ESControllerComponent> CODEC = BuilderCodec.builder(
            ESControllerComponent.class, ESControllerComponent::new).build();

    // transient cache — rebuilt every tick from connected Disk Housings
    public transient List<ItemStack> itemIndex = new ArrayList<>();
    public transient long totalStored;
    public transient long maxCapacity;
    public transient boolean networkOnline;

    // network device counts — rebuilt every tick by ESNetworkSystem BFS
    public transient int gridCount;
    public transient int diskHousingCount;
    public transient int totalDiskCount;
    public transient long energyStored;
    public transient long energyMax;
    public transient long totalPowerUsage;

    @Override
    public ESControllerComponent clone() {
        ESControllerComponent copy = new ESControllerComponent();
        copy.itemIndex = new ArrayList<>(this.itemIndex);
        copy.totalStored = this.totalStored;
        copy.maxCapacity = this.maxCapacity;
        copy.networkOnline = this.networkOnline;
        copy.gridCount = this.gridCount;
        copy.diskHousingCount = this.diskHousingCount;
        copy.totalDiskCount = this.totalDiskCount;
        copy.energyStored = this.energyStored;
        copy.energyMax = this.energyMax;
        copy.totalPowerUsage = this.totalPowerUsage;
        return copy;
    }
}
