package dev.ferroloom.api;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Turns a throwable into a message with its whole stack, for the Rust log. */
public final class Errors {
    private Errors() {}

    public static String describe(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
