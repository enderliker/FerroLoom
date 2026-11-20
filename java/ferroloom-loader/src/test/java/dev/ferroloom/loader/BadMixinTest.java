package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BadMixinTest {
    @Test
    void a_mixin_that_targets_a_missing_member_fails_loudly() throws Exception {
        Path tmp = Files.createTempDirectory("fl-badmixin-run");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.brokenMixinJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir", TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        FerroBootstrap.start();

        Throwable failure = assertThrows(Throwable.class,
                () -> FerroBootstrap.computeFovModifier(1.0f, 0.0f, false));
        String text = failure.toString() + " " + (failure.getCause() == null ? "" : failure.getCause());
        assertTrue(text.contains("bad.mixins.json") || text.toLowerCase().contains("mixin"), text);
    }
}
