package io.github.zatone0.ae2craftingrecovery.recovery;

import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * Presents a recalculated plan with initial network withdrawals reduced by
 * exact matching materials that are already physically held in an idle CPU.
 */
public final class CpuInventoryCraftingPlan implements ICraftingPlan {
    private final ICraftingPlan delegate;
    private final KeyCounter usedItems;
    private final KeyCounter missingItems;
    private final boolean simulation;

    private CpuInventoryCraftingPlan(ICraftingPlan delegate, KeyCounter cpuItems) {
        this.delegate = delegate;
        var remainingCpuItems = new KeyCounter();
        remainingCpuItems.addAll(cpuItems);

        this.missingItems = new KeyCounter();
        this.missingItems.addAll(delegate.missingItems());
        boolean originallyMissing = !this.missingItems.isEmpty();
        for (var entry : cpuItems) {
            long missing = this.missingItems.get(entry.getKey());
            if (missing > 0) {
                long supplied = Math.min(missing, entry.getLongValue());
                this.missingItems.remove(entry.getKey(), supplied);
                remainingCpuItems.remove(entry.getKey(), supplied);
            }
        }
        this.missingItems.removeZeros();

        this.usedItems = new KeyCounter();
        this.usedItems.addAll(delegate.usedItems());
        for (var entry : remainingCpuItems) {
            long planned = this.usedItems.get(entry.getKey());
            if (planned > 0) {
                this.usedItems.remove(entry.getKey(), Math.min(planned, entry.getLongValue()));
            }
        }
        this.usedItems.removeZeros();

        // REPORT_MISSING_ITEMS produces a simulation plan when storage is short.
        // It is executable when the exact missing quantities are already physically
        // present in this idle CPU. Never clear simulation for any other reason.
        this.simulation = delegate.simulation() && !(originallyMissing && this.missingItems.isEmpty());
    }

    public static ICraftingPlan accountForCpuInventory(ICraftingPlan plan, KeyCounter cpuItems) {
        if (cpuItems.isEmpty()) {
            return plan;
        }
        return new CpuInventoryCraftingPlan(plan, cpuItems);
    }

    @Override
    public GenericStack finalOutput() {
        return delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return delegate.bytes();
    }

    @Override
    public boolean simulation() {
        return simulation;
    }

    @Override
    public boolean multiplePaths() {
        return delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return usedItems;
    }

    @Override
    public KeyCounter emittedItems() {
        return delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return missingItems;
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return delegate.patternTimes();
    }
}
