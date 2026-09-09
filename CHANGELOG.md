# Changelog

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
