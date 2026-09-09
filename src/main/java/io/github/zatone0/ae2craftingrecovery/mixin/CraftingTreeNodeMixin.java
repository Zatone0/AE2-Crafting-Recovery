package io.github.zatone0.ae2craftingrecovery.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;

import io.github.zatone0.ae2craftingrecovery.recovery.RetainedInventoryCraftingRequester;

/** Keeps a recovery calculation on the pattern route selected by the original job. */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeNodeMixin {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Redirect(
            method = { "findCraftedStack", "buildChildPatterns" },
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"))
    private Collection<IPatternDetails> ae2cr$keepOriginalRoute(ICraftingService service, AEKey key) {
        var requester = ((CraftingCalculationAccessor) job).ae2cr$getSimulationRequester();
        if (!(requester instanceof RetainedInventoryCraftingRequester recovery) || !recovery.restrictsPatterns()) {
            return service.getCraftingFor(key);
        }
        return service.getCraftingFor(key).stream().filter(recovery::allows).toList();
    }
}
