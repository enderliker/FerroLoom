package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeLayoutTest {
    @Test
    void libDirDefaultsUnderTheRuntimePropertyAndListsJarsSorted() throws Exception {
        Path tmp = Files.createTempDirectory("fl-layout");
        Files.writeString(tmp.resolve("b.jar"), "");
        Files.writeString(tmp.resolve("a.jar"), "");
        Files.writeString(tmp.resolve("notes.txt"), "");
        System.setProperty("ferro.runtime", tmp.toString());
        try {
            assertEquals(tmp, RuntimeLayout.libDir());
            assertEquals(List.of(tmp.resolve("a.jar"), tmp.resolve("b.jar")), RuntimeLayout.libJars());
        } finally {
            System.clearProperty("ferro.runtime");
        }
    }

    @Test
    void missingRuntimeIsANamedError() {
        System.setProperty("ferro.runtime", "/nonexistent-ferro-runtime");
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, RuntimeLayout::libJars);
            assertTrue(e.getMessage().contains("/nonexistent-ferro-runtime"), e.getMessage());
        } finally {
            System.clearProperty("ferro.runtime");
        }
    }
}
