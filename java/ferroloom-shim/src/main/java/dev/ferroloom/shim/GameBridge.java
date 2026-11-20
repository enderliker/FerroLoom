package dev.ferroloom.shim;

import dev.ferroloom.api.FerroApi;
import dev.ferroloom.api.FerroEvents;
import dev.ferroloom.api.FerroRegistrations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
        for (FerroEvents.TickListener listener : List.copyOf(FerroEvents.TICKS)) {
            try {
                listener.onTick(tick, x, y, z);
            } catch (Throwable failure) {
                FAILED.add("tick: " + failure);
                FerroApi.log("listener failed: " + failure);
                FerroEvents.TICKS.remove(listener);
            }
        }
    }

    public static float computeFovModifier(float base, float tickDelta, boolean changingFov) {
        installClient();
        ShimRuntime.setFovBase(base);
        Camera camera = ShimRuntime.camera();
        camera.tickFov();
        return camera.getFov();
    }

    public static void onKey(String modId, String keyId, boolean down) {
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
        if (trimmed.isEmpty()) return false;
        int space = trimmed.indexOf(' ');
        String name = space < 0 ? trimmed : trimmed.substring(0, space);
        String[] args = space < 0 ? new String[0] : trimmed.substring(space + 1).trim().split("\\s+");
        for (FerroEvents.CommandListener listener : List.copyOf(FerroEvents.COMMANDS)) {
            try {
                if (listener.onCommand(name, name, args)) return true;
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
        FabricLoaderImpl.INSTANCE.invokeEntrypoints("client", ClientModInitializer.class,
                ClientModInitializer::onInitializeClient);
    }

    public static List<String> failedListeners() {
        return FAILED;
    }

    private static void installClient() {
        if (ShimRuntime.client() != null) return;
        Minecraft minecraft = new Minecraft();
        minecraft.gameRenderer = new GameRenderer();
        minecraft.player = new LocalPlayer();
        ShimRuntime.installClient(minecraft);
    }
}
