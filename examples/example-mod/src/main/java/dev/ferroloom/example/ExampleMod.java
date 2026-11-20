package dev.ferroloom.example;

import dev.ferroloom.api.FerroApi;
import dev.ferroloom.api.FerroEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The Plan 2 example: a real Fabric mod, unmodified by FerroLoom.
 *
 * <p>Registrations that the engine must know at startup (keys, commands) happen in the main
 * entrypoint because FerroLoom reads the registration report right after it; listeners are set up
 * in the client entrypoint, at client_setup. FerroFabric-API (Plan 4) removes this split.
 */
public final class ExampleMod implements ModInitializer, ClientModInitializer {
    @Override
    public void onInitialize() {
        FerroApi.log("example: main entrypoint");
        FerroApi.registerKey("example", "key", "key.keyboard.g");
        FerroApi.registerCommand("example", "ferro-hola");
        // Marks that this mod wants a HUD layer; the line itself is set every tick.
        FerroApi.setHudLine("example", "");
    }

    @Override
    public void onInitializeClient() {
        FerroApi.log("example: client entrypoint");

        FerroEvents.onClientTick((tick, x, y, z) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            String line = "example tick=" + tick + " pos=" + player.getX() + "," + player.getY() + "," + player.getZ();
            FerroApi.setHudLine("example", line);
            if (tick < 3 || tick % 200 == 0) FerroApi.log(line);
        });

        FerroEvents.onKey((modId, keyId, down) -> {
            if (down && keyId.equals("key")) FerroApi.sendChat("hello from FerroLoom");
        });

        FerroEvents.onCommand((modId, name, args) -> {
            if (!name.equals("ferro-hola")) return false;
            FerroApi.sendChat("ferro-hola from the example");
            return true;
        });
    }
}
