package dev.ferroloom.runtime;

/** The Java side of the FerroLoom bridge. */
public final class FerroRuntime {
    private static volatile boolean stopped;

    private FerroRuntime() {}

    public static String ping() {
        return "pong";
    }

    /** Stops dispatching to Java. Safe to call more than once. */
    public static void shutdown() {
        stopped = true;
    }

    static boolean stopped() {
        return stopped;
    }
}