package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import dev.ferroloom.api.FerroApi;
import dev.ferroloom.api.FerroEvents;
import dev.ferroloom.api.FerroRegistrations;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationsTest {
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void reset() {
        FerroEvents.clear();
        FerroRegistrations.clear();
        keys.clear();
    }

    @Test
    void keys_commands_and_hud_are_reported() {
        FerroApi.registerKey("example", "key", "key.keyboard.g");
        FerroApi.registerCommand("example", "ferro-hola");
        FerroApi.setHudLine("example", "hello");
        FerroApi.log("hello from a mod");

        String report = FerroRegistrations.report();

        assertTrue(report.contains("KEY\texample\tkey\tkey.keyboard.g"), report);
        assertTrue(report.contains("CMD\texample\tferro-hola"), report);
        assertTrue(report.contains("HUD"), report);
        assertTrue(report.contains("LOG\thello from a mod"), report);
    }

    @Test
    void a_registered_command_is_handled_and_an_unknown_one_is_not() {
        FerroApi.registerCommand("example", "ferro-hola");
        String[] owner = new String[1];
        FerroEvents.onCommand((mod, name, args) -> {
            owner[0] = mod;
            return name.equals("ferro-hola");
        });
        assertTrue(FerroBootstrap.onCommand("ferro-hola"));
        assertEquals("example", owner[0]);
        assertFalse(FerroBootstrap.onCommand("otra-cosa"));
    }

    @Test
    void the_key_listener_sees_press_and_release() {
        FerroEvents.onKey((mod, id, down) -> keys.add(mod + ":" + id + "=" + down));
        FerroBootstrap.onKey("example", "key", true);
        FerroBootstrap.onKey("example", "key", false);
        assertEquals(List.of("example:key=true", "example:key=false"), keys);
    }

    @Test
    void the_hud_line_is_the_last_one_set() {
        FerroApi.setHudLine("example", "pos=1");
        assertEquals("pos=1", FerroBootstrap.hudLine());
    }
}
