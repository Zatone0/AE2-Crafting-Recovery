package io.github.zatone0.ae2craftingrecovery.recovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.world.level.Level;

/** Finds the smallest remaining operation that can be made runnable from network stock. */
public final class DeadlockTopUpPlanner {
    private DeadlockTopUpPlanner() {
    }

    public static Optional<Candidate> findCandidate(Map<IPatternDetails, Long> remainingTasks,
            ListCraftingInventory cpuInventory, Level level, MEStorage network, IActionSource source,
            Set<Long> excludedSignatures) {
        var candidates = new ArrayList<Candidate>();
        for (var task : remainingTasks.entrySet()) {
            if (task.getValue() <= 0) {
                continue;
            }
            buildCandidate(task.getKey(), task.getValue(), cpuInventory, level, network, source)
                    .ifPresent(candidates::add);
        }
        return candidates.stream()
                .filter(candidate -> !excludedSignatures.contains(candidate.signature()))
                .min(Comparator
                .comparingInt((Candidate c) -> c.missing().size())
                .thenComparingLong(Candidate::totalMissing)
                .thenComparing(Comparator.comparingLong(Candidate::remainingOperations).reversed()));
    }

    /**
     * Finds a missing input that AE2 can calculate as an independent subcraft when
     * no remaining operation can be unlocked directly from network stock.
     */
    public static Optional<SeedRequest> findCraftableSeed(Map<IPatternDetails, Long> remainingTasks,
            ListCraftingInventory cpuInventory, Level level, ICraftingService craftingService,
            Set<Long> excludedSignatures) {
        var requests = new ArrayList<SeedRequest>();
        for (var task : remainingTasks.entrySet()) {
            if (task.getValue() <= 0) {
                continue;
            }
            var snapshot = copy(cpuInventory);
            int missingInputCount = 0;
            var patternRequests = new ArrayList<SeedRequest>();
            for (var input : task.getKey().getInputs()) {
                long remaining = input.getMultiplier();
                for (var template : CraftingCpuHelper.getValidItemTemplates(snapshot, input, level)) {
                    remaining -= CraftingCpuHelper.extractTemplates(snapshot, template, remaining);
                    if (remaining == 0) {
                        break;
                    }
                }
                if (remaining <= 0) {
                    continue;
                }
                missingInputCount++;
                for (var possible : input.getPossibleInputs()) {
                    if (craftingService.isCraftable(possible.what())) {
                        patternRequests.add(new SeedRequest(possible.what(), remaining, task.getKey(),
                                task.getValue(), 0, seedSignature(possible.what(), remaining)));
                    }
                }
            }
            for (var request : patternRequests) {
                requests.add(new SeedRequest(request.key(), request.amount(), request.blockedPattern(),
                        request.remainingOperations(), missingInputCount, request.signature()));
            }
        }
        return requests.stream()
                .filter(request -> !excludedSignatures.contains(request.signature()))
                .min(Comparator
                .comparingInt(SeedRequest::missingInputCount)
                .thenComparingLong(SeedRequest::amount)
                .thenComparing(Comparator.comparingLong(SeedRequest::remainingOperations).reversed()));
    }

    private static Optional<Candidate> buildCandidate(IPatternDetails pattern, long operations,
            ListCraftingInventory cpuInventory, Level level, MEStorage network, IActionSource source) {
        var snapshot = copy(cpuInventory);
        var reserved = new KeyCounter();
        var missing = new ArrayList<MissingInput>();

        for (var input : pattern.getInputs()) {
            long remaining = input.getMultiplier();
            for (var template : CraftingCpuHelper.getValidItemTemplates(snapshot, input, level)) {
                remaining -= CraftingCpuHelper.extractTemplates(snapshot, template, remaining);
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining == 0) {
                continue;
            }

            MissingInput selected = null;
            for (var possible : input.getPossibleInputs()) {
                AEKey key = possible.what();
                long alreadyReserved = reserved.get(key);
                long requested = remaining > Long.MAX_VALUE - alreadyReserved
                        ? Long.MAX_VALUE
                        : remaining + alreadyReserved;
                long available = network.extract(key, requested, Actionable.SIMULATE, source) - alreadyReserved;
                if (available >= remaining) {
                    selected = new MissingInput(key, remaining);
                    break;
                }
            }
            if (selected == null) {
                return Optional.empty();
            }
            reserved.add(selected.key(), selected.amount());
            missing.add(selected);
        }

        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(pattern, operations, List.copyOf(missing), signature(pattern, missing)));
    }

    private static ListCraftingInventory copy(ListCraftingInventory source) {
        var copy = new ListCraftingInventory(key -> {
        });
        for (var entry : source.list) {
            copy.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        return copy;
    }

    private static long signature(IPatternDetails pattern, List<MissingInput> missing) {
        long value = pattern.getDefinition().hashCode();
        for (var input : missing) {
            value = value * 31 + input.key().hashCode();
            value = value * 31 + Long.hashCode(input.amount());
        }
        return value;
    }

    private static long seedSignature(AEKey key, long amount) {
        return ((long) key.hashCode() << 32) ^ Long.hashCode(amount);
    }

    public record MissingInput(AEKey key, long amount) {
    }

    public record Candidate(IPatternDetails pattern, long remainingOperations,
            List<MissingInput> missing, long signature) {
        public long totalMissing() {
            long total = 0;
            for (var input : missing) {
                total = Math.addExact(total, input.amount());
            }
            return total;
        }
    }


    public record SeedRequest(AEKey key, long amount, IPatternDetails blockedPattern,
            long remainingOperations, int missingInputCount, long signature) {
    }
}
