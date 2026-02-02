package dev.dukedarius.HytaleIndustries.Components.Quarry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class QuarryComponent implements Component<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> {

    public enum QuarryStatus {
        IDLE,
        ACTIVE
    }

    private static final double HE_CAPACITY = 10_000.0;
    private static final double HE_CONSUMPTION_PER_SECOND = 50;
    private static final int UNSET = Integer.MIN_VALUE;
    private static final int WORLD_MIN_Y = 0;
    private static final int WORLD_MAX_Y_EXCLUSIVE = 320;

    @Nonnull
    public static final BuilderCodec<QuarryComponent> CODEC = BuilderCodec.builder(
            QuarryComponent.class,
            QuarryComponent::new
    )
            .append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (c, v) -> c.heStored = v, c -> c.heStored)
            .add()
            .append(new KeyedCodec<>("Progress", Codec.FLOAT), (c, v) -> c.progress = v, c -> c.progress)
            .add()
            .append(new KeyedCodec<>("Inventory", SimpleItemContainer.CODEC), (c, v) -> c.inventory = v, c -> c.inventory)
            .add()
            .append(new KeyedCodec<>("Width", Codec.INTEGER), (c, v) -> c.width = v, c -> c.width)
            .add()
            .append(new KeyedCodec<>("Depth", Codec.INTEGER), (c, v) -> c.depth = v, c -> c.depth)
            .add()
            .append(new KeyedCodec<>("YStart", Codec.INTEGER), (c, v) -> c.yStart = v, c -> c.yStart)
            .add()
            .append(new KeyedCodec<>("Status", Codec.STRING), (c, v) -> c.currentStatus = parseStatus(v), c -> c.currentStatus.name())
            .add()
            .append(new KeyedCodec<>("StartX", Codec.INTEGER), (c, v) -> c.startX = v, c -> c.startX)
            .add()
            .append(new KeyedCodec<>("StartZ", Codec.INTEGER), (c, v) -> c.startZ = v, c -> c.startZ)
            .add()
            .append(new KeyedCodec<>("EndX", Codec.INTEGER), (c, v) -> c.endX = v, c -> c.endX)
            .add()
            .append(new KeyedCodec<>("EndZ", Codec.INTEGER), (c, v) -> c.endZ = v, c -> c.endZ)
            .add()
            .append(new KeyedCodec<>("CurrentX", Codec.INTEGER), (c, v) -> c.currentX = v, c -> c.currentX)
            .add()
            .append(new KeyedCodec<>("CurrentY", Codec.INTEGER), (c, v) -> c.currentY = v, c -> c.currentY)
            .add()
            .append(new KeyedCodec<>("CurrentZ", Codec.INTEGER), (c, v) -> c.currentZ = v, c -> c.currentZ)
            .add()
            .append(new KeyedCodec<>("Speed", Codec.FLOAT), (c, v) -> c.speed = v, c -> c.speed)
            .add()
            .append(new KeyedCodec<>("Efficiency", Codec.FLOAT), (c, v) -> c.efficiency = v, c -> c.efficiency)
            .add()
            .append(new KeyedCodec<>("Gentle", Codec.BOOLEAN), (c, v) -> c.gentle = v, c -> c.gentle)
            .add()
            .append(new KeyedCodec<>("ShowArea", Codec.BOOLEAN), (c, v) -> c.showArea = v, c -> c.showArea)
            .add()
            .build();

    public SimpleItemContainer inventory = new SimpleItemContainer((short) 20);
    public double heStored = 0.0;
    public float progress = 0.0f;
    public int maxSize = 128;
    public int width = 17;
    public int depth = 17;
    public int yStart = WORLD_MAX_Y_EXCLUSIVE - 1;
    public int startX = UNSET;
    public int startZ = UNSET;
    public int endX = UNSET;
    public int endZ = UNSET;
    public int currentX = UNSET;
    public int currentY = UNSET;
    public int currentZ = UNSET;
    public QuarryStatus currentStatus = QuarryStatus.IDLE;
    public float speed = 1f;       // 1x by default; 2.0 halves time per block
    public float efficiency = 1f;  // 1x by default; 2.0 halves HE usage
    public boolean gentle = false; // silk-touch style; 10x HE cost when true
    public boolean showArea = false;

    public boolean hasScanBounds() {
        return startX != UNSET && startZ != UNSET && endX != UNSET && endZ != UNSET;
    }

    public boolean hasCurrentPos() {
        return currentX != UNSET && currentY != UNSET && currentZ != UNSET;
    }

    public void clearMiningState(boolean clearBounds) {
        currentX = UNSET;
        currentY = UNSET;
        currentZ = UNSET;
        if (false) {
            startX = UNSET;
            startZ = UNSET;
            endX = UNSET;
            endZ = UNSET;
        }
    }

    @Nullable
    public Vector3i getCurrentPos() {
        if (!hasCurrentPos()) {
            return null;
        }
        return new Vector3i(currentX, currentY, currentZ);
    }

    @Nullable
    public ItemContainer getOutputContainerAbove(World world) {
        if (world == null) return null;
        // To be implemented in system
        return null;
    }

    public void setWidth(int w) {
        this.width = Math.max(1, Math.min(maxSize, w));
    }

    public void setDepth(int d) {
        this.depth = Math.max(1, Math.min(maxSize, d));
    }

    public void setYStart(int y) {
        this.yStart = Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y_EXCLUSIVE - 1, y));
    }

    public void startMining(int quarryX, int quarryY, int quarryZ, int rot) {
        if (currentStatus != QuarryStatus.IDLE) return;

        dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.LOGGER.atInfo().log(
                "[Quarry] startMining at (%d,%d,%d) rot=%d width=%d depth=%d yStart=%d speed=%.2f eff=%.2f gentle=%s",
                quarryX, quarryY, quarryZ, rot, width, depth, yStart, speed, efficiency, gentle
        );

        int halfWidth = width / 2;

        // Match original QuarryBlockState startMining orientation logic
        switch (rot) {
            case 0 -> { // North: -Z is forward
                startX = quarryX - halfWidth;
                startZ = quarryZ - depth;
                endX = quarryX - halfWidth + width;
                endZ = quarryZ;
            }
            case 1 -> { // West: -X is forward
                startX = quarryX - depth;
                startZ = quarryZ - halfWidth;
                endX = quarryX;
                endZ = quarryZ - halfWidth + width;
            }
            case 2 -> { // South: +Z is forward
                startX = quarryX - halfWidth;
                startZ = quarryZ + 1;
                endX = quarryX - halfWidth + width;
                endZ = quarryZ + 1 + depth;
            }
            case 3 -> { // East: +X is forward
                startX = quarryX + 1;
                startZ = quarryZ - halfWidth;
                endX = quarryX + 1 + depth;
                endZ = quarryZ - halfWidth + width;
            }
            default -> {
                // Fallback: centered box around quarry
                startX = quarryX - halfWidth;
                startZ = quarryZ - halfWidth;
                endX = quarryX - halfWidth + width;
                endZ = quarryZ - halfWidth + width;
            }
        }

        int startY = Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y_EXCLUSIVE - 1, yStart));
        currentX = startX;
        currentY = startY;
        currentZ = startZ;

        currentStatus = QuarryStatus.ACTIVE;
    }

    public void reset() {
        currentStatus = QuarryStatus.IDLE;
        clearMiningState(true);
        progress = 0.0f;
    }

    public double getHeStored() {
        return heStored;
    }

    public double getHeCapacity() {
        return HE_CAPACITY;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public int getYStart() {
        return yStart;
    }

    private static QuarryStatus parseStatus(@Nullable String status) {
        if (status == null) {
            return QuarryStatus.IDLE;
        }
        try {
            return QuarryStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return QuarryStatus.IDLE;
        }
    }

    @Override
    public QuarryComponent clone() {
        try {
            return (QuarryComponent) super.clone();
        } catch (CloneNotSupportedException e) {
            QuarryComponent copy = new QuarryComponent();
            copy.inventory = this.inventory;
            copy.heStored = this.heStored;
            copy.progress = this.progress;
            copy.maxSize = this.maxSize;
            copy.width = this.width;
            copy.depth = this.depth;
            copy.yStart = this.yStart;
            copy.startX = this.startX;
            copy.startZ = this.startZ;
            copy.endX = this.endX;
            copy.endZ = this.endZ;
            copy.currentX = this.currentX;
            copy.currentY = this.currentY;
            copy.currentZ = this.currentZ;
            copy.currentStatus = this.currentStatus;
            copy.speed = this.speed;
            return copy;
        }
    }
}
