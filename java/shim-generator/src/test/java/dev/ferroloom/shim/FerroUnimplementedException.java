package dev.ferroloom.shim;

/** Test copy of the shim exception (the real one lives in ferroloom-shim). */
public class FerroUnimplementedException extends RuntimeException {
    public FerroUnimplementedException(String message) { super(message); }
}
