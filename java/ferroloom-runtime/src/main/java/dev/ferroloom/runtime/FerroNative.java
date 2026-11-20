package dev.ferroloom.runtime;

/** The Rust side of the bridge. The methods are registered by FerroLoom when the JVM starts. */
public final class FerroNative {
    private FerroNative() {}

    public static native long call(int op, long a);

    public static native String callString(int op, String text);

    public static native double[] playerPosition();
}