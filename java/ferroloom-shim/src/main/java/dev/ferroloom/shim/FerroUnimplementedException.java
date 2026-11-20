package dev.ferroloom.shim;

/** A shim member the engine does not model yet. Names the class and member the mod needed. */
public class FerroUnimplementedException extends RuntimeException {
    public FerroUnimplementedException(String message) {
        super("FerroLoom: not implemented: " + message);
    }
}
