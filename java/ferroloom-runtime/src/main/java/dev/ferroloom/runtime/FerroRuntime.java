package dev.ferroloom.runtime;

/** The Java side of the FerroLoom bridge. */
public final class FerroRuntime {
    private static volatile boolean stopped;
    private static double[] position;

    private FerroRuntime() {}

    /** The player's position, pushed by the game thread before each tick. */
    public static void setPosition(double x, double y, double z) {
        position = new double[] {x, y, z};
    }

    public static String ping() {
        return "pong";
    }

    /** Runs for every client tick the host dispatches. */
    public static void onTick(long tick) {
        if (stopped) {
            return;
        }
        double[] current = position;
        if (current == null || current.length < 3) {
            FerroNative.callString(3, "no position tick=" + tick);
            return;
        }
        String text =
                "pos=" + current[0] + "," + current[1] + "," + current[2] + " tick=" + tick;
        FerroNative.callString(3, text);
        dispatchToLoader(tick, current[0], current[1], current[2]);
    }

    // The loader module depends on this one, so the dispatch is reflective: a runtime without the
    // loader (Plan 1's smoke) simply has no one to tell.
    private static volatile java.lang.reflect.Method tickDispatch;
    private static volatile boolean dispatchResolved;

    private static void dispatchToLoader(long tick, double x, double y, double z) {
        java.lang.reflect.Method method = tickDispatch;
        if (method == null) {
            if (dispatchResolved) {
                return;
            }
            dispatchResolved = true;
            try {
                method = Class.forName("dev.ferroloom.loader.FerroBootstrap")
                        .getMethod("onTick", long.class, double.class, double.class, double.class);
                tickDispatch = method;
            } catch (ClassNotFoundException absent) {
                return;
            } catch (NoSuchMethodException broken) {
                FerroNative.callString(3, "loader has no onTick: " + broken);
                return;
            }
        }
        try {
            method.invoke(null, tick, x, y, z);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            FerroNative.callString(3, "tick dispatch failed: " + failure.getCause());
        } catch (IllegalAccessException failure) {
            FerroNative.callString(3, "tick dispatch failed: " + failure);
        }
    }

    /** Stops dispatching to Java. Safe to call more than once. */
    public static void shutdown() {
        stopped = true;
    }

    static boolean stopped() {
        return stopped;
    }
}