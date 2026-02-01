package dev.dukedarius.HytaleIndustries.Components.ItemPipes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
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
            // Per-side item filter configuration (applies to both extraction and insertion)
            .append(
                    new KeyedCodec<>("NorthFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.northFilterItems = v,
                    o -> o.northFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("NorthFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.northFilterMode = v,
                    o -> o.northFilterMode
            )
            .add()
            .append(
                    new KeyedCodec<>("SouthFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.southFilterItems = v,
                    o -> o.southFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("SouthFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.southFilterMode = v,
                    o -> o.southFilterMode
            )
            .add()
            .append(
                    new KeyedCodec<>("WestFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.westFilterItems = v,
                    o -> o.westFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("WestFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.westFilterMode = v,
                    o -> o.westFilterMode
            )
            .add()
            .append(
                    new KeyedCodec<>("EastFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.eastFilterItems = v,
                    o -> o.eastFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("EastFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.eastFilterMode = v,
                    o -> o.eastFilterMode
            )
            .add()
            .append(
                    new KeyedCodec<>("UpFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.upFilterItems = v,
                    o -> o.upFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("UpFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.upFilterMode = v,
                    o -> o.upFilterMode
            )
            .add()
            .append(
                    new KeyedCodec<>("DownFilterItems", Codec.STRING_ARRAY),
                    (o, v) -> o.downFilterItems = v,
                    o -> o.downFilterItems
            )
            .add()
            .append(
                    new KeyedCodec<>("DownFilterMode", new EnumCodec<>(FilterMode.class)),
                    (o, v) -> o.downFilterMode = v,
                    o -> o.downFilterMode
            )
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

    // Per-side item filters (applies to both extraction and insertion)
    private String[] northFilterItems;
    private String[] southFilterItems;
    private String[] westFilterItems;
    private String[] eastFilterItems;
    private String[] upFilterItems;
    private String[] downFilterItems;

    private FilterMode northFilterMode;
    private FilterMode southFilterMode;
    private FilterMode westFilterMode;
    private FilterMode eastFilterMode;
    private FilterMode upFilterMode;
    private FilterMode downFilterMode;

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

        this.northFilterItems = cloneArray(other.northFilterItems);
        this.southFilterItems = cloneArray(other.southFilterItems);
        this.westFilterItems = cloneArray(other.westFilterItems);
        this.eastFilterItems = cloneArray(other.eastFilterItems);
        this.upFilterItems = cloneArray(other.upFilterItems);
        this.downFilterItems = cloneArray(other.downFilterItems);

        this.northFilterMode = other.northFilterMode;
        this.southFilterMode = other.southFilterMode;
        this.westFilterMode = other.westFilterMode;
        this.eastFilterMode = other.eastFilterMode;
        this.upFilterMode = other.upFilterMode;
        this.downFilterMode = other.downFilterMode;
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

        this.northFilterItems = cloneArray(other.northFilterItems);
        this.southFilterItems = cloneArray(other.southFilterItems);
        this.westFilterItems = cloneArray(other.westFilterItems);
        this.eastFilterItems = cloneArray(other.eastFilterItems);
        this.upFilterItems = cloneArray(other.upFilterItems);
        this.downFilterItems = cloneArray(other.downFilterItems);

        this.northFilterMode = other.northFilterMode;
        this.southFilterMode = other.southFilterMode;
        this.westFilterMode = other.westFilterMode;
        this.eastFilterMode = other.eastFilterMode;
        this.upFilterMode = other.upFilterMode;
        this.downFilterMode = other.downFilterMode;
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

    // ---- Filter helpers ----

    private static String[] cloneArray(String[] src) {
        return src != null ? src.clone() : null;
    }

    public enum FilterMode {
        None,
        Whitelist,
        Blacklist
    }

    private FilterMode getFilterModeByIndex(int index) {
        FilterMode mode;
        switch (index) {
            case 0 -> mode = northFilterMode;
            case 1 -> mode = southFilterMode;
            case 2 -> mode = westFilterMode;
            case 3 -> mode = eastFilterMode;
            case 4 -> mode = upFilterMode;
            case 5 -> mode = downFilterMode;
            default -> mode = FilterMode.None;
        }
        return mode != null ? mode : FilterMode.None;
    }

    private String[] getFilterItemsByIndex(int index) {
        return switch (index) {
            case 0 -> northFilterItems;
            case 1 -> southFilterItems;
            case 2 -> westFilterItems;
            case 3 -> eastFilterItems;
            case 4 -> upFilterItems;
            case 5 -> downFilterItems;
            default -> null;
        };
    }

    public FilterMode getFilterMode(Vector3i direction) {
        int idx = getBitIndex(direction);
        if (idx == -1) return FilterMode.None;
        return getFilterModeByIndex(idx);
    }

    public String[] getFilterItems(Vector3i direction) {
        int idx = getBitIndex(direction);
        if (idx == -1) return null;
        String[] arr = getFilterItemsByIndex(idx);
        return arr != null ? arr.clone() : null;
    }

    public void setFilter(Vector3i direction, FilterMode mode, String[] items) {
        int idx = getBitIndex(direction);
        if (idx == -1) return;

        String[] copy = cloneArray(items);
        switch (idx) {
            case 0 -> {
                northFilterMode = mode;
                northFilterItems = copy;
            }
            case 1 -> {
                southFilterMode = mode;
                southFilterItems = copy;
            }
            case 2 -> {
                westFilterMode = mode;
                westFilterItems = copy;
            }
            case 3 -> {
                eastFilterMode = mode;
                eastFilterItems = copy;
            }
            case 4 -> {
                upFilterMode = mode;
                upFilterItems = copy;
            }
            case 5 -> {
                downFilterMode = mode;
                downFilterItems = copy;
            }
        }
    }

    public boolean allowsItemForDirection(Vector3i direction, String itemId) {
        int idx = getBitIndex(direction);
        if (idx == -1 || itemId == null) return true;

        FilterMode mode = getFilterModeByIndex(idx);
        String[] items = getFilterItemsByIndex(idx);

        if (mode == null || mode == FilterMode.None) {
            return true;
        }

        boolean listed = false;
        if (items != null && items.length > 0) {
            for (String id : items) {
                if (itemId.equals(id)) {
                    listed = true;
                    break;
                }
            }
        }

        return switch (mode) {
            case Whitelist -> listed;
            case Blacklist -> !listed;
            case None -> true;
        };
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
