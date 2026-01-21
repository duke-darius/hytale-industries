package dev.dukedarius.HytaleIndustries.Pipes;

import dev.dukedarius.HytaleIndustries.BlockStates.ItemPipeBlockState;

/**
 * Shared per-side conduit configuration.
 *
 * We reuse the existing bit-encoding (2 bits per direction) and UI interactions
 * across different conduit-like blocks (item pipes, power cables, etc.).
 */
public interface SideConfigurableConduit {

    ItemPipeBlockState.ConnectionState getConnectionState(ItemPipeBlockState.Direction dir);

    void setConnectionState(ItemPipeBlockState.Direction dir, ItemPipeBlockState.ConnectionState state);

    ItemPipeBlockState.ConnectionState cycleConnectionState(ItemPipeBlockState.Direction dir);

    boolean isSideConnected(ItemPipeBlockState.Direction dir);

    int getRawSideConfig();

    void setRawSideConfig(int raw);
}
