package dev.ferroloom.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ferroloom.api.FerroRegistrations;
import org.junit.jupiter.api.Test;

class FerroRuntimeTest {
    @Test
    void pingReturnsPong() {
        assertEquals("pong", FerroRuntime.ping());
    }

    @Test
    void a_duplicate_key_registration_keeps_the_first_binding() {
        FerroRegistrations.clear();
        FerroRegistrations.key("m", "k", "key.keyboard.a");
        FerroRegistrations.key("m", "k", "key.keyboard.b");

        assertEquals(1, FerroRegistrations.report().lines().filter(l -> l.startsWith("KEY\t")).count());
        assertEquals("KEY\tm\tk\tkey.keyboard.a", FerroRegistrations.report().trim());
    }
}