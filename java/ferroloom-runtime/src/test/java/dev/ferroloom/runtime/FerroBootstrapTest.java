package dev.ferroloom.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FerroBootstrapTest {
    @Test
    void bootstrapReportsReady() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            FerroBootstrap.main(new String[0]);
        } finally {
            System.setOut(previous);
        }
        assertTrue(
                out.toString(StandardCharsets.UTF_8).contains("FerroLoom runtime ready"),
                "bootstrap output was: " + out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shutdownIsIdempotent() {
        FerroRuntime.shutdown();
        FerroRuntime.shutdown();
    }
}