package dev.ferroloom.shimgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Minimal JSON reader for the impl rows (no dependency: the schema is fixed and tiny). */
final class ImplTableJson {
    private ImplTableJson() {}

    static ImplTables load(Path implDir) {
        Map<String, ImplTables.Impl> methods = new HashMap<>();
        Set<String> constructible = new HashSet<>();
        if (!Files.isDirectory(implDir)) return ImplTables.empty();
        try (var files = Files.list(implDir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                read(Files.readString(f), methods, constructible);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return ImplTables.of(methods).withConstructible(constructible);
    }

    private static void read(String json, Map<String, ImplTables.Impl> methods, Set<String> constructible) {
        String constructibleBody = section(json, "constructible");
        if (constructibleBody != null) {
            for (String s : strings(constructibleBody)) constructible.add(s);
        }
        String methodsBody = section(json, "methods");
        if (methodsBody == null) return;
        int i = 0;
        while ((i = methodsBody.indexOf('"', i)) >= 0) {
            int keyEnd = methodsBody.indexOf('"', i + 1);
            if (keyEnd < 0) break;
            String key = unescape(methodsBody.substring(i + 1, keyEnd));
            int colon = methodsBody.indexOf(':', keyEnd);
            int objStart = methodsBody.indexOf("{\"impl\"", colon);
            if (colon < 0 || objStart < 0) break;
            int valStart = methodsBody.indexOf('"', methodsBody.indexOf('"', objStart + 7) + 1);
            int valEnd = methodsBody.indexOf('"', valStart + 1);
            String target = unescape(methodsBody.substring(valStart + 1, valEnd));
            int dot = target.lastIndexOf('.');
            methods.put(key, new ImplTables.Impl(target.substring(0, dot), target.substring(dot + 1)));
            int close = methodsBody.indexOf('}', valEnd);
            i = close < 0 ? valEnd : close;
        }
    }

    private static String section(String json, String name) {
        int key = json.indexOf("\"" + name + "\"");
        if (key < 0) return null;
        int open = json.indexOf('[', key);
        int openObj = json.indexOf('{', key);
        if (open < 0 && openObj < 0) return null;
        boolean array = open >= 0 && (openObj < 0 || open < openObj);
        int start = array ? open : openObj;
        char closeChar = array ? ']' : '}';
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == (array ? '[' : '{')) depth++;
            if (c == closeChar) {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    private static java.util.List<String> strings(String body) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int i = 0;
        while ((i = body.indexOf('"', i)) >= 0) {
            int end = body.indexOf('"', i + 1);
            if (end < 0) break;
            out.add(unescape(body.substring(i + 1, end)));
            i = end + 1;
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\/", "/").replace("\\\"", "\"");
    }
}
