package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FovMixinTest {
    @Test
    void a_mixin_on_camera_get_fov_changes_the_modifier_the_engine_sees() throws Exception {
        Path tmp = Files.createTempDirectory("fl-fovmix");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.fovMixinJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir", dev.ferroloom.loader.TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());
        FerroBootstrap.start();
        assertEquals(2.0f, FerroBootstrap.computeFovModifier(1.0f, 0.0f, false), 0.0001f);
    }
}
