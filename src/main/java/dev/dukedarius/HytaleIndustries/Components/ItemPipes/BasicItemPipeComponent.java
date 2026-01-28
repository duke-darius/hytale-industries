package dev.dukedarius.HytaleIndustries.Components.ItemPipes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class BasicItemPipeComponent implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<BasicItemPipeComponent> CODEC = BuilderCodec.builder(
            BasicItemPipeComponent.class,
            BasicItemPipeComponent::new
    )
            .append(
                    new KeyedCodec<>("PipeState", Codec.INTEGER),
                    (o, state) -> o.pipeState = state,
                    o -> o.pipeState
            )
            .addValidator(Validators.greaterThanOrEqual(0))
            .add()
            .append(
                    new KeyedCodec<>("SideConfig", Codec.INTEGER),
                    (o, state) -> o.sideConfig = state,
                    o -> o.sideConfig
            )
            .addValidator(Validators.greaterThanOrEqual(0))
            .add()
            .append(
                    new KeyedCodec<>("SecondsAccumulator", Codec.FLOAT),
                    (o, val) -> o.secondsAccumulator = val,
                    o -> o.secondsAccumulator
            )
            .add()
            .append(
                    new KeyedCodec<>("ManualConfigMask", Codec.INTEGER),
                    (o, val) -> o.manualConfigMask = val,
                    o -> o.manualConfigMask
            )
            .addValidator(Validators.greaterThanOrEqual(0))
            .add()
            .build();

    // Bitmask for pipe connections in 6 directions (like Adesi's)
    private int pipeState;
    
    // Per-side configuration: 2 bits per direction
    // 0 = Default, 1 = Extract, 2 = None
    private int sideConfig;
    
    // Time accumulator for extraction (runs once per second)
    private float secondsAccumulator;
    
    // Bitmask for manually configured sides (prevents auto-restoration)
    private int manualConfigMask;

    public BasicItemPipeComponent() {
        this(0, 0);
    }

    public BasicItemPipeComponent(int pipeState, int sideConfig) {
        this.pipeState = pipeState;
        this.sideConfig = sideConfig;
        this.secondsAccumulator = 0.0f;
        this.manualConfigMask = 0;
    }

    public BasicItemPipeComponent(BasicItemPipeComponent other) {
        this.pipeState = other.pipeState;
        this.sideConfig = other.sideConfig;
        this.secondsAccumulator = other.secondsAccumulator;
        this.manualConfigMask = other.manualConfigMask;
    }

    public int getPipeState() {
        return this.pipeState;
    }

    public void setPipeState(int pipeState) {
        this.pipeState = pipeState;
    }

    public int getSideConfig() {
        return this.sideConfig;
    }

    public void setSideConfig(int sideConfig) {
        this.sideConfig = sideConfig;
    }

    public float getSecondsAccumulator() {
        return this.secondsAccumulator;
    }

    public void setSecondsAccumulator(float value) {
        this.secondsAccumulator = value;
    }

    public void updateFrom(BasicItemPipeComponent other) {
        this.pipeState = other.pipeState;
        this.sideConfig = other.sideConfig;
        this.secondsAccumulator = other.secondsAccumulator;
        this.manualConfigMask = other.manualConfigMask;
    }

    private static final Vector3i[] DIRECTIONS = {
        new Vector3i(0, 0, -1),  // North = index 0
        new Vector3i(0, 0, 1),   // South = index 1
        new Vector3i(-1, 0, 0),  // West = index 2
        new Vector3i(1, 0, 0),   // East = index 3
        new Vector3i(0, 1, 0),   // Up = index 4
        new Vector3i(0, -1, 0)   // Down = index 5
    };

    public static int getBitIndex(Vector3i direction) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equals(direction)) {
                return i;
            }
        }
        return -1;
    }

    public ConnectionState getConnectionState(Vector3i direction) {
        int bitIndex = getBitIndex(direction);
        if (bitIndex == -1) return ConnectionState.Default;
        
        int v = (sideConfig >>> (bitIndex * 2)) & 0b11;
        return switch (v) {
            case 1 -> ConnectionState.Extract;
            case 2 -> ConnectionState.None;
            default -> ConnectionState.Default;
        };
    }

    public void setConnectionState(Vector3i direction, ConnectionState state, boolean manual) {
        int bitIndex = getBitIndex(direction);
        if (bitIndex == -1) return;
        
        int shift = bitIndex * 2;
        int mask = 0b11 << shift;
        int v = switch (state) {
            case Default -> 0;
            case Extract -> 1;
            case None -> 2;
        };
        
        sideConfig = (sideConfig & ~mask) | (v << shift);
        
        // Track manual configuration
        if (manual) {
            manualConfigMask |= (1 << bitIndex);
        } else {
            manualConfigMask &= ~(1 << bitIndex);
        }
    }
    
    public void setConnectionState(Vector3i direction, ConnectionState state) {
        setConnectionState(direction, state, false);
    }

    public boolean isManuallyConfigured(Vector3i direction) {
        int bitIndex = getBitIndex(direction);
        if (bitIndex == -1) return false;
        return (manualConfigMask & (1 << bitIndex)) != 0;
    }
    
    public boolean isSideConnected(Vector3i direction) {
        return getConnectionState(direction) != ConnectionState.None;
    }

    public boolean canConnectTo(Vector3i direction) {
        // The neighbor is checking if WE allow connection from THEIR direction
        // So we need to check our opposite face
        Vector3i opposite = new Vector3i(-direction.x, -direction.y, -direction.z);
        return isSideConnected(opposite);
    }

    public void setDirectionalConnection(Vector3i direction, boolean connected) {
        int bitIndex = getBitIndex(direction);
        if (bitIndex == -1) return;
        
        if (connected) {
            this.pipeState |= 1 << bitIndex;
        } else {
            this.pipeState &= ~(1 << bitIndex);
        }
    }

    public void setDirectionalState(int mask) {
        this.pipeState = mask;
    }

    public boolean isMatchingMask(int occupancyMask) {
        return getPipeState() == occupancyMask;
    }

    public boolean hasDirectionalConnection(Vector3i direction) {
        int bitIndex = getBitIndex(direction);
        if (bitIndex == -1) {
            return false;
        }
        return ((getPipeState() >> bitIndex) & 1) == 1;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new BasicItemPipeComponent(this);
    }

    public static ComponentType<ChunkStore, BasicItemPipeComponent> getComponentType() {
        return HytaleIndustriesPlugin.INSTANCE.getBasicItemPipeComponentType();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BasicItemPipeComponent{");
        sb.append("pipeState=").append(Integer.toBinaryString(pipeState));
        sb.append(", sideConfig=").append(Integer.toBinaryString(sideConfig));
        sb.append(", connections=[");
        
        boolean first = true;
        for (var direction : Vector3i.BLOCK_SIDES) {
            if (hasDirectionalConnection(direction)) {
                if (!first) sb.append(", ");
                sb.append(direction);
                first = false;
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    public enum ConnectionState {
        Default,
        Extract,
        None
    }
}
