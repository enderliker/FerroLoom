package dev.ferroloom.loader;

import dev.ferroloom.api.FerroEvents;
import dev.ferroloom.loader.remap.RemapCache;
import dev.ferroloom.loader.remap.RemapError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.knot.Knot;
import dev.ferroloom.shim.ShimRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ClientModInitializer;

/** Starts Fabric Loader inside the JVM FerroLoom owns. Called once, from the mod's registration. */
public final class FerroBootstrap {
    private static final List<String> FAILED = new ArrayList<>();

    private FerroBootstrap() {}

    /** One client tick: push the position the game thread read, then dispatch to the mods. */
    public static void onTick(long tick, double x, double y, double z) {
        ShimRuntime.updatePlayer(x, y, z);
        for (FerroEvents.TickListener listener : List.copyOf(FerroEvents.TICKS)) {
            try {
                listener.onTick(tick, x, y, z);
            } catch (Throwable failure) {
                failed("tick", failure);
                FerroEvents.TICKS.remove(listener);
            }
        }
    }

    /** Runs the mods' client entrypoints once, on the engine's client_setup. */
    public static void onClientSetup() {
        FabricLoaderImpl.INSTANCE.invokeEntrypoints("client", ClientModInitializer.class,
                ClientModInitializer::onInitializeClient);
    }

    static void failed(String where, Throwable failure) {
        String text = where + ": " + failure;
        FAILED.add(text);
        dev.ferroloom.api.FerroApi.log("listener failed " + text);
    }

    /** Test hook: listener failures seen so far. */
    public static List<String> failedListeners() {
        return FAILED;
    }

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

        new Knot(EnvType.CLIENT).init(new String[] {"--gameDir", gameDir().toString()});
        FabricLoaderImpl.INSTANCE.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
        return "";
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
