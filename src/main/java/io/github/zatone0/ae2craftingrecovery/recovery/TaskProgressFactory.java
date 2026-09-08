package io.github.zatone0.ae2craftingrecovery.recovery;

import java.lang.reflect.Constructor;

/** Creates AE2's package-private task counter only when a recovered subplan adds a new pattern. */
public final class TaskProgressFactory {
    private static final Constructor<?> CONSTRUCTOR = findConstructor();

    private TaskProgressFactory() {
    }

    public static Object create() {
        try {
            return CONSTRUCTOR.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create AE2 task progress", e);
        }
    }

    private static Constructor<?> findConstructor() {
        try {
            var type = Class.forName("appeng.crafting.execution.ExecutingCraftingJob$TaskProgress");
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
