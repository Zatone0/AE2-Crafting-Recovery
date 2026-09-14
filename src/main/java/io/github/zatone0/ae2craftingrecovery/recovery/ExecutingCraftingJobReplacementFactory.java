package io.github.zatone0.ae2craftingrecovery.recovery;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;

/** Builds an AE2 job around an existing link without cancelling requester ownership. */
public final class ExecutingCraftingJobReplacementFactory {
    private static final Class<?> LISTENER_TYPE = findListenerType();
    private static final Constructor<?> CONSTRUCTOR = findConstructor();
    private static final Method POST_CHANGE = findPostChange();

    private ExecutingCraftingJobReplacementFactory() {
    }

    public static ExecutingCraftingJob create(ICraftingPlan plan, CraftingCpuLogic logic,
            CraftingLink link, Integer playerId) {
        try {
            Object listener = Proxy.newProxyInstance(
                    LISTENER_TYPE.getClassLoader(),
                    new Class<?>[] { LISTENER_TYPE },
                    (proxy, method, args) -> {
                        if (method.getName().equals("onCraftingDifference")) {
                            POST_CHANGE.invoke(logic, (AEKey) args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) {
                            return "AE2CraftingRecoveryDifferenceListener";
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        throw new UnsupportedOperationException(method.toString());
                    });
            return (ExecutingCraftingJob) CONSTRUCTOR.newInstance(plan, listener, link, playerId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create ownership-preserving AE2 recovery job", e);
        }
    }

    private static Class<?> findListenerType() {
        try {
            return Class.forName("appeng.crafting.execution.ExecutingCraftingJob$CraftingDifferenceListener");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Constructor<?> findConstructor() {
        try {
            var constructor = ExecutingCraftingJob.class.getDeclaredConstructor(
                    ICraftingPlan.class, LISTENER_TYPE, CraftingLink.class, Integer.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method findPostChange() {
        try {
            var method = CraftingCpuLogic.class.getDeclaredMethod("postChange", AEKey.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
