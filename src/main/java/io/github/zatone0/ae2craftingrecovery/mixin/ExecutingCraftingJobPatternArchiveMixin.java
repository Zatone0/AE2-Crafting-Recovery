package io.github.zatone0.ae2craftingrecovery.mixin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class ExecutingCraftingJobPatternArchiveMixin implements ExecutingCraftingJobPatternArchive {
    @Unique
    private static final String AE2CR_ORIGINAL_PATTERNS = "ae2crOriginalPatterns";

    @Shadow
    private Map<IPatternDetails, ?> tasks;

    @Unique
    private final Set<AEItemKey> ae2cr$originalPatternDefinitions = new HashSet<>();

    @Override
    public Set<AEItemKey> ae2cr$getOriginalPatternDefinitions() {
        return Set.copyOf(ae2cr$originalPatternDefinitions);
    }

    @Override
    public void ae2cr$setOriginalPatternDefinitions(Collection<AEItemKey> definitions) {
        ae2cr$originalPatternDefinitions.clear();
        ae2cr$originalPatternDefinitions.addAll(definitions);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At("RETURN"))
    private void ae2cr$readOriginalPatterns(CompoundTag tag, @Coerce Object listener,
            CraftingCpuLogic logic, CallbackInfo ci) {
        ListTag definitions = tag.getList(AE2CR_ORIGINAL_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < definitions.size(); i++) {
            AEItemKey key = AEItemKey.fromTag(definitions.getCompound(i));
            if (key != null) {
                ae2cr$originalPatternDefinitions.add(key);
            }
        }
        if (ae2cr$originalPatternDefinitions.isEmpty()) {
            for (var pattern : tasks.keySet()) {
                ae2cr$originalPatternDefinitions.add(pattern.getDefinition());
            }
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2cr$writeOriginalPatterns(CallbackInfoReturnable<CompoundTag> cir) {
        var definitions = new ListTag();
        for (var definition : ae2cr$originalPatternDefinitions) {
            definitions.add(definition.toTag());
        }
        cir.getReturnValue().put(AE2CR_ORIGINAL_PATTERNS, definitions);
    }
}
