package dev.ferroloom.shim;

import net.minecraft.client.Camera;

/** The state the engine pushes into the shim before each hook call. */
public final class ShimRuntime {
    private static Object client;
    private static Camera camera;
    private static double playerX;
    private static double playerY;
    private static double playerZ;
    private static float fovBase;

    private ShimRuntime() {}

    public static void installClient(Object instance) {
        client = instance;
    }

    public static Object client() {
        return client;
    }

    public static void updatePlayer(double x, double y, double z) {
        playerX = x;
        playerY = y;
        playerZ = z;
    }

    public static double playerX() { return playerX; }
    public static double playerY() { return playerY; }
    public static double playerZ() { return playerZ; }

    public static void setFovBase(float fov) {
        fovBase = fov;
    }

    public static float fovBase() {
        return fovBase;
    }

    public static Camera camera() {
        if (camera == null) camera = new Camera();
        return camera;
    }
}
