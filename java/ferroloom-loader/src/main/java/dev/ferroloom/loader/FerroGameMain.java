package dev.ferroloom.loader;

/** Fabric Loader wants an entrypoint class name; FerroLoom drives the game itself. */
public final class FerroGameMain {
    private FerroGameMain() {}

    public static void main(String[] args) {
        throw new UnsupportedOperationException("FerroLoom never launches a game");
    }
}
