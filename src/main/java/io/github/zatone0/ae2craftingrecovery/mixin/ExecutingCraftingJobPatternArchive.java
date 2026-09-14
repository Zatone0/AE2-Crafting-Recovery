package io.github.zatone0.ae2craftingrecovery.mixin;

import java.util.Collection;
import java.util.Set;

import appeng.api.stacks.AEItemKey;

public interface ExecutingCraftingJobPatternArchive {
    Set<AEItemKey> ae2cr$getOriginalPatternDefinitions();

    void ae2cr$setOriginalPatternDefinitions(Collection<AEItemKey> definitions);
}
