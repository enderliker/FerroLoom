package dev.ferroloom.api;

import dev.ferroloom.runtime.FerroNative;

/** What a mod talks to FerroLoom with in Plan 2: log, chat, keys, commands and the HUD line. */
public final class FerroApi {
    private static final int OP_LOG = 3;
    private static final int OP_CHAT = 4;

    private FerroApi() {}

    public static void log(String message) {
        try {
            FerroNative.callString(OP_LOG, message);
        } catch (Throwable unavailable) {
            System.err.println("[ferroloom] " + message);
        }
    }

    public static void registerKey(String modId, String keyId, String defaultKey) {
        FerroRegistrations.key(modId, keyId, defaultKey);
    }

    public static void registerCommand(String modId, String name) {
        FerroRegistrations.command(modId, name);
    }

    public static void setHudLine(String modId, String line) {
        FerroRegistrations.setHudLine(line);
    }

    public static void sendChat(String message) {
        try {
            FerroNative.callString(OP_CHAT, message);
        } catch (Throwable unavailable) {
            System.err.println("[ferroloom] chat unavailable: " + message);
        }
    }
}
