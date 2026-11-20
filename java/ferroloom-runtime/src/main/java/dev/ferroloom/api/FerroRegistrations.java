package dev.ferroloom.api;

import java.util.ArrayList;
import java.util.List;

/** What mods asked to register while their entrypoints ran; Rust reads the report once. */
public final class FerroRegistrations {
    private record Key(String modId, String keyId, String defaultKey) {}
    private record Command(String modId, String name) {}

    private static final List<Key> KEYS = new ArrayList<>();
    private static final List<Command> COMMANDS = new ArrayList<>();
    private static volatile boolean hud;
    private static volatile String hudLine = "";
    private static final List<String> LOGS = new ArrayList<>();

    private FerroRegistrations() {}

    public static synchronized void key(String modId, String keyId, String defaultKey) {
        KEYS.add(new Key(modId, keyId, defaultKey));
    }

    public static synchronized void command(String modId, String name) {
        COMMANDS.add(new Command(modId, name));
    }

    /** A line logged before the loader returned; Rust prints it with the report. */
    public static synchronized void log(String line) {
        if (LOGS.size() < 200) LOGS.add(line.replace('\n', ' ').replace('\r', ' '));
    }

    public static void setHudLine(String line) {
        hud = true;
        hudLine = line == null ? "" : line;
    }

    public static String hudLine() {
        return hudLine;
    }

    public static synchronized String report() {
        StringBuilder out = new StringBuilder();
        for (Key key : KEYS) {
            out.append("KEY\t").append(key.modId()).append('\t').append(key.keyId())
                    .append('\t').append(key.defaultKey()).append('\n');
        }
        for (Command command : COMMANDS) {
            out.append("CMD\t").append(command.modId()).append('\t').append(command.name()).append('\n');
        }
        if (hud) out.append("HUD\n");
        for (String line : LOGS) out.append("LOG\t").append(line).append('\n');
        return out.toString();
    }

    public static synchronized void clear() {
        KEYS.clear();
        COMMANDS.clear();
        LOGS.clear();
        hud = false;
        hudLine = "";
    }
}
