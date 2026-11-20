package dev.ferroloom.loader;

import dev.ferroloom.loader.remap.RemapCache;
import dev.ferroloom.loader.remap.RemapError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.knot.Knot;

/**
 * Starts Fabric Loader inside the JVM FerroLoom owns, then forwards the engine's calls to
 * {@link dev.ferroloom.shim.GameBridge}, which Knot's class loader must own so Mixin can see the shim classes.
 */
public final class FerroBootstrap {
    private static volatile ClassLoader gameLoader;
    private static volatile Method tick;
    private static volatile Method fov;
    private static volatile Method setup;

    private FerroBootstrap() {}

    public static Path gameDir() {
        return Path.of(property("ferro.gameDir", "."));
    }

    public static Path modsDir() {
        return Path.of(property("ferro.modsDir", gameDir().resolve("mods").toString()));
    }

    public static Path cacheDir() {
        return Path.of(property("ferro.cacheDir", gameDir().resolve("ferroloom-cache").toString()));
    }

    public static List<Path> shimJars() {
        Path shims = Path.of(property("ferro.shimDir", gameDir().toString()));
        if (!Files.isDirectory(shims)) return List.of();
        try (var stream = Files.list(shims)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String start() {
        setDefault("fabric.side", "client");
        setDefault("fabric.skipMcProvider", "true");
        setDefault("fabric.gameVersion", "26.3");
        setDefault("fabric.runtimeMappingNamespace", "named");
        setDefault("fabric.gameMappingNamespace", "named");
        setDefault("fabric.defaultModDistributionNamespace", "named");
        setDefault("fabric.noGui", "true");

        List<Path> shims = shimJars();
        if (!shims.isEmpty()) System.setProperty("fabric.gameJarPath.client", shims.get(0).toString());

        try {
            RemapCache.ensureNamed(modsDir(), cacheDir());
        } catch (RemapError e) {
            throw new IllegalStateException("FerroLoom: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.setProperty("fabric.modsFolder", cacheDir().toString());

        ClassLoader loader = new Knot(EnvType.CLIENT)
                .init(new String[] {"--gameDir", gameDir().toString()});
        gameLoader = loader;
        FabricLoaderImpl.INSTANCE.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
        return dev.ferroloom.api.FerroRegistrations.report();
    }

    public static void onTick(long tickNumber, double x, double y, double z) {
        invoke(gameMethod("onTick", long.class, double.class, double.class, double.class),
                tickNumber, x, y, z);
    }

    public static float computeFovModifier(float base, float tickDelta, boolean changingFov) {
        Object value = invoke(gameMethod("computeFovModifier", float.class, float.class, boolean.class),
                base, tickDelta, changingFov);
        return value instanceof Float f ? f : base;
    }

    public static void onKey(String modId, String keyId, boolean down) {
        invoke(gameMethod("onKey", String.class, String.class, boolean.class), modId, keyId, down);
    }

    public static boolean onCommand(String command) {
        Object handled = invoke(gameMethod("onCommand", String.class), command);
        return handled instanceof Boolean value && value;
    }

    public static String hudLine() {
        Object line = invoke(gameMethod("hudLine"), new Object[0]);
        return line instanceof String text ? text : "";
    }

    public static void onClientSetup() {
        invoke(gameMethod("onClientSetup"), new Object[0]);
    }

    private static Method gameMethod(String name, Class<?>... parameters) {
        try {
            return Class.forName("dev.ferroloom.shim.GameBridge", true, gameLoader())
                    .getMethod(name, parameters);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FerroLoom: GameBridge#" + name + " is missing", e);
        }
    }

    private static ClassLoader gameLoader() {
        ClassLoader loader = gameLoader;
        return loader != null ? loader : FerroBootstrap.class.getClassLoader();
    }

    private static Object invoke(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
