package dev.ferroloom.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.Arguments;

/**
 * Tells Fabric Loader it runs inside Horizon: there is no Java game to launch, only the shim the
 * Java side of FerroLoom provides.
 */
public final class HorizonGameProvider implements GameProvider {
    /** The `fabric-api` version FerroFabric-API reports, so a mod's `depends` resolves. */
    public static final String FABRIC_API_VERSION = "26.3-ferro.1";

    private final GameTransformer transformer = new GameTransformer();
    private Arguments arguments = new Arguments();

    @Override public String getGameId() { return "minecraft"; }
    @Override public String getGameName() { return "Horizon"; }
    @Override public String getRawGameVersion() { return "26.3"; }
    @Override public String getNormalizedGameVersion() { return "26.3"; }
    @Override public String getEntrypoint() { return "dev.ferroloom.shim.GameBridge"; }
    @Override public Path getLaunchDirectory() { return FerroBootstrap.gameDir(); }
    @Override public boolean requiresUrlClassLoader() { return false; }
    @Override public boolean isEnabled() { return true; }
    @Override public GameTransformer getEntrypointTransformer() { return transformer; }
    @Override public void unlockClassPath(FabricLauncher launcher) {}

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        return Collections.emptySet();
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.arguments = new Arguments();
        this.arguments.parse(args);
        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        List<Path> shims = FerroBootstrap.shimJars();
        Path api = apiJar();
        // Loader, libraries and FerroLoom's own jars stay with the parent loader; the shim (game
        // code) and FerroFabric-API are defined by Knot, because mods link against both.
        launcher.setValidParentClassPath(launcher.getClassPath().stream()
                .filter(path -> !containsGameClasses(path))
                .filter(path -> api == null || !samePath(path, api))
                .toList());
        for (Path jar : shims) launcher.addToClassPath(jar);
        if (api != null) launcher.addToClassPath(api);
        transformer.locateEntrypoints(launcher, shims);
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        List<BuiltinMod> mods = new ArrayList<>();
        ModMetadata metadata = new BuiltinModMetadata.Builder("minecraft", "26.3")
                .setName("Horizon")
                .build();
        mods.add(new BuiltinMod(FerroBootstrap.shimJars(), metadata));

        Path api = apiJar();
        if (api != null) {
            // FerroFabric-API's own code, under Fabric API's published package names: this is what
            // satisfies `depends: fabric-api` (design §6.8, §D5).
            ModMetadata apiMetadata = new BuiltinModMetadata.Builder("fabric-api", FABRIC_API_VERSION)
                    .setName("FerroFabric API")
                    .setEnvironment(ModEnvironment.CLIENT)
                    .build();
            mods.add(new BuiltinMod(List.of(api), apiMetadata));
        }
        return mods;
    }

    /** The staged FerroFabric-API jar, when the host found one (`-Dferro.apiJar`). */
    static Path apiJar() {
        String value = System.getProperty("ferro.apiJar");
        if (value == null || value.isBlank()) return null;
        Path path = Path.of(value);
        return Files.isRegularFile(path) ? path : null;
    }

    private static boolean samePath(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    /** True for a jar or directory holding the shim's net.minecraft classes. */
    private static boolean containsGameClasses(Path path) {
        String entry = "net/minecraft/client/Minecraft.class";
        if (java.nio.file.Files.isDirectory(path)) return java.nio.file.Files.exists(path.resolve(entry));
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) {
            return zip.getEntry(entry) != null;
        } catch (java.io.IOException notAJar) {
            return false;
        }
    }

    @Override
    public void launch(ClassLoader loader) {
        throw new UnsupportedOperationException("FerroLoom never launches a game");
    }

    @Override
    public Arguments getArguments() { return arguments; }

    @Override
    public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }

    @Override
    public String getRuntimeNamespace(String defaultNs) { return "named"; }

    @Override
    public String getDefaultModDistributionNamespace(String defaultNs) { return "named"; }

    @Override
    public boolean canOpenErrorGui() { return false; }
}
