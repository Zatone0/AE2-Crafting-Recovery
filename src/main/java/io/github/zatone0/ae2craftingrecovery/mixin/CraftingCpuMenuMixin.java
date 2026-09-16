package io.github.zatone0.ae2craftingrecovery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import io.github.zatone0.ae2craftingrecovery.recovery.CraftingCpuMenuRecoveryAction;
import io.github.zatone0.ae2craftingrecovery.recovery.ManualRecalculationTarget;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class CraftingCpuMenuMixin extends AEBaseMenu implements CraftingCpuMenuRecoveryAction {
    private static final String AE2CR_RECALCULATE_ACTION = "ae2crRecalculate";

    @Shadow
    private CraftingCPUCluster cpu;

    protected CraftingCpuMenuMixin(MenuType<?> menuType, int id, Inventory inventory, Object host) {
        super(menuType, id, inventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2cr$registerRecalculationAction(MenuType<?> menuType, int id, Inventory inventory,
            Object host, CallbackInfo ci) {
        registerClientAction(AE2CR_RECALCULATE_ACTION, this::ae2cr$requestRecalculation);
    }

    @Override
    public void ae2cr$requestRecalculation() {
        if (isClientSide()) {
            sendClientAction(AE2CR_RECALCULATE_ACTION);
            return;
        }
        if (cpu != null && getPlayer() instanceof ServerPlayer player) {
            ((ManualRecalculationTarget) cpu.craftingLogic).ae2cr$requestManualRecalculation(player);
        }
    }
}
