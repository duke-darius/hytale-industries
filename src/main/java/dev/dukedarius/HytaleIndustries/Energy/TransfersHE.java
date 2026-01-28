package dev.dukedarius.HytaleIndustries.Energy;

@Deprecated(forRemoval = true)
public interface TransfersHE extends StoresHE {
    default double extractHe(double amount) {
        if (amount <= 0.0) {
            return 0.0;
        }

        double extracted = Math.min(amount, Math.max(0.0, getHeStored()));
        if (extracted <= 0.0) {
            return 0.0;
        }

        setHeStored(getHeStored() - extracted);
        return extracted;
    }
}
