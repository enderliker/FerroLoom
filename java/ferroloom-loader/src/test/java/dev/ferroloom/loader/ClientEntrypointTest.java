package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientEntrypointTest {
    @Test
    void a_key_registered_in_the_client_entrypoint_reaches_the_report() throws Exception {
        Path tmp = Files.createTempDirectory("fl-clientkey");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.clientKeyJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir",
                TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        System.clearProperty("test.clientkey.runs");

        String report = FerroBootstrap.start();
        assertTrue(report.contains("KEY\tlate\tzoom\tkey.keyboard.v"), report);
        assertEquals("1", System.getProperty("test.clientkey.runs"));

        FerroBootstrap.onClientSetup();
        assertEquals("1", System.getProperty("test.clientkey.runs"), "entrypoints run once");
    }
}