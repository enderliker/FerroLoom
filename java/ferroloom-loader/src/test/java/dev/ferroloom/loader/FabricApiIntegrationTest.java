package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FabricApiIntegrationTest {
    @Test
    void a_mod_depending_on_fabric_api_loads_registers_its_key_and_ticks() throws Exception {
        Path apiJar = Path.of(System.getProperty("ferro.testApiJar", ""));
        Assumptions.assumeTrue(Files.isRegularFile(apiJar),
                "build ../FerroFabric-API first (build.sh)");

        Path tmp = Files.createTempDirectory("fl-api");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.apiKeyJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir",
                TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        System.setProperty("ferro.apiJar", apiJar.toString());
        System.clearProperty("test.apimod.ticked");

        String report;
        try {
            report = FerroBootstrap.start();
        } finally {
            System.clearProperty("ferro.apiJar");
        }

        assertTrue(report.contains("KEY\tapimod\tzoom\tkey.keyboard.c"), report);
        assertTrue(report.contains("fabric-api: 26.3-ferro.1 initialized"), report);

        FerroBootstrap.onTick(1, 0.0, 0.0, 0.0);
        assertEquals("1", System.getProperty("test.apimod.ticked"));
    }
}