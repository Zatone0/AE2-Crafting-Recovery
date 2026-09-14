package io.github.zatone0.ae2craftingrecovery.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RecoveryConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DELAYED_OUTPUT_WARNINGS;
    public static final ForgeConfigSpec.IntValue DELAYED_OUTPUT_WARNING_MINUTES;
    public static final ForgeConfigSpec.BooleanValue GROUP_REPEATED_DELAYED_OUTPUTS;
    public static final ForgeConfigSpec.IntValue REPEATED_OUTPUT_GROUP_MINUTES;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("delayedOutputWarnings");
        DELAYED_OUTPUT_WARNINGS = builder
                .comment("Send warning-only alerts when a craft waits too long for an in-flight machine output.")
                .define("enabled", true);
        DELAYED_OUTPUT_WARNING_MINUTES = builder
                .comment("Minutes an unchanged in-flight output must wait before it is reported.")
                .defineInRange("delayMinutes", 10, 1, 1440);
        GROUP_REPEATED_DELAYED_OUTPUTS = builder
                .comment("Group player and main-log warnings for the same output and craft owner."
                        + " Every affected CPU is still recorded in the dedicated recovery log.")
                .define("groupSameOutput", true);
        REPEATED_OUTPUT_GROUP_MINUTES = builder
                .comment("Minutes to suppress another player warning for the same delayed output and craft owner.")
                .defineInRange("groupWindowMinutes", 10, 1, 1440);
        builder.pop();
        SPEC = builder.build();
    }

    private RecoveryConfig() {
    }

    public static int delayedOutputWarningTicks() {
        return Math.multiplyExact(DELAYED_OUTPUT_WARNING_MINUTES.get(), 60 * 20);
    }

    public static long repeatedOutputGroupTicks() {
        return (long) REPEATED_OUTPUT_GROUP_MINUTES.get() * 60L * 20L;
    }
}
