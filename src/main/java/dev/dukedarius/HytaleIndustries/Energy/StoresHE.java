package dev.dukedarius.HytaleIndustries.Energy;

public interface StoresHE {

    double getHeStored();

    void setHeStored(double he);

    double getHeCapacity();

    default double getHeFreeCapacity() {
        return Math.max(0.0, getHeCapacity() - getHeStored());
    }
}
