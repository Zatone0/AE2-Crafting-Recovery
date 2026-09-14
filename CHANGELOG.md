# Changelog

## 0.5.2 Beta - 2026-09-14

* Give recovery calculations the crafting CPU's grid node so AE2 can enumerate and build the archived pattern route instead of returning a zero-pattern simulation.
* Record whether the recovery requester received a grid node in the dedicated diagnostic log.

## 0.5.1 Beta - 2026-09-13

* Move the crafting-job archive interface outside the reserved Mixin package, fixing the dedicated-server `IllegalClassLoadError` when AE2 CPUs load saved jobs.

## 0.5.0 Beta - 2026-09-13

* Archive every pattern definition from the original submitted plan inside the crafting job and preserve it through world saves and restarts.
* Use that complete archive for route-preserving replans instead of the shrinking unfinished-task ledger.
* Persist up to 32 deduplicated alerts per craft owner while they are offline and deliver them once at their next login.
* Keep legacy jobs compatible by initializing their archive from the patterns that still remain.

## 0.4.9 Beta - 2026-09-13

* Match route-preserving recovery patterns by their stable encoded pattern definition instead of transient object identity.
* Record the number of allowed pattern definitions supplied to each recovery calculation.
* Stop retrying failed seed calculations until the job's task or retained-inventory state actually changes.

## 0.4.8 Beta - 2026-09-11

* Treat every outstanding machine output as in flight regardless of age; delayed outputs can no longer trigger replanning, replacement subplans, or top-ups.
* Replace the five-minute missing-output recovery with a warning-only alert after ten minutes.
* Emit that delay warning only once per waiting output key for the life of each craft.
* Stop reclaiming presumed returned outputs from general ME storage while a machine operation remains in flight.

## 0.4.7 Beta - 2026-09-09

* Resolve a rejected pattern push to its ExpandedAE pattern provider when that information is available.
* Include the provider dimension and coordinates in diagnostics and the player's stall alert.
* Add a player-scoped clickable chat action that uses ExpandedAE's existing 15-second block highlight.
* Keep ExpandedAE integration optional so the recovery mod remains usable in packs without it.

## 0.4.6 Beta - 2026-09-09

* Distinguish shared-provider contention from a jammed target by observing AE2's actual provider push attempts.
* Alert only after an idle provider rejects the same ready pattern for five minutes; busy providers do not advance the stall timer.
* Record the rejected pattern and provider implementation in the dedicated recovery log without injecting more inputs.

## 0.4.5 Beta - 2026-09-08

* Recalculate stalled jobs using only the pattern route selected by the original job before falling back to top-ups.
* Batch direct top-ups for as many as 64 remaining operations when CPU and network stock permit, reducing repeated one-item recovery cycles.
* Record whether a failed preflight used route-preserving recovery and how many operations each top-up unlocked.

## 0.4.4 Beta - 2026-09-07

* Detect provable AE2 crafting deadlocks without treating ordinary in-flight machine work as stalled.
* Recalculate the complete unfinished request first, using CPU-held contents as job-owned planning stock.
* Preserve CPU contents until the replacement plan passes preflight and is successfully submitted.
* Fall back to transactional network top-ups and missing-input subplan merges when a complete replan is unavailable.
* Reconcile exact outputs that returned to general ME storage instead of their crafting CPU.
* Detect unchanged missing returned outputs and calculate replacement work after a five-minute timeout.
* Rotate direct recovery candidates so one cheap recycling branch cannot permanently starve the rest of a large job.
* Rate-limit repeated reports for an unchanged stalled state.
* Write detailed evidence to a dedicated rotating diagnostic log.
* Ignore stale keyless crafting-status rows during rapid recovery transitions to prevent client rendering crashes.
