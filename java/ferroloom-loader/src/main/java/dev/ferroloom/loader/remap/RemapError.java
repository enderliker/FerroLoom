package dev.ferroloom.loader.remap;

/** A mod jar FerroLoom will not load, naming the jar and why. */
public final class RemapError extends Exception {
    private final String jar;
    private final String reason;

    public RemapError(String jar, String reason) {
        super(jar + ": " + reason);
        this.jar = jar;
        this.reason = reason;
    }

    public String jar() { return jar; }
    public String reason() { return reason; }
}
