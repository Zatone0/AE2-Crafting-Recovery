# AE2 Crafting Recovery

Experimental crafting recovery and diagnostics for Minecraft 1.20.1, Forge 47.4.20, and Applied Energistics 2 15.4.10.

Large AE2 jobs can plan around deterministic processing-pattern byproducts and later reach a state where every remaining operation depends on material that the same job was expected to produce. This mod distinguishes that zero-progress state from ordinary machine delay, records the blocked dependency graph, and attempts recovery without first dumping the crafting CPU's contents.

## Recovery order

After twenty consecutive dispatch passes with no runnable pattern and no legitimate output in flight, recovery proceeds in this order:

1. **Reserved full replan.** AE2 recalculates the unfinished final request as if it had just been submitted. A snapshot of the old CPU inventory is treated as job-owned stock, and only the remaining deficit is sourced from network storage. The old job is replaced only after the new plan passes preflight.
2. **Transactional top-up.** If a full plan cannot be executed, one completely available input set is moved from ME storage into the existing CPU. Extraction is simulated first and rolled back on a race.
3. **Seed subplan.** If the required input must itself be crafted, AE2 calculates that smaller dependency and merges its patterns into the existing task ledger without cancelling the original job.

Exact returned outputs that were routed into general ME storage are reclaimed through the CPU's normal insertion path. An unchanged expected output is treated as missing only after five minutes, allowing long-running machines to finish normally.

## Diagnostics

Concise recovery messages remain in `latest.log`. Full dependency graphs, CPU inventories, preflight shortages, recovery choices, and rollback evidence are written to:

```text
logs/ae2-crafting-recovery.log
```

The diagnostic file rotates at 8 MiB and retains three older generations. Repeated reports are suppressed until the job state changes.

## Installation

Install the same jar on both the dedicated server and every connecting client. Remove older versions before starting Minecraft.

This project currently targets the exact AE2 execution internals in version 15.4.10. Other AE2 or Minecraft versions are not yet supported. Back up important worlds before testing recovery on large crafts.

## Building

Use JDK 21 and the included Gradle wrapper:

```text
./gradlew clean build --no-configuration-cache
```

The jar is written to `build/libs/`.

## License

MIT
