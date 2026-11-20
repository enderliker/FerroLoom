package dev.ferroloom.shimgen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the impl rows: {@code {"constructible": [...], "methods": {"key": {"impl": "owner.method"}}}}.
 *
 * <p>A tiny reader for this fixed schema, so the generator builds with no registry dependency.
 */
final class ImplTableJson {
    private ImplTableJson() {}

    static ImplTables load(Path implDir) {
        if (!Files.isDirectory(implDir)) return ImplTables.empty();
        Map<String, ImplTables.Impl> methods = new HashMap<>();
        java.util.Set<String> constructible = new java.util.HashSet<>();
        try (var files = Files.list(implDir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                Object root = new Parser(Files.readString(f)).parse();
                if (!(root instanceof Map<?, ?> object)) continue;
                Object list = object.get("constructible");
                if (list instanceof List<?> names) {
                    for (Object name : names) constructible.add(String.valueOf(name));
                }
                Object rows = object.get("methods");
                if (rows instanceof Map<?, ?> table) {
                    for (Map.Entry<?, ?> entry : table.entrySet()) {
                        Object row = entry.getValue();
                        if (!(row instanceof Map<?, ?> fields)) continue;
                        String target = String.valueOf(fields.get("impl"));
                        int dot = target.lastIndexOf('.');
                        methods.put(String.valueOf(entry.getKey()),
                                new ImplTables.Impl(target.substring(0, dot), target.substring(dot + 1)));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ImplTables.of(methods).withConstructible(constructible);
    }

    /** Recursive-descent reader for the JSON subset these tables use. */
    private static final class Parser {
        private final String text;
        private int at;

        Parser(String text) {
            this.text = text;
        }

        Object parse() {
            skipSpace();
            Object value = value();
            skipSpace();
            if (at != text.length()) throw error("trailing content");
            return value;
        }

        private Object value() {
            skipSpace();
            if (at >= text.length()) throw error("unexpected end");
            char c = text.charAt(at);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            expect('{');
            skipSpace();
            if (peek() == '}') {
                at++;
                return out;
            }
            while (true) {
                skipSpace();
                String key = string();
                skipSpace();
                expect(':');
                out.put(key, value());
                skipSpace();
                char c = next();
                if (c == '}') return out;
                if (c != ',') throw error("expected , or }");
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            expect('[');
            skipSpace();
            if (peek() == ']') {
                at++;
                return out;
            }
            while (true) {
                out.add(value());
                skipSpace();
                char c = next();
                if (c == ']') return out;
                if (c != ',') throw error("expected , or ]");
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("short \\u escape");
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
        }

        private Object number() {
            int start = at;
            while (at < text.length() && "+-.eE0123456789".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) throw error("unexpected character");
            return Double.parseDouble(text.substring(start, at));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) throw error("expected " + word);
            at += word.length();
            return value;
        }

        private void expect(char c) {
            if (next() != c) throw error("expected " + c);
        }

        private char peek() {
            return at < text.length() ? text.charAt(at) : '\0';
        }

        private char next() {
            if (at >= text.length()) throw error("unexpected end");
            return text.charAt(at++);
        }

        private void skipSpace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("impl table: " + message + " at " + at);
        }
    }
}
