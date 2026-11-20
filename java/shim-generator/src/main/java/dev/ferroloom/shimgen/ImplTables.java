package dev.ferroloom.shimgen;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rows that say how a shim member behaves: its own implementation, or nothing (throws). */
public final class ImplTables {
    public record Impl(String owner, String method) {}

    private final Map<String, Impl> methods;
    private final Set<String> constructible;

    private ImplTables(Map<String, Impl> methods, Set<String> constructible) {
        this.methods = methods;
        this.constructible = constructible;
    }

    public static ImplTables empty() {
        return new ImplTables(Map.of(), Set.of());
    }

    public static ImplTables of(Map<String, Impl> methods) {
        return new ImplTables(Map.copyOf(methods), Set.of());
    }

    public static ImplTables constructible(String... classes) {
        return new ImplTables(Map.of(), Set.of(classes));
    }

    public ImplTables withConstructible(Set<String> classes) {
        Set<String> merged = new HashSet<>(constructible);
        merged.addAll(classes);
        return new ImplTables(methods, Collections.unmodifiableSet(merged));
    }

    public Impl get(String key) {
        return methods.get(key);
    }

    public boolean isConstructible(String internalName) {
        return constructible.contains(internalName);
    }

    public Map<String, Impl> methods() {
        return methods;
    }

    public static ImplTables load(Path implDir) {
        return ImplTableJson.load(implDir);
    }
}
