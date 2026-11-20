package dev.ferroloom.shimgen;

import java.nio.file.Path;

/** CLI: {@code ShimGeneratorMain <client.jar> <impl dir> <out.jar>}. */
public final class ShimGeneratorMain {
    private ShimGeneratorMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: ShimGeneratorMain <client.jar> <impl dir> <out.jar>");
            System.exit(2);
        }
        ShimGenerator.Summary summary = ShimGenerator.generate(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.printf("shim: %d classes, %d methods, %d skipped%n",
                summary.classes(), summary.methods(), summary.skipped());
    }
}
