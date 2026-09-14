package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.crafting.execution.ElapsedTimeTracker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerAccessor {
    @Accessor("elapsedTime")
    void ae2cr$setElapsedTime(long elapsedTime);
}
