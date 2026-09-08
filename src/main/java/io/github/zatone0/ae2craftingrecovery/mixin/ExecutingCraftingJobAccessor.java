package io.github.zatone0.ae2craftingrecovery.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("link")
    CraftingLink ae2cr$getLink();

    @Accessor("tasks")
    Map<IPatternDetails, Object> ae2cr$getTasks();

    @Accessor("waitingFor")
    ListCraftingInventory ae2cr$getWaitingFor();

    @Accessor("finalOutput")
    GenericStack ae2cr$getFinalOutput();

    @Accessor("remainingAmount")
    long ae2cr$getRemainingAmount();

    @Accessor("playerId")
    Integer ae2cr$getPlayerId();
}
