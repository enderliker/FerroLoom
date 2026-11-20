package dev.ferroloom.loader;

import static org.junit.jupiter.api.Assertions.*;

import dev.ferroloom.api.FerroEvents;
import dev.ferroloom.shim.GameBridge;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickTest {
    private final List<String> ticks = new ArrayList<>();

    @BeforeEach
    void reset() {
        FerroEvents.clear();
        GameBridge.failedListeners().clear();
        ticks.clear();
    }

    @Test
    void a_tick_reaches_every_listener_with_the_pushed_position() {
        FerroEvents.onClientTick((tick, x, y, z) -> ticks.add(tick + ":" + x + "," + y + "," + z));

        FerroBootstrap.onTick(7, 1.5, 64.0, -2.5);

        assertEquals(List.of("7:1.5,64.0,-2.5"), ticks);
    }

    @Test
    void a_listener_that_throws_is_dropped_and_the_others_run() {
        FerroEvents.onClientTick((tick, x, y, z) -> {
            throw new IllegalStateException("boom");
        });
        FerroEvents.onClientTick((tick, x, y, z) -> ticks.add("second"));

        FerroBootstrap.onTick(1, 0, 0, 0);
        FerroBootstrap.onTick(2, 0, 0, 0);

        assertEquals(List.of("second", "second"), ticks);
        assertEquals(1, FerroEvents.TICKS.size(), "the failing listener is removed");
        assertTrue(GameBridge.failedListeners().get(0).contains("boom"),
                GameBridge.failedListeners().toString());
    }
}
