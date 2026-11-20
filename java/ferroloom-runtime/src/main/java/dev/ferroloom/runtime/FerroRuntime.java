package dev.ferroloom.runtime;

/** The Java side of the FerroLoom bridge. */
public final class FerroRuntime {
    private FerroRuntime() {}

    public static String ping() {
        return "pong";
    }
}