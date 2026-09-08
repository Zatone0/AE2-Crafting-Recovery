package io.github.zatone0.ae2craftingrecovery.mixin.client;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatus;

/**
 * AE2 incremental status entries may omit their key when the client is expected
 * to know the entry serial. A cancel/resubmit transition can invalidate that
 * client-side serial map, leaving a live keyless row that crashes the renderer.
 */
@Mixin(value = CraftingCPUScreen.class, remap = false)
public abstract class CraftingCpuScreenMixin {
    @Shadow
    private CraftingStatus status;

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2cr$dropUnresolvedIncrementalRows(CraftingStatus update, CallbackInfo ci) {
        if (status == null || status.getEntries().stream().noneMatch(entry -> entry.getWhat() == null)) {
            return;
        }

        var resolved = new ArrayList<>(status.getEntries());
        resolved.removeIf(entry -> entry.getWhat() == null);
        status = new CraftingStatus(
                true,
                status.getElapsedTime(),
                status.getRemainingItemCount(),
                status.getStartItemCount(),
                resolved);
    }
}
