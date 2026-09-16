package io.github.zatone0.ae2craftingrecovery.mixin.client;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import io.github.zatone0.ae2craftingrecovery.recovery.CraftingCpuMenuRecoveryAction;

/**
 * AE2 incremental status entries may omit their key when the client is expected
 * to know the entry serial. A cancel/resubmit transition can invalidate that
 * client-side serial map, leaving a live keyless row that crashes the renderer.
 */
@Mixin(value = CraftingCPUScreen.class, remap = false)
public abstract class CraftingCpuScreenMixin extends AEBaseScreen<CraftingCPUMenu> {
    @Shadow
    private CraftingStatus status;

    @Shadow
    private Button cancel;

    @Unique
    private Button ae2cr$recalculate;

    protected CraftingCpuScreenMixin(CraftingCPUMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2cr$addRecalculationButton(CraftingCPUMenu menu, Inventory inventory,
            Component title, ScreenStyle style, CallbackInfo ci) {
        ae2cr$recalculate = widgets.addButton("recalculate", Component.literal("Recalculate"),
                () -> ((CraftingCpuMenuRecoveryAction) menu).ae2cr$requestRecalculation());
    }

    @Inject(method = "m_88315_", at = @At("HEAD"))
    private void ae2cr$updateRecalculationButton(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        ae2cr$recalculate.active = cancel.active;
    }

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
