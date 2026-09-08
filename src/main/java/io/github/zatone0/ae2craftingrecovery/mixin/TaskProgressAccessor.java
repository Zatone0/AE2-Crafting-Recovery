package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface TaskProgressAccessor {
    @Accessor("value")
    long ae2cr$getValue();

    @Accessor("value")
    void ae2cr$setValue(long value);

}
