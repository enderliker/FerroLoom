package dev.ferroloom.loader.remap;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemapTest {
    @TempDir Path tmp;

    @Test
    void a_named_jar_is_cached_by_sha_and_the_original_is_left_alone() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        Path jar = JarFixtures.jar(mods, "a", "net/minecraft/client/Minecraft");

        List<Path> out = RemapCache.ensureNamed(mods, cache).cached();

        assertEquals(1, out.size());
        assertTrue(out.get(0).getFileName().toString().matches("[0-9a-f]{64}-named\\.jar"));
        assertTrue(Files.exists(jar));
    }

    @Test
    void an_intermediary_reference_is_refused_by_name() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        JarFixtures.jar(mods, "old", "net/minecraft/class_310");

        RemapCache.CacheResult result = RemapCache.ensureNamed(mods, cache);
        assertTrue(result.cached().isEmpty());
        assertEquals(1, result.refused().size());
        assertTrue(result.refused().get(0).jar().endsWith("old.jar"));
        assertTrue(result.refused().get(0).reason().contains("intermediary"));
    }

    @Test
    void a_jar_without_fabric_metadata_is_ignored() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        JarFixtures.plainJar(mods, "plain");

        assertTrue(RemapCache.ensureNamed(mods, cache).cached().isEmpty());
    }

    @Test
    void detection_tells_named_from_intermediary() throws Exception {
        assertEquals(Namespace.NAMED, JarNamespace.detect(JarFixtures.jar(tmp, "n", "net/minecraft/client/Minecraft")));
        assertEquals(Namespace.INTERMEDIARY, JarNamespace.detect(JarFixtures.jar(tmp, "i", "net/minecraft/class_310")));
    }

    @Test
    void a_reference_that_only_appears_in_a_descriptor_is_intermediary() throws Exception {
        Path jar = JarFixtures.descriptorJar(tmp, "desc", "net/minecraft/class_310");
        assertEquals(Namespace.INTERMEDIARY, JarNamespace.detect(jar));
    }

    @Test
    void a_reference_that_only_appears_in_an_annotation_is_intermediary() throws Exception {
        Path jar = JarFixtures.annotationJar(tmp, "ann", "net/minecraft/world/entity/class_1297");
        assertEquals(Namespace.INTERMEDIARY, JarNamespace.detect(jar));
    }

    @Test
    void a_zero_byte_jar_is_skipped_and_the_valid_one_is_cached() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        Files.write(mods.resolve("broken.jar"), new byte[0]);
        JarFixtures.jar(mods, "good", "net/minecraft/client/Minecraft");
        dev.ferroloom.api.FerroRegistrations.clear();

        List<Path> out = RemapCache.ensureNamed(mods, cache).cached();

        assertEquals(1, out.size());
        assertTrue(out.get(0).getFileName().toString().endsWith("-named.jar"));
        assertTrue(dev.ferroloom.api.FerroRegistrations.report().contains("skipping broken.jar"),
                dev.ferroloom.api.FerroRegistrations.report());
    }

    @Test
    void the_same_jar_is_cached_once() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        JarFixtures.jar(mods, "a", "net/minecraft/client/Minecraft");
        RemapCache.ensureNamed(mods, cache);
        RemapCache.ensureNamed(mods, cache);
        try (var files = Files.list(cache)) {
            assertEquals(1, files.count());
        }
    }
}
