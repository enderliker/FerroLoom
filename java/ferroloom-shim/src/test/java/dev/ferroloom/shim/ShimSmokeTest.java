package dev.ferroloom.shim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.jar.JarFile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.Test;

class ShimSmokeTest {
    @Test
    void the_shim_has_the_26_3_client_classes() throws Exception {
        try (JarFile jar = new JarFile(Path.of(System.getProperty("ferro.shim.jar")).toFile())) {
            assertNotNull(jar.getEntry("net/minecraft/client/Minecraft.class"));
            assertNotNull(jar.getEntry("net/minecraft/client/renderer/GameRenderer.class"));
            assertNotNull(jar.getEntry("net/minecraft/world/entity/Entity.class"));
        }
    }

    @Test
    void position_reads_come_from_the_pushed_values() {
        ShimRuntime.updatePlayer(1.5, 64.0, -2.5);
        LocalPlayer p = new LocalPlayer();
        assertEquals(1.5, p.getX());
        assertEquals(64.0, p.getY());
        assertEquals(-2.5, p.getZ());
    }

    @Test
    void the_fov_call_passes_through_the_shim() {
        ShimRuntime.setFovBase(1.25f);
        Minecraft mc = new Minecraft();
        mc.gameRenderer = new net.minecraft.client.renderer.GameRenderer();
        ShimRuntime.installClient(mc);
        Camera camera = ShimRuntime.camera();
        camera.tickFov();
        assertEquals(1.25f, camera.getFov());
        assertSame(mc, Minecraft.getInstance());
    }
}
