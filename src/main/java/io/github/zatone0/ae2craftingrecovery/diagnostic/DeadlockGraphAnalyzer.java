package io.github.zatone0.ae2craftingrecovery.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;

public final class DeadlockGraphAnalyzer {
    private DeadlockGraphAnalyzer() {
    }

    public static List<String> findRootCycles(
            Map<IPatternDetails, Long> remainingTasks,
            ListCraftingInventory inventory,
            Level level) {
        var missingByPattern = new IdentityHashMap<IPatternDetails, List<BlockedInput>>();
        for (var pattern : remainingTasks.keySet()) {
            missingByPattern.put(pattern, findMissingInputs(pattern, inventory, level));
        }

        var edges = new IdentityHashMap<IPatternDetails, Set<IPatternDetails>>();
        for (var pattern : remainingTasks.keySet()) {
            edges.put(pattern, newIdentitySet());
        }

        for (var consumer : remainingTasks.keySet()) {
            for (var missing : missingByPattern.get(consumer)) {
                for (var producer : remainingTasks.keySet()) {
                    if (producesValidInput(producer, missing.input(), level)) {
                        edges.get(producer).add(consumer);
                    }
                }
            }
        }

        var components = stronglyConnectedComponents(remainingTasks.keySet(), edges);
        var componentOf = new IdentityHashMap<IPatternDetails, Integer>();
        for (int i = 0; i < components.size(); i++) {
            for (var pattern : components.get(i)) {
                componentOf.put(pattern, i);
            }
        }

        var incoming = new boolean[components.size()];
        for (var edge : edges.entrySet()) {
            int from = componentOf.get(edge.getKey());
            for (var target : edge.getValue()) {
                int to = componentOf.get(target);
                if (from != to) {
                    incoming[to] = true;
                }
            }
        }

        var result = new ArrayList<String>();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            if (incoming[i] || !isCycle(component, edges)) {
                continue;
            }

            long operationCount = component.stream().mapToLong(remainingTasks::get).sum();
            var seedOptions = new LinkedHashSet<String>();
            for (var pattern : component) {
                for (var missing : missingByPattern.get(pattern)) {
                    seedOptions.add("add " + missing.description() + " to run " + describePattern(pattern));
                }
            }

            result.add("root cycle: " + component.size() + " patterns / " + operationCount
                    + " remaining operations; seed options=" + seedOptions.stream().limit(8).toList());
        }

        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static List<BlockedInput> findMissingInputs(
            IPatternDetails details,
            ListCraftingInventory inventory,
            Level level) {
        var snapshot = copyInventory(inventory);
        var missing = new ArrayList<BlockedInput>();
        for (var input : details.getInputs()) {
            long remaining = input.getMultiplier();
            for (var template : CraftingCpuHelper.getValidItemTemplates(snapshot, input, level)) {
                remaining -= CraftingCpuHelper.extractTemplates(snapshot, template, remaining);
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining > 0) {
                var alternatives = List.of(input.getPossibleInputs()).stream()
                        .map(GenericStack::toString)
                        .collect(Collectors.joining(" OR "));
                missing.add(new BlockedInput(input, remaining + " unit(s) of " + alternatives));
            }
        }
        return missing;
    }

    private static boolean producesValidInput(
            IPatternDetails producer,
            IPatternDetails.IInput input,
            Level level) {
        for (var output : producer.getOutputs()) {
            if (input.isValid(output.what(), level)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCycle(
            List<IPatternDetails> component,
            Map<IPatternDetails, Set<IPatternDetails>> edges) {
        if (component.size() > 1) {
            return true;
        }
        var only = component.get(0);
        return edges.get(only).contains(only);
    }

    private static List<List<IPatternDetails>> stronglyConnectedComponents(
            Set<IPatternDetails> nodes,
            Map<IPatternDetails, Set<IPatternDetails>> edges) {
        var state = new TarjanState(edges);
        for (var node : nodes) {
            if (!state.index.containsKey(node)) {
                state.visit(node);
            }
        }
        return state.components;
    }

    private static Set<IPatternDetails> newIdentitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static ListCraftingInventory copyInventory(ListCraftingInventory source) {
        var copy = new ListCraftingInventory(key -> {
        });
        for (var entry : source.list) {
            copy.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        return copy;
    }

    private static String describePattern(IPatternDetails details) {
        var inputs = List.of(details.getInputs()).stream()
                .flatMap(input -> List.of(input.getPossibleInputs()).stream().limit(1))
                .map(GenericStack::toString)
                .collect(Collectors.joining(" + "));
        var outputs = List.of(details.getOutputs()).stream()
                .map(GenericStack::toString)
                .collect(Collectors.joining(" + "));
        return inputs + " -> " + outputs;
    }

    private record BlockedInput(IPatternDetails.IInput input, String description) {
    }

    private static final class TarjanState {
        private final Map<IPatternDetails, Set<IPatternDetails>> edges;
        private final IdentityHashMap<IPatternDetails, Integer> index = new IdentityHashMap<>();
        private final IdentityHashMap<IPatternDetails, Integer> lowLink = new IdentityHashMap<>();
        private final Set<IPatternDetails> onStack = newIdentitySet();
        private final ArrayDeque<IPatternDetails> stack = new ArrayDeque<>();
        private final List<List<IPatternDetails>> components = new ArrayList<>();
        private int nextIndex;

        private TarjanState(Map<IPatternDetails, Set<IPatternDetails>> edges) {
            this.edges = edges;
        }

        private void visit(IPatternDetails node) {
            index.put(node, nextIndex);
            lowLink.put(node, nextIndex);
            nextIndex++;
            stack.push(node);
            onStack.add(node);

            for (var target : edges.get(node)) {
                if (!index.containsKey(target)) {
                    visit(target);
                    lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(target)));
                } else if (onStack.contains(target)) {
                    lowLink.put(node, Math.min(lowLink.get(node), index.get(target)));
                }
            }

            if (lowLink.get(node).equals(index.get(node))) {
                var component = new ArrayList<IPatternDetails>();
                IPatternDetails member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (member != node);
                components.add(component);
            }
        }
    }
}
