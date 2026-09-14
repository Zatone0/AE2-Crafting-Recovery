package io.github.zatone0.ae2craftingrecovery.notification;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import appeng.api.stacks.AEKey;

/** Groups redundant chat warnings without discarding per-CPU diagnostics. */
public final class DelayedOutputWarningTracker {
    private static final Map<WarningKey, Long> LAST_WARNINGS = new HashMap<>();

    private DelayedOutputWarningTracker() {
    }

    public static synchronized boolean shouldNotify(Integer playerId, AEKey output,
            long gameTime, long groupWindowTicks) {
        var key = new WarningKey(playerId, output);
        Long lastWarning = LAST_WARNINGS.put(key, gameTime);
        if (LAST_WARNINGS.size() > 2048) {
            long oldestUsefulTick = gameTime - groupWindowTicks;
            Iterator<Long> values = LAST_WARNINGS.values().iterator();
            while (values.hasNext()) {
                if (values.next() < oldestUsefulTick) {
                    values.remove();
                }
            }
        }
        return lastWarning == null || gameTime < lastWarning || gameTime - lastWarning >= groupWindowTicks;
    }

    private record WarningKey(Integer playerId, AEKey output) {
    }
}
