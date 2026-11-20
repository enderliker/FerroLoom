package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BootstrapTest {
    @Test
    void a_fabric_mod_in_the_mods_folder_runs_its_main_entrypoint() throws Exception {
        Path tmp = Files.createTempDirectory("fl-boot");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        Path cache = Files.createDirectories(tmp.resolve("cache"));
        Path shims = Files.createDirectories(tmp.resolve("shims"));
        TestMods.jar(mods, "testmod", TestMods.MAIN_MARKER);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", cache.toString());
        System.setProperty("ferro.shimDir", shims.toString());
        System.setProperty("ferro.gameDir", tmp.toString());

        String report = FerroBootstrap.start();

        assertEquals("", report);
        assertEquals("1", System.getProperty("test.testmod.ran"));
        assertTrue(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("testmod"));
        try (var files = Files.list(cache)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().endsWith("-named.jar")));
        }
    }
}
