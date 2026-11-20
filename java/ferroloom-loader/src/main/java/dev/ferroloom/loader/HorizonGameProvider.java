package dev.ferroloom.loader;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
    private final GameTransformer transformer = new GameTransformer();
    private Arguments arguments = new Arguments();

    @Override public String getGameId() { return "minecraft"; }
    @Override public String getGameName() { return "Horizon"; }
    @Override public String getRawGameVersion() { return "26.3"; }
    @Override public String getNormalizedGameVersion() { return "26.3"; }
    @Override public String getEntrypoint() { return "dev.ferroloom.loader.FerroGameMain"; }
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
        for (Path jar : FerroBootstrap.shimJars()) launcher.addToClassPath(jar);
        return true;
    }

    @Override
    public void initialize(FabricLauncher launcher) {
        transformer.locateEntrypoints(launcher, FerroBootstrap.shimJars());
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        ModMetadata metadata = new BuiltinModMetadata.Builder("minecraft", "26.3")
                .setName("Horizon")
                .build();
        return List.of(new BuiltinMod(FerroBootstrap.shimJars(), metadata));
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
