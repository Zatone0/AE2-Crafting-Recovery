package io.github.zatone0.ae2craftingrecovery.recovery;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;

/** Carries a stable CPU-inventory snapshot into AE2's asynchronous calculator. */
public final class RetainedInventoryCraftingRequester implements ICraftingSimulationRequester {
    private final IActionSource actionSource;
    private final KeyCounter retainedItems = new KeyCounter();
    private final Set<AEItemKey> allowedPatternDefinitions = new HashSet<>();

    public RetainedInventoryCraftingRequester(IActionSource actionSource, KeyCounter retainedItems) {
        this(actionSource, retainedItems, Map.of());
    }

    public RetainedInventoryCraftingRequester(IActionSource actionSource, KeyCounter retainedItems,
            Map<IPatternDetails, ?> allowedPatterns) {
        this.actionSource = actionSource;
        this.retainedItems.addAll(retainedItems);
        for (var pattern : allowedPatterns.keySet()) {
            this.allowedPatternDefinitions.add(pattern.getDefinition());
        }
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    public KeyCounter retainedItems() {
        return retainedItems;
    }

    public boolean restrictsPatterns() {
        return !allowedPatternDefinitions.isEmpty();
    }

    public boolean allows(IPatternDetails pattern) {
        return allowedPatternDefinitions.contains(pattern.getDefinition());
    }

    public int allowedPatternCount() {
        return allowedPatternDefinitions.size();
    }
}
