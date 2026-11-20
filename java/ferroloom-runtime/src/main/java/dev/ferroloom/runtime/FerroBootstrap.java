package dev.ferroloom.runtime;

/** Starts the Java half of FerroLoom inside the embedded JVM. */
public final class FerroBootstrap {
    private FerroBootstrap() {}

    public static void main(String[] args) {
        System.out.println("FerroLoom runtime ready");
    }
}