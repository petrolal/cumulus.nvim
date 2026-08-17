# Epic 7 Context: CI/CD Pipeline & Cross-Platform Distribution

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Automate cross-platform compilation of `cumulus-engine` across Linux (x86_64, aarch64) and macOS (arm64) using GraalVM Native Image via GitHub Actions, and provide seamless in-editor binary resolution/auto-download in the Neovim Lua engine bridge so end users never have to install SBT or GraalVM manually.

## Stories

- Story 7.1: GitHub Actions GraalVM Native Build Matrix
- Story 7.2: Auto-Download Binary in Engine Bridge

## Requirements & Constraints

- Automated compilation on push to `main` and release tags (`v*`) via GitHub Actions `.github/workflows/release-engine.yml`.
- Build target platforms:
  - `linux-x86_64` (Ubuntu runner)
  - `linux-aarch64` (Ubuntu ARM runner / QEMU / cross-build)
  - `darwin-arm64` (macOS M-series runner)
- Native binaries must be compiled with GraalVM Native Image using `sbt graalvm-native-image:packageBin` (`GraalVMNativeImagePlugin`).
- Release assets naming convention: `cumulus-engine-linux-x86_64`, `cumulus-engine-linux-aarch64`, `cumulus-engine-darwin-arm64`.
- Generate SHA-256 checksum manifest `checksums.sha256` published alongside release assets.
- CI pipeline must run `sbt test` before compiling native image to block broken releases.
- Neovim bridge must support automated download, verification against SHA-256 manifest, and installation to local data directory (`~/.local/share/nvim/cumulus/bin/cumulus-engine`).

## Technical Decisions

- **GraalVM Setup**: Use `graalvm/setup-graalvm` action with Java 21 and native-image component.
- **SBT Setup**: Use standard SBT runner actions or cache SBT dependencies (`sbt/setup-sbt` or native sbt in runner).
- **Binary Distribution Target**: GitHub Releases with attached artifacts + checksums.
- **Protocol & Binary Integrity**: Native binary outputs match SPEC-031 JSON envelope standard; Lua bridge verifies SHA256 before making executable.

## Cross-Story Dependencies

- Story 7.1 establishes the release assets naming and `checksums.sha256` contract that Story 7.2 downloads and verifies in Neovim.
