package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** ZoomX's injection shape: RETURN on Camera.calculateFov(F)F. Its own JVM, so Knot starts once. */
class FovCalculateMixinTest {
    @Test
    void a_mixin_on_camera_calculate_fov_changes_the_modifier_the_engine_sees() throws Exception {
        Path tmp = Files.createTempDirectory("fl-fovcalc");
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        TestMods.fovCalculateMixinJar(mods);
        System.setProperty("ferro.modsDir", mods.toString());
        System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
        System.setProperty("ferro.shimDir",
                TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
        System.setProperty("ferro.gameDir", tmp.toString());

        FerroBootstrap.start();

        assertEquals(1.0f / 3.0f, FerroBootstrap.computeFovModifier(1.0f, 0.0f, false), 0.0001f);
    }
}