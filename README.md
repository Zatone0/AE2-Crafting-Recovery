# AE2 Crafting Recovery

Experimental crafting recovery and diagnostics for Minecraft 1.20.1, Forge 47.4.20, and Applied Energistics 2 Supergiant 15.5.3-uelm.

Large AE2 jobs can plan around deterministic processing-pattern byproducts and later reach a state where every remaining operation depends on material that the same job was expected to produce. This mod distinguishes that zero-progress state from ordinary machine delay, records the blocked dependency graph, and attempts recovery without first dumping the crafting CPU's contents.

## Recovery order

After twenty consecutive dispatch passes with no runnable pattern and no legitimate output in flight, recovery proceeds in this order:

1. **Reserved full replan.** AE2 recalculates the unfinished final request as if it had just been submitted. A snapshot of the old CPU inventory is treated as job-owned stock, and only the remaining deficit is sourced from network storage. After preflight, the existing job's internal plan is replaced in place while preserving its crafting link, requester ownership, CPU inventory, CPU assignment, and elapsed time.
2. **Transactional top-up.** If a full plan cannot be executed, one completely available input set is moved from ME storage into the existing CPU. Extraction is simulated first and rolled back on a race.
3. **Seed subplan.** If the required input must itself be crafted, AE2 calculates that smaller dependency and merges its patterns into the existing task ledger without cancelling the original job.

Any expected machine output still in flight is an absolute recovery barrier, regardless of age. After ten minutes the owner receives a warning, but the mod does not replan, top up, reclaim, or replace delayed output work. This prevents slow, fuel-starved, paused, or externally gated machines from causing duplicate production.

Newly submitted jobs preserve their complete original pattern route through world saves and restarts. Recovery calculations use that archive rather than AE2's shrinking remaining-task list. If the craft owner is offline when an alert occurs, the server stores a capped, deduplicated message and delivers it when that player next logs in.

## Diagnostics

Concise recovery messages remain in `latest.log`. Full dependency graphs, CPU inventories, preflight shortages, recovery choices, and rollback evidence are written to:

```text
logs/ae2-crafting-recovery.log
```

The diagnostic file rotates at 8 MiB and retains three older generations. Repeated reports are suppressed until the job state changes.

## Installation

Install the same jar on both the dedicated server and every connecting client. Remove older versions before starting Minecraft.

This release targets the AE2 Supergiant 15.5.3-uelm execution internals. Other AE2 or Minecraft versions are not supported by this build. Back up important worlds before testing recovery on large crafts.

## Building

Use JDK 21 and the included Gradle wrapper:

```text
./gradlew clean build --no-configuration-cache -Pae2_local_jar=/path/to/appliedenergistics2-forge-15.5.3-uelm.jar
```

The AE2 JAR must be the official UELM 15.5.3 release. CI downloads that release from its GitHub repository and verifies its SHA-256 before building. The recovery jar is written to `build/libs/`.

## License

MIT

Project artwork has [separate attribution and terms](artwork/README.md).
