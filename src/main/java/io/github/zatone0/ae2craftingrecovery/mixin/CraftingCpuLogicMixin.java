package io.github.zatone0.ae2craftingrecovery.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import io.github.zatone0.ae2craftingrecovery.AE2CraftingRecovery;
import io.github.zatone0.ae2craftingrecovery.compat.ExpandedAeHighlightCompat;
import io.github.zatone0.ae2craftingrecovery.compat.ExpandedAeHighlightCompat.ProviderLocation;
import io.github.zatone0.ae2craftingrecovery.config.RecoveryConfig;
import io.github.zatone0.ae2craftingrecovery.diagnostic.DeadlockGraphAnalyzer;
import io.github.zatone0.ae2craftingrecovery.diagnostic.PatternBlockage;
import io.github.zatone0.ae2craftingrecovery.diagnostic.RecoveryDiagnostics;
import io.github.zatone0.ae2craftingrecovery.notification.PendingPlayerAlerts;
import io.github.zatone0.ae2craftingrecovery.notification.DelayedOutputWarningTracker;
import io.github.zatone0.ae2craftingrecovery.recovery.DeadlockTopUpPlanner;
import io.github.zatone0.ae2craftingrecovery.recovery.ExecutingCraftingJobPatternArchive;
import io.github.zatone0.ae2craftingrecovery.recovery.ExecutingCraftingJobReplacementFactory;
import io.github.zatone0.ae2craftingrecovery.recovery.RetainedInventoryCraftingRequester;
import io.github.zatone0.ae2craftingrecovery.recovery.CpuInventoryCraftingPlan;
import io.github.zatone0.ae2craftingrecovery.recovery.TopUpOutcome;
import io.github.zatone0.ae2craftingrecovery.recovery.TaskProgressFactory;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {
    private static final int AE2CR_CONFIRMATION_PASSES = 20;
    private static final int AE2CR_PROVIDER_REJECTION_PASSES = 6000;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Unique
    private int ae2cr$deadlockPasses;

    @Unique
    private ExecutingCraftingJob ae2cr$lastReportedJob;

    @Unique
    private long ae2cr$lastReportedFingerprint;

    @Unique
    private Future<ICraftingPlan> ae2cr$recalculation;

    @Unique
    private boolean ae2cr$recoveryJob;

    @Unique
    private Integer ae2cr$recoveryPlayerId;

    @Unique
    private AEKey ae2cr$recoveryOutput;

    @Unique
    private long ae2cr$recoveryAmount;

    @Unique
    private int ae2cr$recalculationAttempts;

    @Unique
    private ExecutingCraftingJob ae2cr$recoveryOriginalJob;

    @Unique
    private ExecutingCraftingJob ae2cr$topUpJob;

    @Unique
    private final java.util.Set<Long> ae2cr$attemptedTopUps = new HashSet<>();

    @Unique
    private final java.util.Set<Long> ae2cr$attemptedSeedPlans = new HashSet<>();

    @Unique
    private long ae2cr$recoveryFingerprint;

    @Unique
    private long ae2cr$waitingFingerprint;

    @Unique
    private int ae2cr$waitingStablePasses;

    @Unique
    private ExecutingCraftingJob ae2cr$waitingWarningJob;

    @Unique
    private final java.util.Set<AEKey> ae2cr$warnedWaitingOutputs = new HashSet<>();

    @Unique
    private boolean ae2cr$fullReplan;

    @Unique
    private boolean ae2cr$routePreservingReplan;

    @Unique
    private long ae2cr$attemptedFullReplanFingerprint = Long.MIN_VALUE;

    @Unique
    private int ae2cr$providerChecksThisPass;

    @Unique
    private int ae2cr$busyProvidersThisPass;

    @Unique
    private int ae2cr$pushAttemptsThisPass;

    @Unique
    private int ae2cr$acceptedPushesThisPass;

    @Unique
    private final Map<String, Integer> ae2cr$rejectedPushesThisPass = new LinkedHashMap<>();

    @Unique
    private ProviderLocation ae2cr$rejectedProviderLocationThisPass;

    @Unique
    private int ae2cr$providerRejectionPasses;

    @Unique
    private long ae2cr$providerRejectionFingerprint = Long.MIN_VALUE;

    @Unique
    private long ae2cr$alertedProviderRejectionFingerprint = Long.MIN_VALUE;

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void ae2cr$archiveSubmittedPlan(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful() && job != null) {
            var definitions = plan.patternTimes().keySet().stream()
                    .map(IPatternDetails::getDefinition)
                    .collect(Collectors.toSet());
            ((ExecutingCraftingJobPatternArchive) job).ae2cr$setOriginalPatternDefinitions(definitions);
            RecoveryDiagnostics.record("ORIGINAL_PATTERN_ARCHIVE_CREATED cpu=" + ae2cr$cpuPosition()
                    + " definitions=" + definitions.size()
                    + " output=" + plan.finalOutput());
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2cr$recordRecoveryJobCompletion(boolean completed, CallbackInfo ci) {
        if (!ae2cr$recoveryJob || job == null) {
            return;
        }

        var jobView = (ExecutingCraftingJobAccessor) job;
        ElapsedTimeTracker tracker = ((CraftingCpuLogic) (Object) this).getElapsedTimeTracker();
        RecoveryDiagnostics.record((completed ? "RECOVERY_JOB_FINISHED" : "RECOVERY_JOB_CANCELLED")
                + " cpu=" + ae2cr$cpuPosition()
                + " cpuName=\"" + ae2cr$cpuName() + "\""
                + " finalOutput=" + jobView.ae2cr$getFinalOutput()
                + " remainingAmount=" + jobView.ae2cr$getRemainingAmount()
                + " elapsedNanos=" + (tracker == null ? -1 : tracker.getElapsedTime()));
        ae2cr$recoveryJob = false;
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void ae2cr$finishRecalculation(CallbackInfo ci) {
        if (ae2cr$recalculation == null || !ae2cr$recalculation.isDone()) {
            return;
        }

        // Seed calculations augment the existing task ledger. A full replan instead
        // continues below and replaces the job only after an executable plan has
        // been calculated against its physically retained CPU inventory.
        if (!ae2cr$fullReplan && ae2cr$recoveryOutput != null && ae2cr$recoveryAmount > 0) {
            ae2cr$finishSeedCalculation();
            return;
        }

        if (ae2cr$recoveryOriginalJob != null && job != ae2cr$recoveryOriginalJob) {
            ae2cr$recalculation = null;
            ae2cr$clearRecoveryState();
            AE2CraftingRecovery.LOGGER.warn(
                    "AE2 recovery discarded a stale calculation at CPU {} because the active job changed",
                    cluster.getBoundsMin());
            return;
        }

        try {
            var plan = ae2cr$recalculation.get();
            ae2cr$recalculation = null;
            var adjustedPlan = CpuInventoryCraftingPlan.accountForCpuInventory(plan, inventory.list);
            if (adjustedPlan.simulation() || !adjustedPlan.missingItems().isEmpty()) {
                String detailedPreflight = "PREFLIGHT_INCOMPLETE cpu=" + ae2cr$cpuPosition()
                        + " finalOutput=" + adjustedPlan.finalOutput()
                        + " simulation=" + adjustedPlan.simulation()
                        + " bytes=" + adjustedPlan.bytes()
                        + " patterns=" + adjustedPlan.patternTimes().size()
                        + " routePreserving=" + ae2cr$routePreservingReplan
                        + " multiplePaths=" + adjustedPlan.multiplePaths()
                        + " missing=" + ae2cr$describeCounter(adjustedPlan.missingItems(), true)
                        + " plannedNetworkInputs=" + ae2cr$describeCounter(adjustedPlan.usedItems(), false)
                        + " plannedEmissions=" + ae2cr$describeCounter(adjustedPlan.emittedItems(), false)
                        + " retainedCpuInventory=" + ae2cr$describeCounter(inventory.list, false);
                RecoveryDiagnostics.record(detailedPreflight);
                AE2CraftingRecovery.LOGGER.error(
                        "AE2 recovery preflight incomplete at CPU {}: finalOutput={}, patterns={}, missing={}; "
                                + "full evidence is in logs/ae2-crafting-recovery.log",
                        cluster.getBoundsMin(), adjustedPlan.finalOutput(), adjustedPlan.patternTimes().size(),
                        ae2cr$describeCounter(adjustedPlan.missingItems(), true));
                // The original job is still present and untouched. A later change to its
                // task/CPU state will permit another full preflight. Also permit the
                // seed-only fallback immediately for this unchanged fingerprint.
                ae2cr$clearRecoveryState();
                ae2cr$lastReportedJob = null;
                return;
            }

            var recoveryPlayer = ae2cr$getConnectedPlayer(ae2cr$recoveryPlayerId);
            IActionSource submissionSource = recoveryPlayer == null
                    ? cluster.getSrc()
                    : IActionSource.ofPlayer(recoveryPlayer);
            var missingInitialItem = CraftingCpuHelper.tryExtractInitialItems(
                    adjustedPlan, cluster.getGrid(), inventory, submissionSource);
            if (missingInitialItem != null) {
                RecoveryDiagnostics.record("IN_PLACE_REPLAN_EXTRACTION_FAILED cpu=" + ae2cr$cpuPosition()
                        + " missing=" + missingInitialItem
                        + " attempt=" + ae2cr$recalculationAttempts);
                if (ae2cr$recalculationAttempts < 3) {
                    AE2CraftingRecovery.LOGGER.warn(
                            "AE2 recovery in-place replan lost an ingredient at CPU {}; recalculating again ({}/3)",
                            cluster.getBoundsMin(), ae2cr$recalculationAttempts + 1);
                    ae2cr$beginRecoveryCalculation();
                    return;
                }
                AE2CraftingRecovery.LOGGER.error(
                        "AE2 recovery in-place replan could not reserve {} at CPU {}",
                        missingInitialItem, cluster.getBoundsMin());
                ae2cr$clearRecoveryState();
                ae2cr$lastReportedJob = null;
                return;
            }

            Integer recoveryPlayerId = ae2cr$recoveryPlayerId;
            ae2cr$applyPlanToExistingJob(adjustedPlan);
            RecoveryDiagnostics.record("FULL_REPLAN_SUCCESS cpu=" + ae2cr$cpuPosition()
                    + " cpuName=\"" + ae2cr$cpuName() + "\""
                    + " finalOutput=" + adjustedPlan.finalOutput()
                    + " patterns=" + adjustedPlan.patternTimes().size()
                    + " ownershipPreserved=true"
                    + " retainedCpuInventory=" + ae2cr$describeCounter(inventory.list, false));
            ae2cr$recoveryJob = true;
            ae2cr$recoveryOriginalJob = null;
            ae2cr$attemptedFullReplanFingerprint = Long.MIN_VALUE;
            ae2cr$deadlockPasses = 0;
            AE2CraftingRecovery.LOGGER.warn(
                    "AE2 recovery recalculation replaced the task plan in place at CPU {}: finalOutput={}",
                    cluster.getBoundsMin(), adjustedPlan.finalOutput());
            ae2cr$alertPlayer(recoveryPlayerId,
                    Component.literal("AE2 recovery replanned the stalled craft at CPU ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(ae2cr$cpuLabel()).withStyle(ChatFormatting.YELLOW)));
            ae2cr$clearRecoveryState();
        } catch (Exception e) {
            Integer recoveryPlayerId = ae2cr$recoveryPlayerId;
            ae2cr$recalculation = null;
            ae2cr$lastReportedJob = null;
            AE2CraftingRecovery.LOGGER.error(
                    "AE2 recovery recalculation failed at CPU {}", cluster.getBoundsMin(), e);
            ae2cr$alertPlayer(recoveryPlayerId,
                    Component.literal("AE2 recovery failed while replanning the craft at CPU ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(ae2cr$cpuLabel()).withStyle(ChatFormatting.YELLOW)));
            ae2cr$clearRecoveryState();
        }
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void ae2cr$detectDeadlock(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() != 0) {
            // A previously supplied seed was consumed and the task ledger advanced.
            // Permit the same cyclic pattern to receive another seed if it reaches a
            // later deadlock. Keeping signatures only across zero-progress passes still
            // prevents repeatedly draining the network into a genuinely stuck CPU.
            // Keep successful candidates in the per-job rotation. Clearing them here
            // made the cheapest recyclable branch win again after every operation,
            // starving the rest of a large dependency graph.
            ae2cr$deadlockPasses = 0;
            ae2cr$resetProviderRejectionTracking();
            return;
        }

        if (job == null) {
            ae2cr$deadlockPasses = 0;
            return;
        }

        var jobView = (ExecutingCraftingJobAccessor) job;
        var tasks = jobView.ae2cr$getTasks();
        if (ae2cr$hasPositiveEntries(jobView.ae2cr$getWaitingFor().list)) {
            ae2cr$monitorWaitingOutputs(jobView);
            ae2cr$deadlockPasses = 0;
            // An outstanding output means AE2 has already dispatched real work. Its
            // duration cannot prove that the work was lost: a machine may be slow,
            // starved of fuel, paused, or otherwise externally gated. Never submit
            // replacement work or top-ups while any output is still in flight.
            return;
        }
        ae2cr$waitingStablePasses = 0;
        ae2cr$waitingFingerprint = 0;
        if (tasks.isEmpty()) {
            ae2cr$deadlockPasses = 0;
            return;
        }

        var snapshot = ae2cr$copyInventory(inventory);
        if (ae2cr$hasRunnableTask(tasks, snapshot)) {
            ae2cr$deadlockPasses = 0;
            ae2cr$monitorProviderRejections(jobView, tasks);
            return;
        }

        ae2cr$resetProviderRejectionTracking();

        ae2cr$deadlockPasses++;
        if (ae2cr$deadlockPasses >= AE2CR_CONFIRMATION_PASSES) {
            long fingerprint = ae2cr$fingerprint(jobView, tasks);
            if (ae2cr$lastReportedJob == job && ae2cr$lastReportedFingerprint == fingerprint) {
                return;
            }
            if (ae2cr$topUpJob != job) {
                ae2cr$topUpJob = job;
                ae2cr$attemptedTopUps.clear();
                ae2cr$attemptedSeedPlans.clear();
            }
            TopUpOutcome topUp;
            // Prefer a fresh plan for the entire unfinished request. This lets AE2
            // reorder the dependency graph around everything already retained by the
            // CPU instead of unlocking a very large craft one operation at a time.
            if (ae2cr$tryFullRecalculate(jobView)) {
                topUp = TopUpOutcome.RECALCULATING;
            } else {
                topUp = ae2cr$tryTransactionalTopUp(tasks, jobView);
                if (topUp == TopUpOutcome.UNAVAILABLE && ae2cr$tryRecalculate(jobView)) {
                    topUp = TopUpOutcome.RECALCULATING;
                }
            }
            ae2cr$logDeadlock(jobView, tasks, topUp);
            ae2cr$lastReportedJob = job;
            ae2cr$lastReportedFingerprint = fingerprint;
            if (topUp == TopUpOutcome.SUCCESS) {
                ae2cr$deadlockPasses = 0;
            }
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void ae2cr$beginProviderObservation(CallbackInfoReturnable<Integer> cir) {
        ae2cr$providerChecksThisPass = 0;
        ae2cr$busyProvidersThisPass = 0;
        ae2cr$pushAttemptsThisPass = 0;
        ae2cr$acceptedPushesThisPass = 0;
        ae2cr$rejectedPushesThisPass.clear();
        ae2cr$rejectedProviderLocationThisPass = null;
    }

    @Redirect(
            method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;isBusy()Z"))
    private boolean ae2cr$observeProviderBusy(ICraftingProvider provider) {
        ae2cr$providerChecksThisPass++;
        boolean busy = provider.isBusy();
        if (busy) {
            ae2cr$busyProvidersThisPass++;
        }
        return busy;
    }

    @Redirect(
            method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean ae2cr$observePatternPush(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputHolder) {
        ae2cr$pushAttemptsThisPass++;
        boolean accepted = provider.pushPattern(pattern, inputHolder);
        if (accepted) {
            ae2cr$acceptedPushesThisPass++;
        } else {
            String key = ae2cr$describePattern(pattern) + " provider=" + provider.getClass().getName();
            ae2cr$rejectedPushesThisPass.merge(key, 1, Integer::sum);
            if (ae2cr$rejectedProviderLocationThisPass == null) {
                ae2cr$rejectedProviderLocationThisPass = ExpandedAeHighlightCompat.locate(provider);
            }
        }
        return accepted;
    }

    @Unique
    private void ae2cr$monitorProviderRejections(ExecutingCraftingJobAccessor jobView,
            Map<IPatternDetails, Object> tasks) {
        // Busy providers are normal shared-network contention. Only an actual push
        // to a provider that reports itself idle and then rejects the pattern is a
        // machine/provider-side blockage.
        if (ae2cr$acceptedPushesThisPass > 0 || ae2cr$pushAttemptsThisPass == 0) {
            ae2cr$resetProviderRejectionTracking();
            return;
        }

        long fingerprint = ae2cr$fingerprint(jobView, tasks);
        for (var entry : ae2cr$rejectedPushesThisPass.entrySet()) {
            fingerprint ^= ae2cr$mix64(((long) entry.getKey().hashCode() << 32) ^ entry.getValue());
        }
        if (fingerprint != ae2cr$providerRejectionFingerprint) {
            ae2cr$providerRejectionFingerprint = fingerprint;
            ae2cr$providerRejectionPasses = 1;
            return;
        }
        ae2cr$providerRejectionPasses++;
        if (ae2cr$providerRejectionPasses < AE2CR_PROVIDER_REJECTION_PASSES
                || ae2cr$alertedProviderRejectionFingerprint == fingerprint) {
            return;
        }

        ae2cr$alertedProviderRejectionFingerprint = fingerprint;
        String rejected = ae2cr$rejectedPushesThisPass.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey())
                .collect(Collectors.joining("; "));
        RecoveryDiagnostics.record("PROVIDER_REJECTION_STALL cpu=" + ae2cr$cpuPosition()
                + " output=" + jobView.ae2cr$getFinalOutput()
                + " elapsedPasses=" + ae2cr$providerRejectionPasses
                + " providerChecks=" + ae2cr$providerChecksThisPass
                + " busyProviders=" + ae2cr$busyProvidersThisPass
                + " providerLocation=" + ae2cr$rejectedProviderLocationThisPass
                + " rejectedPushes=[" + rejected + "]");
        AE2CraftingRecovery.LOGGER.error(
                "AE2 provider repeatedly rejected a ready pattern for five minutes at CPU {}: {}; "
                        + "check the target machine inputs, recipe state, or provider blocking mode",
                cluster.getBoundsMin(), rejected);
        var player = ae2cr$getConnectedPlayer(jobView.ae2cr$getPlayerId());
        var alert = Component.literal("AE2 machine/provider rejected a ready pattern at CPU ")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(ae2cr$cpuLabel()).withStyle(ChatFormatting.YELLOW));
        if (player != null && ae2cr$rejectedProviderLocationThisPass != null) {
            int token = ExpandedAeHighlightCompat.remember(player, ae2cr$rejectedProviderLocationThisPass);
            alert.append(Component.literal("; provider " + ae2cr$rejectedProviderLocationThisPass + " ")
                    .withStyle(ChatFormatting.GOLD));
            alert.append(Component.literal("[Highlight]").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ae2cr highlight " + token))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Highlight this provider with ExpandedAE")))));
        } else if (ae2cr$rejectedProviderLocationThisPass == null) {
            alert.append(Component.literal("; provider location unavailable").withStyle(ChatFormatting.GRAY));
        }
        ae2cr$alertPlayer(jobView.ae2cr$getPlayerId(), alert);
        ae2cr$playAlertSound(jobView.ae2cr$getPlayerId());
    }

    @Unique
    private void ae2cr$resetProviderRejectionTracking() {
        ae2cr$providerRejectionPasses = 0;
        ae2cr$providerRejectionFingerprint = Long.MIN_VALUE;
    }

    @Unique
    private TopUpOutcome ae2cr$tryTransactionalTopUp(
            Map<IPatternDetails, Object> tasks, ExecutingCraftingJobAccessor jobView) {
        var remaining = new java.util.IdentityHashMap<IPatternDetails, Long>();
        for (var entry : tasks.entrySet()) {
            long count = ((TaskProgressAccessor) entry.getValue()).ae2cr$getValue();
            if (count > 0) {
                remaining.put(entry.getKey(), count);
            }
        }

        var player = ae2cr$getConnectedPlayer(jobView.ae2cr$getPlayerId());
        IActionSource source = player == null ? cluster.getSrc() : IActionSource.ofPlayer(player);
        var network = cluster.getGrid().getStorageService().getInventory();
        var candidate = DeadlockTopUpPlanner.findCandidate(
                remaining, inventory, cluster.getLevel(), network, source, ae2cr$attemptedTopUps).orElse(null);
        if (candidate == null && !ae2cr$attemptedTopUps.isEmpty()) {
            // A full rotation has been attempted. Only then allow candidates to repeat.
            var unrestricted = DeadlockTopUpPlanner.findCandidate(
                    remaining, inventory, cluster.getLevel(), network, source, java.util.Set.of()).orElse(null);
            if (unrestricted != null) {
                ae2cr$attemptedTopUps.clear();
                candidate = unrestricted;
            }
        }
        if (candidate == null) {
            RecoveryDiagnostics.record("TOP_UP_UNAVAILABLE cpu=" + ae2cr$cpuPosition()
                    + " output=" + jobView.ae2cr$getFinalOutput());
            return TopUpOutcome.UNAVAILABLE;
        }
        if (!ae2cr$attemptedTopUps.add(candidate.signature())) {
            RecoveryDiagnostics.record("TOP_UP_REPEATED cpu=" + ae2cr$cpuPosition()
                    + " pattern=" + ae2cr$describePattern(candidate.pattern())
                    + " inputs=" + candidate.missing());
            return TopUpOutcome.REPEATED;
        }

        var extracted = new KeyCounter();
        for (var input : candidate.missing()) {
            long amount = network.extract(input.key(), input.amount(), Actionable.MODULATE, source);
            if (amount > 0) {
                extracted.add(input.key(), amount);
            }
            if (amount != input.amount()) {
                ae2cr$rollbackTopUp(network, source, extracted);
                RecoveryDiagnostics.record("TOP_UP_RACE_ABORT cpu=" + ae2cr$cpuPosition()
                        + " wanted=" + input.amount() + "x " + input.key() + " extracted=" + amount);
                return TopUpOutcome.UNAVAILABLE;
            }
        }
        for (var entry : extracted) {
            inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        cluster.markDirty();
        String message = "TOP_UP_SUCCESS cpu=" + ae2cr$cpuPosition()
                + " output=" + jobView.ae2cr$getFinalOutput()
                + " pattern=" + ae2cr$describePattern(candidate.pattern())
                + " batchOperations=" + candidate.batchOperations()
                + " inputs=" + candidate.missing();
        RecoveryDiagnostics.record(message);
        AE2CraftingRecovery.LOGGER.warn("AE2 recovery supplied a transactional seed set without cancelling job at CPU {}: {}",
                cluster.getBoundsMin(), candidate.missing());
        return TopUpOutcome.SUCCESS;
    }

    @Unique
    private void ae2cr$rollbackTopUp(appeng.api.storage.MEStorage network,
            IActionSource source, KeyCounter extracted) {
        for (var entry : extracted) {
            long restored = network.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            long remainder = entry.getLongValue() - restored;
            if (remainder > 0) {
                // Never void an item if the storage topology changed during rollback.
                inventory.insert(entry.getKey(), remainder, Actionable.MODULATE);
            }
        }
    }

    @Unique
    private void ae2cr$monitorWaitingOutputs(ExecutingCraftingJobAccessor jobView) {
        if (!RecoveryConfig.DELAYED_OUTPUT_WARNINGS.get()) {
            return;
        }
        var waiting = jobView.ae2cr$getWaitingFor().list;
        if (ae2cr$waitingWarningJob != job) {
            ae2cr$waitingWarningJob = job;
            ae2cr$warnedWaitingOutputs.clear();
            ae2cr$waitingFingerprint = 0;
            ae2cr$waitingStablePasses = 0;
        }
        long fingerprint = 0;
        for (var entry : waiting) {
            fingerprint ^= ae2cr$mix64(((long) entry.getKey().hashCode() << 32) ^ entry.getLongValue());
        }
        if (fingerprint != ae2cr$waitingFingerprint) {
            ae2cr$waitingFingerprint = fingerprint;
            ae2cr$waitingStablePasses = 0;
            return;
        }
        ae2cr$waitingStablePasses++;

        // Duration alone is diagnostic evidence, never recovery authority. Warn once
        // per output key for this job after the configured unchanged-wait duration.
        if (ae2cr$waitingStablePasses >= RecoveryConfig.delayedOutputWarningTicks()) {
            for (var entry : waiting) {
                if (!ae2cr$warnedWaitingOutputs.add(entry.getKey())) {
                    continue;
                }
                RecoveryDiagnostics.record("WAITING_OUTPUT_DELAYED cpu=" + ae2cr$cpuPosition()
                        + " output=" + jobView.ae2cr$getFinalOutput()
                        + " delayedKey=" + entry.getKey()
                        + " delayedAmount=" + entry.getLongValue()
                        + " waiting=" + ae2cr$describeCounter(waiting, true));
                boolean grouped = RecoveryConfig.GROUP_REPEATED_DELAYED_OUTPUTS.get()
                        && !DelayedOutputWarningTracker.shouldNotify(
                                jobView.ae2cr$getPlayerId(), entry.getKey(), cluster.getLevel().getGameTime(),
                                RecoveryConfig.repeatedOutputGroupTicks());
                if (grouped) {
                    RecoveryDiagnostics.record("WAITING_OUTPUT_DELAYED_GROUPED cpu=" + ae2cr$cpuPosition()
                            + " delayedKey=" + entry.getKey()
                            + " playerId=" + jobView.ae2cr$getPlayerId());
                    continue;
                }
                int delayMinutes = RecoveryConfig.DELAYED_OUTPUT_WARNING_MINUTES.get();
                AE2CraftingRecovery.LOGGER.warn(
                        "AE2 crafting CPU {} has waited at least {} minutes for {}x {}; warning only, no recovery work was submitted",
                        cluster.getBoundsMin(), delayMinutes, entry.getLongValue(), entry.getKey());
                ae2cr$alertPlayer(jobView.ae2cr$getPlayerId(),
                        Component.literal("AE2 crafting CPU " + ae2cr$cpuLabel()
                                + " has waited over " + delayMinutes + " minutes for ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(entry.getKey().getDisplayName().copy().withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal(". Is its machine stalled or sharing a busy provider?")
                                        .withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    @Unique
    private boolean ae2cr$tryRecalculate(ExecutingCraftingJobAccessor jobView) {
        if (ae2cr$recalculation != null) {
            return true;
        }

        var remaining = new java.util.IdentityHashMap<IPatternDetails, Long>();
        for (var entry : jobView.ae2cr$getTasks().entrySet()) {
            long count = ((TaskProgressAccessor) entry.getValue()).ae2cr$getValue();
            if (count > 0) {
                remaining.put(entry.getKey(), count);
            }
        }
        var crafting = cluster.getGrid().getCraftingService();
        var request = DeadlockTopUpPlanner.findCraftableSeed(
                remaining, inventory, cluster.getLevel(), crafting, ae2cr$attemptedSeedPlans).orElse(null);
        if (request == null && !ae2cr$attemptedSeedPlans.isEmpty()) {
            var unrestricted = DeadlockTopUpPlanner.findCraftableSeed(
                    remaining, inventory, cluster.getLevel(), crafting, java.util.Set.of()).orElse(null);
            if (unrestricted != null) {
                ae2cr$attemptedSeedPlans.clear();
                request = unrestricted;
            }
        }
        if (request == null) {
            RecoveryDiagnostics.record("SEED_PLAN_UNAVAILABLE cpu=" + ae2cr$cpuPosition()
                    + " output=" + jobView.ae2cr$getFinalOutput());
            return false;
        }

        ae2cr$attemptedSeedPlans.add(request.signature());
        ae2cr$recoveryPlayerId = jobView.ae2cr$getPlayerId();
        ae2cr$recoveryOutput = request.key();
        ae2cr$recoveryAmount = request.amount();
        ae2cr$recalculationAttempts = 0;
        ae2cr$recoveryOriginalJob = job;
        ae2cr$recoveryFingerprint = ae2cr$fingerprint(jobView, jobView.ae2cr$getTasks());
        RecoveryDiagnostics.record("SEED_PLAN_STARTED cpu=" + ae2cr$cpuPosition()
                + " seed=" + request.amount() + "x " + request.key()
                + " blockedPattern=" + ae2cr$describePattern(request.blockedPattern()));
        ae2cr$beginRecoveryCalculation();
        return true;
    }

    /**
     * Reissues the unfinished final request as a new AE2 calculation while exposing
     * a snapshot of the old CPU inventory only to that calculation. The inventory
     * remains physically inside the CPU until preflight succeeds, so neither normal
     * storage nor another crafting job can consume the retained intermediates.
     */
    @Unique
    private boolean ae2cr$tryFullRecalculate(ExecutingCraftingJobAccessor jobView) {
        if (ae2cr$recalculation != null) {
            return true;
        }
        GenericStack finalOutput = jobView.ae2cr$getFinalOutput();
        long remainingAmount = jobView.ae2cr$getRemainingAmount();
        if (finalOutput == null || remainingAmount <= 0) {
            return false;
        }
        long fingerprint = ae2cr$fingerprint(jobView, jobView.ae2cr$getTasks());
        if (ae2cr$attemptedFullReplanFingerprint == fingerprint) {
            return false;
        }

        ae2cr$attemptedFullReplanFingerprint = fingerprint;
        ae2cr$fullReplan = true;
        ae2cr$routePreservingReplan = true;
        ae2cr$recoveryPlayerId = jobView.ae2cr$getPlayerId();
        ae2cr$recoveryOutput = finalOutput.what();
        ae2cr$recoveryAmount = remainingAmount;
        ae2cr$recalculationAttempts = 0;
        ae2cr$recoveryOriginalJob = job;
        ae2cr$recoveryFingerprint = fingerprint;
        RecoveryDiagnostics.record("FULL_REPLAN_STARTED cpu=" + ae2cr$cpuPosition()
                + " request=" + remainingAmount + "x " + finalOutput.what()
                + " route=original-patterns "
                + " retainedCpuInventory=" + ae2cr$describeCounter(inventory.list, false));
        ae2cr$beginRecoveryCalculation();
        return true;
    }

    @Unique
    private void ae2cr$finishSeedCalculation() {
        var originalJob = ae2cr$recoveryOriginalJob;
        var requestedKey = ae2cr$recoveryOutput;
        long requestedAmount = ae2cr$recoveryAmount;
        try {
            var calculated = ae2cr$recalculation.get();
            ae2cr$recalculation = null;
            if (job != originalJob || job == null) {
                RecoveryDiagnostics.record("SEED_PLAN_STALE_JOB cpu=" + ae2cr$cpuPosition());
                ae2cr$clearRecoveryState();
                return;
            }
            var jobView = (ExecutingCraftingJobAccessor) job;
            long currentFingerprint = ae2cr$fingerprint(jobView, jobView.ae2cr$getTasks());
            if (currentFingerprint != ae2cr$recoveryFingerprint) {
                RecoveryDiagnostics.record("SEED_PLAN_STALE_PROGRESS cpu=" + ae2cr$cpuPosition()
                        + " seed=" + requestedAmount + "x " + requestedKey);
                ae2cr$clearRecoveryState();
                ae2cr$lastReportedJob = null;
                return;
            }

            var plan = CpuInventoryCraftingPlan.accountForCpuInventory(calculated, inventory.list);
            if (plan.simulation() || !plan.missingItems().isEmpty() || plan.patternTimes().isEmpty()) {
                RecoveryDiagnostics.record("SEED_PLAN_INCOMPLETE cpu=" + ae2cr$cpuPosition()
                        + " seed=" + requestedAmount + "x " + requestedKey
                        + " patterns=" + plan.patternTimes().size()
                        + " missing=" + ae2cr$describeCounter(plan.missingItems(), true));
                ae2cr$clearRecoveryState();
                // Keep the unchanged-state report guard. Retrying another seed every
                // tick caused complete candidate sweeps and multi-megabyte log bursts
                // when AE2 could not resolve any of them. A real task or inventory
                // change produces a new fingerprint and permits another attempt.
                return;
            }

            // Prepare every task mutation before moving network contents. This makes
            // reflection and overflow failures preflight failures, not partial commits.
            var preparedProgress = new java.util.IdentityHashMap<IPatternDetails, TaskProgressAccessor>();
            var preparedValues = new java.util.IdentityHashMap<IPatternDetails, Long>();
            for (var entry : plan.patternTimes().entrySet()) {
                var existing = jobView.ae2cr$getTasks().get(entry.getKey());
                if (existing == null) {
                    preparedProgress.put(entry.getKey(), (TaskProgressAccessor) TaskProgressFactory.create());
                    preparedValues.put(entry.getKey(), entry.getValue());
                } else {
                    preparedValues.put(entry.getKey(), Math.addExact(
                            ((TaskProgressAccessor) existing).ae2cr$getValue(), entry.getValue()));
                }
            }
            for (var entry : plan.emittedItems()) {
                long addition = ae2cr$expectedOutputAddition(entry.getKey(), entry.getLongValue());
                Math.addExact(jobView.ae2cr$getWaitingFor().list.get(entry.getKey()), addition);
            }

            var player = ae2cr$getConnectedPlayer(ae2cr$recoveryPlayerId);
            IActionSource source = player == null ? cluster.getSrc() : IActionSource.ofPlayer(player);
            var network = cluster.getGrid().getStorageService().getInventory();
            var extracted = new KeyCounter();
            for (var entry : plan.usedItems()) {
                long available = network.extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source);
                if (available != entry.getLongValue()) {
                    RecoveryDiagnostics.record("SEED_PLAN_STORAGE_CHANGED cpu=" + ae2cr$cpuPosition()
                            + " seed=" + requestedAmount + "x " + requestedKey
                            + " missing=" + entry.getLongValue() + "x " + entry.getKey());
                    ae2cr$clearRecoveryState();
                    ae2cr$lastReportedJob = null;
                    return;
                }
            }
            for (var entry : plan.usedItems()) {
                long amount = network.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
                if (amount > 0) {
                    extracted.add(entry.getKey(), amount);
                }
                if (amount != entry.getLongValue()) {
                    ae2cr$rollbackTopUp(network, source, extracted);
                    RecoveryDiagnostics.record("SEED_PLAN_RACE_ABORT cpu=" + ae2cr$cpuPosition()
                            + " seed=" + requestedAmount + "x " + requestedKey);
                    ae2cr$clearRecoveryState();
                    ae2cr$lastReportedJob = null;
                    return;
                }
            }

            for (var entry : plan.patternTimes().entrySet()) {
                var progress = jobView.ae2cr$getTasks().get(entry.getKey());
                if (progress == null) {
                    var created = preparedProgress.get(entry.getKey());
                    created.ae2cr$setValue(preparedValues.get(entry.getKey()));
                    jobView.ae2cr$getTasks().put(entry.getKey(), created);
                } else {
                    ((TaskProgressAccessor) progress).ae2cr$setValue(preparedValues.get(entry.getKey()));
                }
            }
            for (var entry : plan.emittedItems()) {
                long addition = ae2cr$expectedOutputAddition(entry.getKey(), entry.getLongValue());
                if (addition > 0) {
                    jobView.ae2cr$getWaitingFor().insert(entry.getKey(), addition, Actionable.MODULATE);
                }
            }
            for (var entry : extracted) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
            cluster.markDirty();
            RecoveryDiagnostics.record("SEED_PLAN_MERGED cpu=" + ae2cr$cpuPosition()
                    + " seed=" + requestedAmount + "x " + requestedKey
                    + " addedPatterns=" + plan.patternTimes().size()
                    + " networkInputs=" + ae2cr$describeCounter(plan.usedItems(), false));
            ae2cr$clearRecoveryState();
            ae2cr$deadlockPasses = 0;
            ae2cr$lastReportedJob = null;
        } catch (Exception e) {
            ae2cr$recalculation = null;
            AE2CraftingRecovery.LOGGER.error("AE2 seed-plan recovery failed at CPU {}", cluster.getBoundsMin(), e);
            RecoveryDiagnostics.record("SEED_PLAN_EXCEPTION cpu=" + ae2cr$cpuPosition()
                    + " seed=" + requestedAmount + "x " + requestedKey + " error=" + e);
            ae2cr$clearRecoveryState();
            ae2cr$lastReportedJob = null;
        }
    }

    @Unique
    private long ae2cr$expectedOutputAddition(AEKey key, long emittedAmount) {
        return emittedAmount;
    }

    @Unique
    private void ae2cr$applyPlanToExistingJob(ICraftingPlan plan) {
        if (job == null) {
            throw new IllegalStateException("Cannot replace a recovery plan without an active AE2 job");
        }

        var oldJobView = (ExecutingCraftingJobAccessor) job;
        long elapsedTime = ((CraftingCpuLogic) (Object) this).getElapsedTimeTracker().getElapsedTime();
        var replacement = ExecutingCraftingJobReplacementFactory.create(
                plan,
                (CraftingCpuLogic) (Object) this,
                oldJobView.ae2cr$getLink(),
                oldJobView.ae2cr$getPlayerId());

        ((ElapsedTimeTrackerAccessor) ((ExecutingCraftingJobAccessor) replacement).ae2cr$getTimeTracker())
                .ae2cr$setElapsedTime(elapsedTime);
        var definitions = plan.patternTimes().keySet().stream()
                .map(IPatternDetails::getDefinition)
                .collect(Collectors.toSet());
        ((ExecutingCraftingJobPatternArchive) replacement).ae2cr$setOriginalPatternDefinitions(definitions);

        // This is the ownership-preserving transition: the original job is never
        // cancelled, its CraftingLink is reused, and the CPU inventory is untouched.
        job = replacement;
        cluster.updateOutput(plan.finalOutput());
        cluster.markDirty();
    }

    @Unique
    private void ae2cr$clearRecoveryState() {
        ae2cr$recoveryOriginalJob = null;
        ae2cr$recoveryOutput = null;
        ae2cr$recoveryAmount = 0;
        ae2cr$recalculationAttempts = 0;
        ae2cr$fullReplan = false;
        ae2cr$routePreservingReplan = false;
    }

    @Unique
    private String ae2cr$describeCounter(KeyCounter counter, boolean includeLocations) {
        if (counter == null || counter.isEmpty()) {
            return "[]";
        }
        var storage = cluster.getGrid().getStorageService().getCachedInventory();
        var entries = new ArrayList<String>();
        int omitted = 0;
        for (var entry : counter) {
            if (entries.size() >= 80) {
                omitted++;
                continue;
            }
            var key = entry.getKey();
            if (includeLocations) {
                entries.add(entry.getLongValue() + "x " + key
                        + " {network=" + storage.get(key) + ", cpu=" + inventory.list.get(key) + "}");
            } else {
                entries.add(entry.getLongValue() + "x " + key);
            }
        }
        if (omitted > 0) {
            entries.add("... " + omitted + " additional key(s) omitted");
        }
        return entries.toString();
    }

    @Unique
    private void ae2cr$beginRecoveryCalculation() {
        ae2cr$recalculationAttempts++;
        var recoveryPlayer = ae2cr$getConnectedPlayer(ae2cr$recoveryPlayerId);
        IActionSource recoverySource = recoveryPlayer == null
                ? cluster.getSrc()
                : IActionSource.ofPlayer(recoveryPlayer);
        Iterable<appeng.api.stacks.AEItemKey> allowedPatternDefinitions = List.of();
        if (ae2cr$routePreservingReplan && ae2cr$recoveryOriginalJob != null) {
            allowedPatternDefinitions = ((ExecutingCraftingJobPatternArchive) ae2cr$recoveryOriginalJob)
                    .ae2cr$getOriginalPatternDefinitions();
        }
        var requester = new RetainedInventoryCraftingRequester(
                recoverySource, cluster.getNode(), inventory.list, allowedPatternDefinitions);
        RecoveryDiagnostics.record("RECOVERY_CALCULATION_REQUEST cpu=" + ae2cr$cpuPosition()
                + " output=" + ae2cr$recoveryAmount + "x " + ae2cr$recoveryOutput
                + " routePreserving=" + ae2cr$routePreservingReplan
                + " allowedPatternDefinitions=" + requester.allowedPatternCount()
                + " gridNodePresent=" + (requester.getGridNode() != null));
        ae2cr$recalculation = cluster.getGrid().getCraftingService().beginCraftingCalculation(
                cluster.getLevel(), requester, ae2cr$recoveryOutput, ae2cr$recoveryAmount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    @Unique
    private long ae2cr$fingerprint(ExecutingCraftingJobAccessor jobView, Map<IPatternDetails, Object> tasks) {
        long fingerprint = ae2cr$mix64(jobView.ae2cr$getRemainingAmount());
        for (var entry : inventory.list) {
            long component = ((long) entry.getKey().hashCode() << 32) ^ entry.getLongValue();
            fingerprint ^= ae2cr$mix64(component);
        }
        for (var entry : tasks.entrySet()) {
            long count = ((TaskProgressAccessor) entry.getValue()).ae2cr$getValue();
            long component = ((long) entry.getKey().getDefinition().hashCode() << 32) ^ count;
            fingerprint ^= ae2cr$mix64(component);
        }
        return fingerprint;
    }

    @Unique
    private static long ae2cr$mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    @Unique
    private boolean ae2cr$hasRunnableTask(Map<IPatternDetails, Object> tasks, ListCraftingInventory snapshot) {
        for (var entry : tasks.entrySet()) {
            if (((TaskProgressAccessor) entry.getValue()).ae2cr$getValue() <= 0) {
                continue;
            }

            var expectedOutputs = new KeyCounter();
            var expectedContainers = new KeyCounter();
            var extracted = CraftingCpuHelper.extractPatternInputs(
                    entry.getKey(), snapshot, cluster.getLevel(), expectedOutputs, expectedContainers);
            if (extracted != null) {
                CraftingCpuHelper.reinjectPatternInputs(snapshot, extracted);
                return true;
            }
        }
        return false;
    }

    @Unique
    private static ListCraftingInventory ae2cr$copyInventory(ListCraftingInventory source) {
        var copy = new ListCraftingInventory(key -> {
        });
        for (var entry : source.list) {
            copy.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        return copy;
    }

    @Unique
    private static boolean ae2cr$hasPositiveEntries(KeyCounter counter) {
        for (var entry : counter) {
            if (entry.getLongValue() > 0) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void ae2cr$logDeadlock(ExecutingCraftingJobAccessor jobView,
            Map<IPatternDetails, Object> tasks, TopUpOutcome topUpOutcome) {
        var blockedByInput = new LinkedHashMap<String, Long>();
        var rootShortages = new LinkedHashMap<String, Long>();
        var analyses = new ArrayList<PatternBlockage>();
        var remainingOutputs = ae2cr$getRemainingOutputs(tasks);
        long remainingOperations = 0;
        for (var entry : tasks.entrySet()) {
            long count = ((TaskProgressAccessor) entry.getValue()).ae2cr$getValue();
            if (count <= 0) {
                continue;
            }
            remainingOperations += count;
            var analysis = ae2cr$analyzePattern(entry.getKey(), count, remainingOutputs);
            analyses.add(analysis);
            for (var missing : analysis.missingInputs()) {
                blockedByInput.merge(missing, count, Long::sum);
            }
            for (var root : analysis.rootMissingInputs()) {
                rootShortages.merge(root, count, Long::sum);
            }
        }

        List<String> largestStored = new ArrayList<>();
        for (var entry : inventory.list) {
            largestStored.add(entry.getLongValue() + "x " + entry.getKey());
        }
        largestStored.sort(Comparator.comparingLong(CraftingCpuLogicMixin::ae2cr$leadingCount).reversed());

        var missingFrontier = blockedByInput.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(e -> e.getValue() + " blocked operations need " + e.getKey())
                .toList();

        var likelyRootShortages = rootShortages.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(e -> "add " + e.getKey() + " (no remaining producer; blocks " + e.getValue() + " operations)")
                .toList();

        analyses.sort(Comparator
                .comparingInt((PatternBlockage a) -> a.missingInputs().size())
                .thenComparing(Comparator.comparingLong(PatternBlockage::remainingOperations).reversed()));
        var smallestUnlocks = analyses.stream()
                .limit(12)
                .map(a -> "add [" + String.join(" + ", a.missingInputs()) + "] to unlock one of "
                        + a.remainingOperations() + "x " + a.patternDescription())
                .toList();

        var taskCounts = new java.util.IdentityHashMap<IPatternDetails, Long>();
        for (var entry : tasks.entrySet()) {
            long count = ((TaskProgressAccessor) entry.getValue()).ae2cr$getValue();
            if (count > 0) {
                taskCounts.put(entry.getKey(), count);
            }
        }
        var rootDependencyCycles = DeadlockGraphAnalyzer.findRootCycles(taskCounts, inventory, cluster.getLevel());

        GenericStack output = jobView.ae2cr$getFinalOutput();
        if (topUpOutcome != TopUpOutcome.SUCCESS && topUpOutcome != TopUpOutcome.RECALCULATING) {
            AE2CraftingRecovery.LOGGER.error(
                    "PROVEN AE2 CRAFTING DEADLOCK at CPU {}: finalOutput={}, remainingOperations={}, recovery={}; "
                            + "full evidence is in logs/ae2-crafting-recovery.log",
                    cluster.getBoundsMin(), output, remainingOperations, topUpOutcome);
        }

        RecoveryDiagnostics.record("DEADLOCK cpu=" + ae2cr$cpuPosition()
                + " finalOutput=" + output
                + " remainingFinalAmount=" + jobView.ae2cr$getRemainingAmount()
                + " remainingOperations=" + remainingOperations
                + " rootDependencyCycles=" + rootDependencyCycles
                + " likelyRootShortages=" + likelyRootShortages
                + " smallestDirectUnlocks=" + smallestUnlocks
                + " dependencyFrontier=" + missingFrontier
                + " cpuInventory=" + ae2cr$describeCounter(inventory.list, false));

        if (topUpOutcome != TopUpOutcome.SUCCESS && topUpOutcome != TopUpOutcome.RECALCULATING) {
            var outputName = output == null
                    ? Component.literal("unknown output")
                    : output.what().getDisplayName().copy().withStyle(ChatFormatting.GOLD);
            ae2cr$alertPlayer(jobView.ae2cr$getPlayerId(), Component.literal("AE2 craft stalled: ")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(outputName)
                    .append(Component.literal(" at CPU " + ae2cr$cpuLabel()).withStyle(ChatFormatting.YELLOW)));
            ae2cr$playAlertSound(jobView.ae2cr$getPlayerId());
        }
    }

    @Unique
    private String ae2cr$cpuPosition() {
        var pos = cluster.getBoundsMin();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Unique
    private String ae2cr$cpuLabel() {
        String name = ae2cr$cpuName();
        return name.isEmpty() ? ae2cr$cpuPosition() : "\"" + name + "\"";
    }

    @Unique
    private String ae2cr$cpuName() {
        var component = cluster.getName();
        return component == null ? "" : component.getString().trim();
    }

    @Unique
    private net.minecraft.server.level.ServerPlayer ae2cr$getConnectedPlayer(Integer playerId) {
        var server = cluster.getLevel().getServer();
        if (server == null || playerId == null) {
            return null;
        }
        return IPlayerRegistry.getConnected(server, playerId);
    }

    @Unique
    private void ae2cr$alertPlayer(Integer playerId, Component message) {
        var player = ae2cr$getConnectedPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
            return;
        }
        var server = cluster.getLevel().getServer();
        if (server != null && playerId != null) {
            var profileId = IPlayerRegistry.getMapping(server).getProfileId(playerId);
            if (profileId != null) {
                PendingPlayerAlerts.get(server).queue(profileId, message);
            }
        }
    }

    @Unique
    private void ae2cr$playAlertSound(Integer playerId) {
        var player = ae2cr$getConnectedPlayer(playerId);
        if (player != null) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.MASTER, 0.9F, 0.65F);
        }
    }

    @Unique
    private PatternBlockage ae2cr$analyzePattern(IPatternDetails details, long count, List<GenericStack> remainingOutputs) {
        var snapshot = ae2cr$copyInventory(inventory);
        var missingInputs = new ArrayList<String>();
        var rootMissingInputs = new ArrayList<String>();
        for (var input : details.getInputs()) {
            long remaining = input.getMultiplier();
            for (var template : CraftingCpuHelper.getValidItemTemplates(snapshot, input, cluster.getLevel())) {
                remaining -= CraftingCpuHelper.extractTemplates(snapshot, template, remaining);
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining > 0) {
                var alternatives = List.of(input.getPossibleInputs()).stream()
                        .map(GenericStack::toString)
                        .collect(Collectors.joining(" OR "));
                var description = remaining + " unit(s) of " + alternatives;
                missingInputs.add(description);

                boolean hasRemainingProducer = remainingOutputs.stream()
                        .anyMatch(output -> input.isValid(output.what(), cluster.getLevel()));
                if (!hasRemainingProducer) {
                    rootMissingInputs.add(description);
                }
            }
        }
        if (missingInputs.isEmpty()) {
            missingInputs.add("unknown input (classification changed while reporting)");
        }
        return new PatternBlockage(count, List.copyOf(missingInputs), List.copyOf(rootMissingInputs),
                ae2cr$describePattern(details));
    }

    @Unique
    private static List<GenericStack> ae2cr$getRemainingOutputs(Map<IPatternDetails, Object> tasks) {
        var outputs = new ArrayList<GenericStack>();
        for (var entry : tasks.entrySet()) {
            if (((TaskProgressAccessor) entry.getValue()).ae2cr$getValue() > 0) {
                outputs.addAll(List.of(entry.getKey().getOutputs()));
            }
        }
        return outputs;
    }

    @Unique
    private static String ae2cr$describePattern(IPatternDetails details) {
        var inputs = List.of(details.getInputs()).stream()
                .flatMap(input -> List.of(input.getPossibleInputs()).stream().limit(1))
                .map(GenericStack::toString)
                .collect(Collectors.joining(" + "));
        var outputs = List.of(details.getOutputs()).stream()
                .map(GenericStack::toString)
                .collect(Collectors.joining(" + "));
        return inputs + " -> " + outputs;
    }

    @Unique
    private static long ae2cr$leadingCount(String value) {
        int separator = value.indexOf('x');
        if (separator <= 0) {
            return 0;
        }
        try {
            return Long.parseLong(value.substring(0, separator));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
