# Security

FerroLoom runs arbitrary Java code from the jars you put in `ferroloom-mods/`, with the
permissions of your user. There is no sandbox. Treat a Fabric jar like any other executable.

## Reporting a vulnerability

Do not open a public issue for a vulnerability. Use GitHub's private vulnerability reporting
("Security" tab → "Report a vulnerability") on this repository, or contact the maintainer
directly. Include the FerroLoom commit, the Horizon build, the JDK and a reproduction.

## Scope

- The JNI boundary, handle lifetimes, the read window and the write queue.
- The shim generator: generated bodies must not execute Mojang code.
- The loader: class-loader split (Knot must own only the shim jar) and dependency resolution.
- Secrets: the embedded JVM must not receive account tokens or world data through arguments.

## Supported versions

Only the current `master` receives fixes. There are no backports yet.
