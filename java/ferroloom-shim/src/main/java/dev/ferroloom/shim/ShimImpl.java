package dev.ferroloom.shim;

// The generated shim does not carry InnerClasses attributes, so nested game types are named by
// their binary names here.
import com.mojang.blaze3d.platform.InputConstants$Key;
import com.mojang.blaze3d.platform.InputConstants$Type;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping$Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Hand-written bodies the generated shim classes call; never Mojang code. */
public final class ShimImpl {
    private static OptionInstance sensitivity;
    private static final java.util.Map<String, KeyMapping$Category> CATEGORIES = new java.util.HashMap<>();

    private ShimImpl() {}

    public static Minecraft getInstance() {
        return (Minecraft) ShimRuntime.client();
    }

    public static void tickFov(Camera self) {
        // calculateFov is the method zoom mods mix into; the engine's FOV call site must go
        // through it, not assign the fields directly.
        self.fov = self.calculateFov(ShimRuntime.fovBase());
        self.hudFov = self.fov;
    }

    public static float calculateFov(Camera self, float fov) {
        return fov;
    }

    public static float fov(Camera self) {
        return self.fov;
    }

    public static double posX(Entity self) {
        ShimRuntime.requireCallback();
        return ShimRuntime.playerX();
    }

    public static double posY(Entity self) {
        ShimRuntime.requireCallback();
        return ShimRuntime.playerY();
    }

    public static double posZ(Entity self) {
        ShimRuntime.requireCallback();
        return ShimRuntime.playerZ();
    }

    public static void keyMappingInit(KeyMapping self, String name, InputConstants$Type type,
            int keyCode, KeyMapping$Category category) {
        self.name = name;
        self.defaultKey = typeGetOrCreate(type, keyCode);
        self.key = self.defaultKey;
        self.category = category;
    }

    public static boolean keyMappingDown(KeyMapping self) {
        return ShimRuntime.keyDown(self.name);
    }

    /** Called by KeyMappingHelper: registers once, as upstream, and refuses a duplicate. */
    public static KeyMapping ensureRegistered(KeyMapping self) {
        String name = self.name == null ? "key.mod.key" : self.name;
        String modId = ShimRuntime.keyModId(name);
        String keyId = ShimRuntime.keyId(name);
        if (dev.ferroloom.api.FerroRegistrations.hasKey(modId, keyId)) {
            throw new IllegalArgumentException("Key mapping " + name + " is already registered");
        }
        String defaultKey = self.defaultKey == null ? "key.keyboard.unknown" : self.defaultKey.getName();
        dev.ferroloom.api.FerroRegistrations.key(modId, keyId, defaultKey);
        return self;
    }

    /** The key the player's binding names, pushed by the engine, or the declared default. */
    public static InputConstants$Key boundKey(KeyMapping self) {
        String name = ShimRuntime.boundKey(self.name);
        if (name == null) {
            name = self.defaultKey == null ? "key.keyboard.unknown" : self.defaultKey.getName();
        }
        InputConstants$Type type = self.defaultKey == null
                ? InputConstants$Type.KEYBOARD
                : self.defaultKey.getType();
        int value = self.defaultKey == null ? 0 : self.defaultKey.getValue();
        return inputKey(name, type, value);
    }

    public static InputConstants$Key inputKey(String name, InputConstants$Type type, int value) {
        InputConstants$Key key = new InputConstants$Key();
        key.name = name;
        key.type = type;
        key.value = value;
        return key;
    }

    public static String inputKeyName(InputConstants$Key self) {
        return self.name;
    }

    public static InputConstants$Type inputKeyType(InputConstants$Key self) {
        return self.type;
    }

    public static int inputKeyValue(InputConstants$Key self) {
        return self.value;
    }

    public static InputConstants$Key typeGetOrCreate(InputConstants$Type self, int code) {
        String name = self == null || self == InputConstants$Type.KEYBOARD
                ? ShimRuntime.keyName(code)
                : "key.mouse." + code;
        return inputKey(name, self, code);
    }

    public static KeyMapping$Category categoryRegister(Identifier id) {
        String key = id == null ? "minecraft:unknown" : id.toString();
        return CATEGORIES.computeIfAbsent(key, unused -> {
            KeyMapping$Category category = new KeyMapping$Category();
            category.id = id;
            return category;
        });
    }

    public static OptionInstance sensitivity(Options self) {
        if (sensitivity == null) {
            sensitivity = new OptionInstance();
            sensitivity.value = Double.valueOf(0.5);
        }
        return sensitivity;
    }

    public static Object optionGet(OptionInstance self) {
        return self.value;
    }

    public static void optionSet(OptionInstance self, Object value) {
        self.value = value;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Screen screen(Gui self) {
        return null;
    }

    /** A level object for the lifecycle events; the dimension is carried by the event, not read. */
    public static ClientLevel clientLevel(String dimension) {
        return new ClientLevel();
    }

    public static Identifier identifierFrom(String namespace, String path) {
        Identifier identifier = new Identifier();
        identifier.namespace = namespace == null || namespace.isEmpty() ? "minecraft" : namespace;
        identifier.path = path == null ? "" : path;
        return identifier;
    }

    public static Identifier identifierParse(String value) {
        int colon = value.indexOf(':');
        return colon < 0
                ? identifierFrom("minecraft", value)
                : identifierFrom(value.substring(0, colon), value.substring(colon + 1));
    }

    public static String identifierNamespace(Identifier self) {
        return self.namespace;
    }

    public static String identifierPath(Identifier self) {
        return self.path;
    }

    public static String identifierToString(Identifier self) {
        return self.namespace + ":" + self.path;
    }

    public static boolean identifierEquals(Identifier self, Object other) {
        return other instanceof Identifier identifier
                && self.namespace.equals(identifier.namespace)
                && self.path.equals(identifier.path);
    }

    public static int identifierHash(Identifier self) {
        return 31 * self.namespace.hashCode() + self.path.hashCode();
    }
}