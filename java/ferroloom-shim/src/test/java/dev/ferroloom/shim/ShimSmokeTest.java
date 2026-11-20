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
    void position_reads_come_from_the_pushed_values_inside_a_tick() {
        double[] read = new double[3];
        dev.ferroloom.api.FerroEvents.onClientTick((tick, x, y, z) -> {
            LocalPlayer p = new LocalPlayer();
            read[0] = p.getX();
            read[1] = p.getY();
            read[2] = p.getZ();
        });
        try {
            GameBridge.onTick(1, 1.5, 64.0, -2.5);
        } finally {
            dev.ferroloom.api.FerroEvents.clear();
        }
        assertEquals(1.5, read[0]);
        assertEquals(64.0, read[1]);
        assertEquals(-2.5, read[2]);
    }

    @Test
    void a_read_outside_a_callback_is_refused() {
        ShimRuntime.updatePlayer(1.0, 2.0, 3.0);
        LocalPlayer p = new LocalPlayer();
        assertThrows(dev.ferroloom.runtime.FerroNotInCallbackException.class, p::getX);
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
