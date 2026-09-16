package io.github.zatone0.ae2craftingrecovery.recovery;

import net.minecraft.server.level.ServerPlayer;

/** Server-side entry point for an explicit recalculation request. */
public interface ManualRecalculationTarget {
    void ae2cr$requestManualRecalculation(ServerPlayer player);
}
