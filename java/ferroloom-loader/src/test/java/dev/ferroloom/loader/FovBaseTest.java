package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FovBaseTest {
    @Test
    void without_mods_the_modifier_is_the_base() throws Exception {
        Path tmp = Files.createTempDirectory("fl-fovbase");
        System.setProperty("ferro.modsDir", Files.createDirectories(tmp.resolve("mods")).toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir", dev.ferroloom.loader.TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        FerroBootstrap.start();

        FerroBootstrap.onClientSetup();
        Class<?> runtime = Class.forName("dev.ferroloom.shim.ShimRuntime", true,
                Thread.currentThread().getContextClassLoader());
        assertNotNull(runtime.getMethod("client").invoke(null));
        assertEquals(1.25f, FerroBootstrap.computeFovModifier(1.25f, 0.0f, false), 0.0001f);
    }
}
