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
import dev.ferroloom.api.FerroApi;

/**
 * The named-jar cache. 26.3 is unobfuscated, so a mod jar is already in the runtime namespace;
 * this verifies that and copies it beside the originals, keyed by its sha256.
 */
public final class RemapCache {
    private RemapCache() {}

    /** Cached jars plus the jars that were refused, so one old jar never stops the others. */
    public record CacheResult(List<Path> cached, List<RemapError> refused) {}

    public static CacheResult ensureNamed(Path modsDir, Path cacheDir) throws IOException {
        if (!Files.isDirectory(modsDir)) return new CacheResult(List.of(), List.of());
        Files.createDirectories(cacheDir);
        List<Path> cached = new ArrayList<>();
        List<RemapError> refused = new ArrayList<>();
        try (var files = Files.list(modsDir)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                if (!hasFabricMetadata(jar)) {
                    FerroApi.log("mods: skipping " + jar.getFileName() + ": no fabric.mod.json");
                    continue;
                }
                if (JarNamespace.detect(jar) == Namespace.INTERMEDIARY) {
                    refused.add(new RemapError(jar.getFileName().toString(),
                            "intermediary names (class_*) are not loadable: Horizon 26.3 is unobfuscated,"
                                    + " so this jar was built for another Minecraft version"));
                    continue;
                }
                Path target = cacheDir.resolve(sha256(jar) + "-named.jar");
                if (!Files.exists(target)) Files.copy(jar, target);
                cached.add(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Drop jars cached for mods no longer in the folder: the loader reads the whole cache.
        try (var files = Files.list(cacheDir)) {
            for (Path stale : files.filter(p -> p.getFileName().toString().endsWith("-named.jar"))
                    .filter(p -> !cached.contains(p)).toList()) {
                Files.deleteIfExists(stale);
            }
        }
        return new CacheResult(cached, refused);
    }

    private static boolean hasFabricMetadata(Path jar) {
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            return zip.getEntry("fabric.mod.json") != null;
        } catch (IOException unreadable) {
            // A partial download or a stray non-jar must not stop the other mods.
            FerroApi.log("mods: skipping " + jar.getFileName() + ": " + unreadable);
            return false;
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
