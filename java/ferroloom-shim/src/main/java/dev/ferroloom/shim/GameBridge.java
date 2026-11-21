package dev.ferroloom.shim;

import dev.ferroloom.api.FerroApi;
import dev.ferroloom.api.FerroEvents;
import dev.ferroloom.api.FerroRegistrations;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;

/**
 * The half of the bootstrap that lives inside Knot's class loader, so the shim classes it touches
 * are the ones Mixin transforms. FerroBootstrap (the system loader's copy) forwards here.
 */
public final class GameBridge {
    private static final List<String> FAILED = new ArrayList<>();

    private GameBridge() {}

    public static void main(String[] args) {
        throw new UnsupportedOperationException("FerroLoom never launches a game");
    }

    public static void onTick(long tick, double x, double y, double z) {
        ShimRuntime.updatePlayer(x, y, z);
        ShimRuntime.enterCallback();
        try {
            for (FerroEvents.TickListener listener : List.copyOf(FerroEvents.TICKS)) {
                try {
                    listener.onTick(tick, x, y, z);
                } catch (Throwable failure) {
                    FAILED.add("tick: " + failure);
                    FerroApi.log("listener failed: " + failure);
                    FerroEvents.TICKS.remove(listener);
                }
            }
        } finally {
            ShimRuntime.exitCallback();
        }
    }

    public static float computeFovModifier(float base, float tickDelta, boolean changingFov) {
        installClient();
        ShimRuntime.setFovBase(base);
        // The FOV hook is an engine upcall: a mod's calculateFov mixin may read the pushed
        // position there, so it runs inside the same callback window as a tick.
        ShimRuntime.enterCallback();
        try {
            Camera camera = ShimRuntime.camera();
            camera.tickFov();
            return camera.getFov();
        } finally {
            ShimRuntime.exitCallback();
        }
    }

    public static void onKey(String modId, String keyId, boolean down) {
        ShimRuntime.setKey(modId, keyId, down);
        for (FerroEvents.KeyListener listener : List.copyOf(FerroEvents.KEYS)) {
            try {
                listener.onKey(modId, keyId, down);
            } catch (Throwable failure) {
                FAILED.add("key: " + failure);
                FerroApi.log("listener failed: " + failure);
                FerroEvents.KEYS.remove(listener);
            }
        }
    }

    public static boolean onCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isEmpty()) return false;
        int space = trimmed.indexOf(' ');
        String name = space < 0 ? trimmed : trimmed.substring(0, space);
        String[] args = space < 0 ? new String[0] : trimmed.substring(space + 1).trim().split("\\s+");
        String owner = dev.ferroloom.api.FerroRegistrations.commandOwner(name);
        for (FerroEvents.CommandListener listener : List.copyOf(FerroEvents.COMMANDS)) {
            try {
                if (listener.onCommand(owner, name, args)) return true;
            } catch (Throwable failure) {
                FAILED.add("command: " + failure);
                FerroApi.log("listener failed: " + failure);
                FerroEvents.COMMANDS.remove(listener);
            }
        }
        return false;
    }

    public static String hudLine() {
        return FerroRegistrations.hudLine();
    }

    public static void onClientSetup() {
        installClient();
        // The client entrypoints already ran during registration, where their keys could reach
        // the Registrar. This hook only records that the client half is ready.
        FerroApi.log("client setup: entrypoints ran at registration");
    }

    public static List<String> failedListeners() {
        return FAILED;
    }

    /** Installs the synthetic client early, before client entrypoints can ask for it. */
    public static void installClientNow() {
        installClient();
    }

    private static void installClient() {
        if (ShimRuntime.client() != null) return;
        Minecraft minecraft = new Minecraft();
        minecraft.gameRenderer = new GameRenderer();
        minecraft.player = new LocalPlayer();
        minecraft.gui = new Gui();
        minecraft.options = new Options();
        ShimRuntime.installClient(minecraft);
    }
}
