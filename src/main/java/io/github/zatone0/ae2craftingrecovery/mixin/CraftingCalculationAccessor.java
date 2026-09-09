package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.crafting.CraftingCalculation;

@Mixin(value = CraftingCalculation.class, remap = false)
public interface CraftingCalculationAccessor {
    @Accessor("simRequester")
    ICraftingSimulationRequester ae2cr$getSimulationRequester();
}
