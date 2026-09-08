package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;

@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public interface NetworkCraftingSimulationStateAccessor {
    @Accessor("list")
    KeyCounter ae2cr$getNetworkList();
}
