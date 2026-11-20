package dev.ferroloom.loader.remap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Tells whether a jar references Minecraft through intermediary names. */
public final class JarNamespace {
    private static final Pattern INTERMEDIARY =
            Pattern.compile("net/minecraft/(?:[A-Za-z0-9_$]+/)*class_\\d+");

    private JarNamespace() {}

    /**
     * Scans the class files' constant pools as text: descriptors, signatures, annotation values
     * and invokedynamic handles all keep their names verbatim there, so one scan covers every
     * place a reference can hide.
     */
    public static Namespace detect(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                    if (INTERMEDIARY.matcher(text).find()) return Namespace.INTERMEDIARY;
                }
            }
        }
        return Namespace.NAMED;
    }
}
