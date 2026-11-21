package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ModuleIdentityTest {
    private static final String NEEDS_API = """
            package needsapi;
            public final class Entry implements net.fabricmc.api.ClientModInitializer {
                @Override public void onInitializeClient() {
                    System.setProperty("test.needsapi.ran", "1");
                }
            }
            """;

    @Test
    void a_mod_can_depend_on_a_provided_module() throws Exception {
        Path apiJar = Path.of(System.getProperty("ferro.testApiJar", ""));
        Assumptions.assumeTrue(Files.isRegularFile(apiJar), "build ../FerroFabric-API first");

        Path tmp = Files.createTempDirectory("fl-moduleid");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.jar(mods, "needsapi", "needsapi.Entry", NEEDS_API,
                Map.of("client", "needsapi.Entry"), null, "\"fabric-api-base\":\"*\"");
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir",
                TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        System.setProperty("ferro.apiJar", apiJar.toString());
        System.clearProperty("test.needsapi.ran");

        String report;
        try {
            report = FerroBootstrap.start();
        } finally {
            System.clearProperty("ferro.apiJar");
        }

        assertEquals("1", System.getProperty("test.needsapi.ran"), report);
        assertFalse(report.contains("FAILED"), report);
    }
}
