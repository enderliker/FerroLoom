package dev.ferroloom.shim;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Hand-written bodies the generated shim classes call; never Mojang code. */
public final class ShimImpl {
    private ShimImpl() {}

    public static Minecraft getInstance() {
        return (Minecraft) ShimRuntime.client();
    }

    public static void tickFov(Camera self) {
        self.fov = ShimRuntime.fovBase();
        self.hudFov = ShimRuntime.fovBase();
    }

    public static float calculateFov(Camera self, float fov) {
        return fov;
    }

    public static float fov(Camera self) {
        return self.fov;
    }

    public static double posX(Entity self) { return ShimRuntime.playerX(); }
    public static double posY(Entity self) { return ShimRuntime.playerY(); }
    public static double posZ(Entity self) { return ShimRuntime.playerZ(); }
}
