package dev.ferroloom.runtime;

/** The Java side of the FerroLoom bridge. */
public final class FerroRuntime {
    private static volatile boolean stopped;

    private FerroRuntime() {}

    public static String ping() {
        return "pong";
    }

    /** Runs for every client tick the host dispatches. */
    public static void onTick(long tick) {
        if (stopped) {
            return;
        }
        double[] position = FerroNative.playerPosition();
        if (position == null || position.length < 3) {
            return;
        }
        String text =
                "pos=" + position[0] + "," + position[1] + "," + position[2] + " tick=" + tick;
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