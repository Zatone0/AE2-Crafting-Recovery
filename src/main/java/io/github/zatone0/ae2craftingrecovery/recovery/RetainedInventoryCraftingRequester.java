package io.github.zatone0.ae2craftingrecovery.recovery;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;

/** Carries a stable CPU-inventory snapshot into AE2's asynchronous calculator. */
public final class RetainedInventoryCraftingRequester implements ICraftingSimulationRequester {
    private final IActionSource actionSource;
    private final KeyCounter retainedItems = new KeyCounter();

    public RetainedInventoryCraftingRequester(IActionSource actionSource, KeyCounter retainedItems) {
        this.actionSource = actionSource;
        this.retainedItems.addAll(retainedItems);
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    public KeyCounter retainedItems() {
        return retainedItems;
    }
}
