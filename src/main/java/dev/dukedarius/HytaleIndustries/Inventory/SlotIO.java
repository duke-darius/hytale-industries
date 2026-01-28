package dev.dukedarius.HytaleIndustries.Inventory;

public enum SlotIO {
    INPUT(true, false),
    OUTPUT(false, true),
    BOTH(true, true),
    NONE(false, false);

    private final boolean input;
    private final boolean output;

    SlotIO(boolean input, boolean output) {
        this.input = input;
        this.output = output;
    }

    public boolean allowsInput() {
        return input;
    }

    public boolean allowsOutput() {
        return output;
    }
}
