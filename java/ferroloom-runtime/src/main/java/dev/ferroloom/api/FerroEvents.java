package dev.ferroloom.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The dev-time events a Fabric mod can listen to before FerroFabric-API exists (Plan 4). */
public final class FerroEvents {
    @FunctionalInterface
    public interface TickListener {
        void onTick(long tick, double x, double y, double z);
    }

    @FunctionalInterface
    public interface KeyListener {
        void onKey(String modId, String keyId, boolean down);
    }

    @FunctionalInterface
    public interface CommandListener {
        /** Returns true when this mod ran the command. */
        boolean onCommand(String modId, String name, String[] args);
    }

    public static final List<TickListener> TICKS = new CopyOnWriteArrayList<>();
    public static final List<KeyListener> KEYS = new CopyOnWriteArrayList<>();
    public static final List<CommandListener> COMMANDS = new CopyOnWriteArrayList<>();

    private FerroEvents() {}

    public static void onClientTick(TickListener listener) {
        TICKS.add(listener);
    }

    public static void onKey(KeyListener listener) {
        KEYS.add(listener);
    }

    public static void onCommand(CommandListener listener) {
        COMMANDS.add(listener);
    }

    /** Test and reload hook: stops every listener. */
    public static void clear() {
        TICKS.clear();
        KEYS.clear();
        COMMANDS.clear();
    }
}
