package dev.ferroloom.shim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;

/** The state the engine pushes into the shim before each hook call. */
public final class ShimRuntime {
    private static Object client;
    private static volatile Thread callbackThread;
    private static volatile int callbacks;
    private static Camera camera;
    private static double playerX;
    private static double playerY;
    private static double playerZ;
    private static float fovBase = 1.0f;
    private static ClientLevel level;
    private static final Map<String, Boolean> KEYS = new ConcurrentHashMap<>();
    private static final Map<String, String> BOUND_KEYS = new ConcurrentHashMap<>();

    private ShimRuntime() {}

    public static void installClient(Object instance) {
        client = instance;
    }

    public static Object client() {
        return client;
    }

    public static void updatePlayer(double x, double y, double z) {
        playerX = x;
        playerY = y;
        playerZ = z;
        callbackThread = Thread.currentThread();
    }

    public static void enterCallback() {
        callbacks++;
    }

    public static void exitCallback() {
        callbacks--;
    }

    /** Engine reads are only valid inside a callback, on the thread that owns the bridge. */
    public static void requireCallback() {
        if (callbacks <= 0 || Thread.currentThread() != callbackThread) {
            throw new dev.ferroloom.runtime.FerroNotInCallbackException(
                    "FerroLoom: engine read outside a callback");
        }
    }

    public static double playerX() { return playerX; }
    public static double playerY() { return playerY; }
    public static double playerZ() { return playerZ; }

    public static void setFovBase(float fov) {
        fovBase = fov;
    }

    public static float fovBase() {
        return fovBase;
    }

    public static Camera camera() {
        if (camera == null) camera = new Camera();
        return camera;
    }

    public static void installLevel(ClientLevel value) {
        level = value;
    }

    public static void clearLevel() {
        level = null;
    }

    public static ClientLevel level() {
        return level;
    }

    /** Records the engine's current state for the key a mod registered (ids as in the report). */
    public static void setKey(String modId, String keyId, boolean down) {
        KEYS.put(stateId(modId, keyId), down);
    }

    /** The pushed state of a mod's key, looked up by its translation key (`key.zoomx.zoom`). */
    public static boolean keyDown(String translationKey) {
        return Boolean.TRUE.equals(KEYS.get(stateId(keyModId(translationKey), keyId(translationKey))));
    }

    /** The key the player's Controls binding names, as the engine reports it each tick. */
    public static void setBoundKey(String modId, String keyId, String name) {
        BOUND_KEYS.put(stateId(modId, keyId), name);
    }

    /** The pushed binding name, or null when the engine has not reported one. */
    public static String boundKey(String translationKey) {
        return BOUND_KEYS.get(stateId(keyModId(translationKey), keyId(translationKey)));
    }

    /** The id the Registrar and the engine use: `<mod>_<key>`. */
    public static String stateId(String modId, String keyId) {
        return modId + "_" + keyId;
    }

    public static String keyModId(String translationKey) {
        String rest = stripKeyPrefix(translationKey);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    public static String keyId(String translationKey) {
        String rest = stripKeyPrefix(translationKey);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(dot + 1).replace('.', '_');
    }

    /**
     * Horizon's key name for a Minecraft 26.3 key code. 26.3 uses SDL/HID scancodes (`KEY_C` is
     * 6, not 67), so letters start at 4 and digits at 30.
     */
    public static String keyName(int scancode) {
        if (scancode >= 4 && scancode <= 29) {
            return "key.keyboard." + (char) ('a' + scancode - 4);
        }
        if (scancode >= 30 && scancode <= 38) {
            return "key.keyboard." + (char) ('1' + scancode - 30);
        }
        return switch (scancode) {
            case 39 -> "key.keyboard.0";
            case 40 -> "key.keyboard.enter";
            case 41 -> "key.keyboard.escape";
            case 43 -> "key.keyboard.tab";
            case 44 -> "key.keyboard.space";
            case 224 -> "key.keyboard.left.control";
            case 225 -> "key.keyboard.left.shift";
            case 226 -> "key.keyboard.left.alt";
            default -> "key.keyboard.unknown";
        };
    }

    private static String stripKeyPrefix(String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) return "mod";
        return translationKey.startsWith("key.") ? translationKey.substring(4) : translationKey;
    }
}
