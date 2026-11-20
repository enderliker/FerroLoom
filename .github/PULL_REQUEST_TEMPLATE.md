## Summary

<!-- What changed and why. Link the issue: Fixes #123. -->

## How it was tested

<!-- Paste the commands and their result. -->

- [ ] `cargo test`
- [ ] `cargo horizon-mod check`
- [ ] `(cd java && ./gradlew test)`
- [ ] In-game check (if a hook, the shim or the loader changed) — log lines below

```
<paste the relevant ferroloom.log lines>
```

## Checklist

- [ ] Every commit is signed off (`git commit -s`, DCO).
- [ ] No Mojang code or assets, and the shim still only reads names and descriptors.
- [ ] `Cargo.lock` keeps the game engine's graph (no `native-lock --update-lock`).
- [ ] Comments and documentation are in English.
- [ ] README/docs updated when behavior changed.
