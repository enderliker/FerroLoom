package dev.ferroloom.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FerroRuntimeTest {
    @Test
    void pingReturnsPong() {
        assertEquals("pong", FerroRuntime.ping());
    }
}