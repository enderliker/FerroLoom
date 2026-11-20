package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntryFailureTest {
    private static final String THROWS = """
            package bad;
            public final class Entry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {
                    throw new IllegalStateException("bad main entrypoint");
                }
            }
            """;

    private static final String GOOD = """
            package good;
            public final class Entry implements net.fabricmc.api.ModInitializer {
                @Override public void onInitialize() {
                    dev.ferroloom.api.FerroApi.registerKey("good", "key", "key.keyboard.g");
                }
            }
            """;

    @Test
    void one_entrypoint_that_throws_does_not_discard_the_other_mods_registrations() throws Exception {
        Path tmp = Files.createTempDirectory("fl-entryfail");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.jar(mods, "bad", "bad.Entry", THROWS, java.util.Map.of("main", "bad.Entry"), null);
        TestMods.jar(mods, "good", "good.Entry", GOOD, java.util.Map.of("main", "good.Entry"), null);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir", TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());

        String report = FerroBootstrap.start();

        assertTrue(report.contains("KEY\tgood\tkey\tkey.keyboard.g"), report);
        assertTrue(report.contains("FAILED"), report);
        assertTrue(report.contains("bad"), report);
    }
}
