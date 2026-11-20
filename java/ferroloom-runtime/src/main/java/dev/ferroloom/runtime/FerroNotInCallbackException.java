package dev.ferroloom.runtime;

/** A mod touched the engine outside a FerroLoom callback (a tick, key, command or hook). */
public class FerroNotInCallbackException extends RuntimeException {
    public FerroNotInCallbackException(String message) {
        super(message);
    }
}
