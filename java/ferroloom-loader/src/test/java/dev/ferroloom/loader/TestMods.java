package dev.ferroloom.loader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Builds throwaway Fabric mod jars for the loader tests. */
public final class TestMods {
    public static final String MAIN_MARKER = """
            package testmod;
            public final class Entry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {
                    System.setProperty("test.testmod.ran", "1");
                }
            }
            """;

    private TestMods() {}

    /** Compiles {@code source} as {@code className} and jars it with {@code fabric.mod.json}. */
    public static Path jar(Path modsDir, String id, String className, String source,
            Map<String, String> entrypoints, String extraMetadata) throws IOException {
        Path work = Files.createTempDirectory("fl-mod-" + id);
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path src = work.resolve("src").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "--release", "25", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("test mod failed to compile: " + className);

        String entryJson = entrypoints.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":[\"" + e.getValue() + "\"]")
                .reduce((a, b) -> a + "," + b).orElse("");
        String metadata = "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0.0\","
                + "\"name\":\"" + id + "\",\"environment\":\"client\",\"entrypoints\":{" + entryJson + "},"
                + "\"depends\":{\"minecraft\":\">=26.3\",\"java\":\">=25\"}"
                + (extraMetadata == null ? "" : "," + extraMetadata) + "}";

        Path jar = Files.createDirectories(modsDir).resolve(id + ".jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zout = new JarOutputStream(out)) {
            zout.putNextEntry(new JarEntry("fabric.mod.json"));
            zout.write(metadata.getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    zout.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    zout.write(Files.readAllBytes(file));
                    zout.closeEntry();
                }
            }
        }
        return jar;
    }

    public static Path jar(Path modsDir, String id, String source) throws IOException {
        return jar(modsDir, id, id + ".Entry", source, Map.of("main", id + ".Entry"), null);
    }
}
