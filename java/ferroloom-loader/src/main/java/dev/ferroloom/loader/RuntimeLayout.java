package dev.ferroloom.loader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Where the development runtime lives: the loader, its libraries, the shim and the game jars. */
public final class RuntimeLayout {
    private RuntimeLayout() {}

    public static Path libDir() {
        String override = System.getProperty("ferro.runtime");
        if (override != null && !override.isBlank()) return Path.of(override);
        String env = System.getenv("FERROLOOM_RUNTIME");
        if (env != null && !env.isBlank()) return Path.of(env);
        // <repo>/java/.runtime when running from the built jar of a development check.
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve(".runtime");
    }

    public static List<Path> libJars() {
        Path dir = libDir();
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException(
                    "FerroLoom: runtime not found at " + dir + "; run tools/fetch-runtime.sh");
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
