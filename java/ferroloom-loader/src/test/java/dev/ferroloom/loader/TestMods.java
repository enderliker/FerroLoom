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

    /** Puts the built shim jar where the provider adds it to Knot's class path. */
    public static Path stageShim(Path dir) throws IOException {
        String jar = System.getProperty("ferro.shim.jar");
        if (jar == null) throw new IllegalStateException("ferro.shim.jar is not set");
        Files.copy(Path.of(jar), dir.resolve("ferro-shim.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return dir;
    }

    /** Compiles {@code source} as {@code className} and jars it with {@code fabric.mod.json}. */
    public static Path jar(Path modsDir, String id, String className, String source,
            Map<String, String> entrypoints, String extraMetadata) throws IOException {
        return jar(modsDir, id, className, source, entrypoints, extraMetadata, null);
    }

    public static Path jar(Path modsDir, String id, String className, String source,
            Map<String, String> entrypoints, String extraMetadata, String extraDepends)
            throws IOException {
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
                + "\"depends\":{\"minecraft\":\">=26.3\",\"java\":\">=25\""
                + (extraDepends == null ? "" : "," + extraDepends) + "}"
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

    /** A mod whose only content is a Mixin that doubles {@code Camera.getFov}. */
    public static Path fovMixinJar(Path modsDir) throws IOException {
        String source = """
                package testfov.mixin;
                @org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Camera.class)
                public class CameraMixin {
                    @org.spongepowered.asm.mixin.injection.Inject(
                            method = "getFov",
                            at = @org.spongepowered.asm.mixin.injection.At("RETURN"),
                            cancellable = true)
                    private void doubleFov(
                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
                        cir.setReturnValue(cir.getReturnValue() * 2.0f);
                    }
                }
                """;
        Path work = Files.createTempDirectory("fl-fov");
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path src = work.resolve("src/testfov/mixin/CameraMixin.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("fov mixin failed to compile");

        Path jar = Files.createDirectories(modsDir).resolve("fovmod.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zout = new JarOutputStream(out)) {
            zout.putNextEntry(new JarEntry("fabric.mod.json"));
            zout.write(("{\"schemaVersion\":1,\"id\":\"fovmod\",\"version\":\"1.0.0\","
                    + "\"environment\":\"client\",\"mixins\":[\"fov.mixins.json\"],"
                    + "\"depends\":{\"minecraft\":\">=26.3\"}}").getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
            zout.putNextEntry(new JarEntry("fov.mixins.json"));
            zout.write(("{\"required\":true,\"package\":\"testfov.mixin\",\"client\":[\"CameraMixin\"],"
                    + "\"compatibilityLevel\":\"JAVA_21\",\"injectors\":{\"defaultRequire\":1}}").getBytes(StandardCharsets.UTF_8));
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

    /** A mod whose Mixin divides {@code Camera.calculateFov}'s return value, like a zoom mod. */
    public static Path fovCalculateMixinJar(Path modsDir) throws IOException {
        String source = """
                package testfovcalc.mixin;
                @org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Camera.class)
                public class CameraMixin {
                    @org.spongepowered.asm.mixin.injection.Inject(
                            method = "calculateFov(F)F",
                            at = @org.spongepowered.asm.mixin.injection.At("RETURN"),
                            cancellable = true)
                    private void divideFov(
                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
                        cir.setReturnValue(cir.getReturnValueF() / 3.0f);
                    }
                }
                """;
        Path work = Files.createTempDirectory("fl-fovcalc");
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path src = work.resolve("src/testfovcalc/mixin/CameraMixin.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("calculateFov mixin failed to compile");

        Path jar = Files.createDirectories(modsDir).resolve("fovcalcmod.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zout = new JarOutputStream(out)) {
            zout.putNextEntry(new JarEntry("fabric.mod.json"));
            zout.write(("{\"schemaVersion\":1,\"id\":\"fovcalcmod\",\"version\":\"1.0.0\","
                    + "\"environment\":\"client\",\"mixins\":[\"fovcalc.mixins.json\"],"
                    + "\"depends\":{\"minecraft\":\">=26.3\"}}").getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
            zout.putNextEntry(new JarEntry("fovcalc.mixins.json"));
            zout.write(("{\"required\":true,\"package\":\"testfovcalc.mixin\",\"client\":[\"CameraMixin\"],"
                    + "\"compatibilityLevel\":\"JAVA_21\",\"injectors\":{\"defaultRequire\":1}}")
                    .getBytes(StandardCharsets.UTF_8));
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

    /** A client-only mod whose mixin injects into a method that does not exist. */
    public static Path brokenMixinJar(Path modsDir) throws IOException {
        String source = """
                package badmixin;
                @org.spongepowered.asm.mixin.Mixin(net.minecraft.client.Camera.class)
                public class BadMixin {
                    @org.spongepowered.asm.mixin.injection.Inject(
                            method = "noSuchCameraMethod",
                            at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
                    private void missing(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
                    }
                }
                """;
        Path work = Files.createTempDirectory("fl-badmixin");
        Path classes = Files.createDirectories(work.resolve("classes"));
        Path src = work.resolve("src/badmixin/BadMixin.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("bad mixin failed to compile");

        Path jar = Files.createDirectories(modsDir).resolve("badmixin.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream zout = new JarOutputStream(out)) {
            zout.putNextEntry(new JarEntry("fabric.mod.json"));
            zout.write(("{\"schemaVersion\":1,\"id\":\"badmixin\",\"version\":\"1.0.0\","
                    + "\"environment\":\"client\",\"mixins\":[\"bad.mixins.json\"],"
                    + "\"depends\":{\"minecraft\":\">=26.3\"}}").getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
            zout.putNextEntry(new JarEntry("bad.mixins.json"));
            zout.write(("{\"required\":true,\"package\":\"badmixin\",\"client\":[\"BadMixin\"],"
                    + "\"compatibilityLevel\":\"JAVA_21\",\"injectors\":{\"defaultRequire\":1}}")
                    .getBytes(StandardCharsets.UTF_8));
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

    /** A client-only mod whose key exists only in its client entrypoint. */
    public static Path clientKeyJar(Path modsDir) throws IOException {
        String source = """
                package clientkey;
                public final class Entry implements net.fabricmc.api.ClientModInitializer {
                    @Override public void onInitializeClient() {
                        int runs = Integer.parseInt(System.getProperty("test.clientkey.runs", "0")) + 1;
                        System.setProperty("test.clientkey.runs", Integer.toString(runs));
                        dev.ferroloom.api.FerroApi.registerKey("late", "zoom", "key.keyboard.v");
                    }
                }
                """;
        return jar(modsDir, "clientkey", "clientkey.Entry", source,
                Map.of("client", "clientkey.Entry"), null);
    }

    /** A client mod that depends on `fabric-api` and uses KeyMappingHelper and ClientTickEvents. */
    public static Path apiKeyJar(Path modsDir) throws IOException {
        String source = """
                package apimod;
                public final class Entry implements net.fabricmc.api.ClientModInitializer {
                    @Override public void onInitializeClient() {
                        net.minecraft.client.KeyMapping zoom = new net.minecraft.client.KeyMapping(
                                "key.apimod.zoom",
                                com.mojang.blaze3d.platform.InputConstants$Type.KEYBOARD, 6, null);
                        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
                                .registerKeyMapping(zoom);
                        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                                .END_CLIENT_TICK.register(
                                        client -> System.setProperty("test.apimod.ticked", "1"));
                    }
                }
                """;
        return jar(modsDir, "apimod", "apimod.Entry", source,
                Map.of("client", "apimod.Entry"), null, "\"fabric-api\":\"*\"");
    }

    public static Path jar(Path modsDir, String id, String source) throws IOException {
        return jar(modsDir, id, id + ".Entry", source, Map.of("main", id + ".Entry"), null);
    }
}
