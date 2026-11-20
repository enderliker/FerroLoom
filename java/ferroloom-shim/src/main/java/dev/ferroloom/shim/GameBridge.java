package dev.ferroloom.shim;

import dev.ferroloom.api.FerroApi;
import dev.ferroloom.api.FerroEvents;
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
