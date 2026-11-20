# Contributing to FerroLoom

Thanks for helping. FerroLoom is a JVM host for Horizon that runs Fabric mods as Java shims
over JNI. It is MIT-licensed (see [LICENSE](LICENSE)): use it, modify it, fork it and contribute.

## Before you start

- Open an issue (or a draft pull request) before large changes, so we agree on the approach.
- Contributions must not contain Mojang code or assets. The shim only reads the official 26.3
  jar's class names and descriptors at build time; generated bodies either throw or call
  FerroLoom's own code.
- Do not run `horizon-launcher native-lock --update-lock`. The package's `Cargo.lock` must keep
  the game engine's graph.
- All JNI runs on the thread that created the JVM. Do not attach the game's threads.
- Comments and documentation are written in English.

## Development setup

Prerequisites: the Horizon launcher (it provides the native-mod SDK and the pinned toolchain),
a JDK 21+, and network access for the first run.

```sh
cargo horizon-mod check          # resolves the pinned SDK and checks the package
cargo test                       # Rust unit tests
./tools/fetch-runtime.sh         # Fabric Loader, Mixin, ASM and the 26.3 client jar
(cd java && ./gradlew test)      # runtime, loader, shim and generator tests
```

Running the mod in the game is described in [docs/smoke.md](../docs/smoke.md) (in the parent
folder of this checkout) and in the README.

## How to contribute

1. Fork the repository and create a topic branch.
2. Add the change with a test, following the rules below.
3. Open a pull request against `master` and paste the test output.

Every commit must be signed off with the Developer Certificate of Origin:

```sh
git commit -s
```

The sign-off certifies that you wrote the change or have the right to submit it under the
project's license. See <https://developercertificate.org/>.

## Pull requests

- One topic per pull request. Small, reviewable commits with conventional messages
  (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
- Include a test that fails without the change. For hooks and the shim, add the in-game
  evidence (the relevant `ferroloom.log` lines) when the change is only observable there.
- Run the full suites before opening the PR and paste the result in the description.
- Update the README, `docs/`, the design spec or the plan when behavior changes.
- Expect a review of the JNI boundary, the class-loader split (Knot owns only the shim jar),
  and the thread/phase rules.

## Style

- Rust: `cargo fmt`, edition 2024, no new registry dependencies without a launcher lock.
- Java: release 25 bytecode, JUnit 5, no new runtime dependencies for the loader.
- Keep files focused; prefer small modules over large ones.
- Every new error must be typed and must name the mod, the path or the member involved.
