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
    }

    /** Stops dispatching to Java. Safe to call more than once. */
    public static void shutdown() {
        stopped = true;
    }

    static boolean stopped() {
        return stopped;
    }
}