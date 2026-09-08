package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import net.minecraft.world.level.Level;

import io.github.zatone0.ae2craftingrecovery.recovery.RetainedInventoryCraftingRequester;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationMixin {
    @Shadow
    @Final
    private NetworkCraftingSimulationState networkInv;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2cr$includeRetainedCpuInventory(Level level, IGrid grid,
            ICraftingSimulationRequester requester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo ci) {
        if (requester instanceof RetainedInventoryCraftingRequester recoveryRequester) {
            var searchableNetworkInventory =
                    ((NetworkCraftingSimulationStateAccessor) networkInv).ae2cr$getNetworkList();
            for (var entry : recoveryRequester.retainedItems()) {
                // Add retained keys to the same parent index used for fuzzy/tag
                // candidate discovery. The finished plan later subtracts these
                // physical CPU quantities from its network withdrawals.
                searchableNetworkInventory.add(entry.getKey(), entry.getLongValue());
            }
        }
    }
}
