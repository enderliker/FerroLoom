package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class LifecycleTest {
    @Test
    void client_lifecycle_events_fire_in_order() throws Exception {
        Path apiJar = Path.of(System.getProperty("ferro.testApiJar", ""));
        Assumptions.assumeTrue(Files.isRegularFile(apiJar), "build ../FerroFabric-API first");

        Path tmp = Files.createTempDirectory("fl-lifecycle");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.lifecycleJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir",
                TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        System.setProperty("ferro.apiJar", apiJar.toString());
        System.clearProperty("test.lifecycle");
        try {
            FerroBootstrap.start();
            FerroBootstrap.onClientSetup();
            FerroBootstrap.onClientLevelLoad("minecraft:overworld");
            FerroBootstrap.onTick(1, 0.0, 0.0, 0.0);
            FerroBootstrap.onClientLevelUnload("minecraft:overworld");
            FerroBootstrap.onClientStopping();
        } finally {
            System.clearProperty("ferro.apiJar");
        }

        assertEquals("STARTED;LEVEL;TICK_START;LEVEL_TICK_START;LEVEL_TICK_END;TICK_END;STOPPING;",
                System.getProperty("test.lifecycle"));
    }
}