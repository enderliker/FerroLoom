# Support

## Read first

- [README](README.md): what works, what does not, and the settings.
- [docs/smoke.md](docs/smoke.md): the in-game check and its known failures.
- [docs/ferrofabric-deviations.md](docs/ferrofabric-deviations.md): what a Fabric mod can expect
  from the API, and what it cannot.
- [docs/superpowers](docs/superpowers): the design and the plans.

## Asking for help

- **A bug or a crash:** open an issue with the bug report template. Include the FerroLoom commit,
  the Horizon build, the JDK, the mod jars involved and the relevant
  `~/.local/share/horizon/ferroloom.log` lines (especially a `FerroUnimplementedException`, which
  names the member a mod needs).
- **A mod that needs a missing shim member or API module:** open an issue with the feature
  request template and the `javap` lines of what the mod calls.
- **A security problem:** do not open a public issue; see [SECURITY.md](SECURITY.md).

## What to expect

FerroLoom is a young compatibility layer: a mod works when the shim and the API cover what it
calls. A missing member fails loudly, with the mod and the member in the message. The fastest
path for a new mod is an issue listing the members it touches; that list is the work plan.