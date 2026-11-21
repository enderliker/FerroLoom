package dev.ferroloom.shim;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants$Type;
import java.nio.file.Path;
import java.util.jar.JarFile;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
    void identifiers_compare_by_namespace_and_path() {
        Identifier a = Identifier.fromNamespaceAndPath("fabric", "default");
        Identifier b = Identifier.parse("fabric:default");

        assertEquals("fabric", a.getNamespace());
        assertEquals("default", a.getPath());
        assertEquals("fabric:default", a.toString());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("minecraft", Identifier.parse("stone").getNamespace());
    }

    @Test
    void a_key_mapping_registers_its_engine_key_and_reads_the_pushed_state() {
        dev.ferroloom.api.FerroRegistrations.clear();
        KeyMapping zoom = new KeyMapping("key.testmod.zoom",
                InputConstants$Type.KEYBOARD, 25, null);
        KeyMapping other = new KeyMapping("key.other.zoom",
                InputConstants$Type.KEYBOARD, 6, null);
        assertTrue(dev.ferroloom.api.FerroRegistrations.report().isEmpty(),
                "the constructor must not register");

        ShimImpl.ensureRegistered(zoom);
        ShimImpl.ensureRegistered(other);
        String report = dev.ferroloom.api.FerroRegistrations.report();
        assertTrue(report.contains("KEY\ttestmod\tzoom\tkey.keyboard.v"), report);
        assertTrue(report.contains("KEY\tother\tzoom\tkey.keyboard.c"), report);
        assertFalse(zoom.isDown());
        ShimRuntime.setKey("testmod", "zoom", true);
        assertTrue(zoom.isDown());
        assertFalse(other.isDown());
        ShimRuntime.setKey("testmod", "zoom", false);
        assertFalse(zoom.isDown());
    }

    @Test
    void zoomx_surface_answers_without_engine_reads() {
        assertEquals(50.0, Mth.clamp(100.0, 1.0, 50.0));

        Options options = new Options();
        assertEquals(0.5, ((Double) options.sensitivity().get()).doubleValue());
        assertTrue(options.sensitivity() == options.sensitivity());
        options.sensitivity().set(Double.valueOf(0.25));
        assertEquals(0.25, ((Double) options.sensitivity().get()).doubleValue());

        assertNull(new Gui().screen());

        GameBridge.installClientNow();
        assertNotNull(Minecraft.getInstance());
        assertNotNull(Minecraft.getInstance().player);
        assertNotNull(Minecraft.getInstance().options);
        assertNotNull(Minecraft.getInstance().gui);
    }

    @Test
    void tick_fov_goes_through_calculate_fov_so_mixins_see_it() {
        class SpyCamera extends Camera {
            boolean calculated;

            @Override
            public float calculateFov(float fov) {
                calculated = true;
                return fov / 2.0f;
            }
        }
        ShimRuntime.setFovBase(1.0f);
        SpyCamera camera = new SpyCamera();

        ShimImpl.tickFov(camera);

        assertTrue(camera.calculated, "tickFov must call calculateFov");
        assertEquals(0.5f, camera.getFov());
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
