package dev.ferroloom.shim;

// The generated shim does not carry InnerClasses attributes, so nested game types are named by
// their binary names here.
import com.mojang.blaze3d.platform.InputConstants$Type;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping$Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Hand-written bodies the generated shim classes call; never Mojang code. */
public final class ShimImpl {
    private static OptionInstance sensitivity;

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
        registerKey(self, ShimRuntime.keyName(keyCode));
    }

    public static boolean keyMappingDown(KeyMapping self) {
        return ShimRuntime.keyDown(self.name);
    }

    /** Called by KeyMappingHelper: idempotent, so a mod may construct and then register. */
    public static KeyMapping ensureRegistered(KeyMapping self) {
        registerKey(self, "key.keyboard.unknown");
        return self;
    }

    private static void registerKey(KeyMapping self, String defaultKey) {
        String name = self.name == null ? "key.mod.key" : self.name;
        // FerroRegistrations keeps the first binding, so the constructor's real default wins.
        dev.ferroloom.api.FerroRegistrations.key(
                ShimRuntime.keyModId(name), ShimRuntime.keyId(name), defaultKey);
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