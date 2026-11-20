package dev.ferroloom.shimgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Reads the impl rows: {@code {"constructible": [...], "methods": {"key": {"impl": "owner.method"}}}}. */
final class ImplTableJson {
    private ImplTableJson() {}

    static ImplTables load(Path implDir) {
        if (!Files.isDirectory(implDir)) return ImplTables.empty();
        Map<String, ImplTables.Impl> methods = new HashMap<>();
        Set<String> constructible = new HashSet<>();
        try (var files = Files.list(implDir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("constructible")) constructible.add(e.getAsString());
                JsonObject rows = root.getAsJsonObject("methods");
                for (var entry : rows.entrySet()) {
                    String target = entry.getValue().getAsJsonObject().get("impl").getAsString();
                    int dot = target.lastIndexOf('.');
                    methods.put(entry.getKey(), new ImplTables.Impl(target.substring(0, dot), target.substring(dot + 1)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ImplTables.of(methods).withConstructible(constructible);
    }
}
