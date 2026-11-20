package dev.ferroloom.loader.remap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The named-jar cache. 26.3 is unobfuscated, so a mod jar is already in the runtime namespace;
 * this verifies that and copies it beside the originals, keyed by its sha256.
 */
public final class RemapCache {
    private RemapCache() {}

    public static List<Path> ensureNamed(Path modsDir, Path cacheDir) throws IOException, RemapError {
        return ensureNamedInternal(modsDir, cacheDir);
    }

    private static List<Path> ensureNamedInternal(Path modsDir, Path cacheDir) throws IOException, RemapError {
        if (!Files.isDirectory(modsDir)) return List.of();
        Files.createDirectories(cacheDir);
        List<Path> cached = new ArrayList<>();
        try (var files = Files.list(modsDir)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                if (!hasFabricMetadata(jar)) continue;
                if (JarNamespace.detect(jar) == Namespace.INTERMEDIARY) {
                    throw new RemapError(jar.getFileName().toString(),
                            "intermediary names (class_*) are not loadable: Horizon 26.3 is unobfuscated,"
                                    + " so this jar was built for another Minecraft version");
                }
                Path target = cacheDir.resolve(sha256(jar) + "-named.jar");
                if (!Files.exists(target)) Files.copy(jar, target);
                cached.add(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cached;
    }

    private static boolean hasFabricMetadata(Path jar) throws IOException {
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            return zip.getEntry("fabric.mod.json") != null;
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
