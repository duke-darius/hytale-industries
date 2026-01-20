package dev.dukedarius.HytaleIndustries.Energy;

public interface ReceivesHE extends StoresHE {

    default double receiveHe(double amount) {
        if (amount <= 0.0) {
            return 0.0;
        }

        double accepted = Math.min(amount, getHeFreeCapacity());
        if (accepted <= 0.0) {
            return 0.0;
        }

        setHeStored(getHeStored() + accepted);
        return accepted;
    }
}
