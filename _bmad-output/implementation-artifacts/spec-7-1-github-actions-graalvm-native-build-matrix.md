---
title: 'Story 7.1: GitHub Actions GraalVM Native Build Matrix, Sonatype Publishing & Distribution Bootstrap'
type: 'feature'
created: '2026-08-16'
status: 'done'
baseline_commit: 'ce86850b073b6100c928c9ac44217da480b60fed'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-7-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `cumulus-engine` and `cumulus.nvim` lack automated multi-architecture CI/CD releases, Maven Central Sonatype publishing under the `io.github.petrolal` namespace, and an end-to-end bootstrap installer (`bootstrap.sh`) to setup the complete Neovim distribution from scratch on fresh systems.

**Approach:** 
1. Update `engine/build.sbt` and `engine/project/plugins.sbt` with Sonatype Central Portal publishing under `io.github.petrolal`, `sbt-ci-release`, PGP signing, and license metadata.
2. Implement `.github/workflows/release-engine.yml` with test gates, cross-platform GraalVM native builds (`linux-x86_64`, `linux-aarch64`, `darwin-arm64`), Maven Central publishing via `sbt ci-release`, and GitHub Release asset creation with `checksums.sha256`.
3. Provide an all-in-one `bootstrap.sh` installer at the repository root to automatically install system dependencies, Java/GraalVM, Coursier, Neovim, font, and bootstrap the Neovim distribution.

## Boundaries & Constraints

**Always:**
- Use `io.github.petrolal` as the Maven namespace / organization in `engine/build.sbt`.
- Run `sbt test` in `engine/` before native image compilation or Sonatype publishing.
- Use `graalvm/setup-graalvm` action with Java 21 and native-image support.
- Output release assets with standard naming: `cumulus-engine-linux-x86_64`, `cumulus-engine-linux-aarch64`, `cumulus-engine-darwin-arm64`.
- Generate and publish a unified `checksums.sha256` manifest alongside the binaries.
- Ensure the workflow triggers on tag pushes (`v*`), push to `main`, and manual dispatch (`workflow_dispatch`).
- Root `bootstrap.sh` must support standard Linux package managers (pacman, apt, dnf) and macOS (brew).

**Ask First:**
- Additional OS/architecture targets beyond the standard three (`linux-x86_64`, `linux-aarch64`, `darwin-arm64`).

**Never:**
- Hardcode secrets or credentials in the workflow file.
- Publish release assets without passing test validation and checksum calculation.

## I/O & Edge-Case Matrix

| Scenario | Trigger / Input | Expected Output / Behavior | Error Handling |
|----------|----------------|---------------------------|----------------|
| Tag Push (`v*`) | Tag push `v0.1.0` | Runs tests, builds 3 native binaries, publishes to Sonatype Maven Central (`io.github.petrolal`), computes sha256 sums, publishes GitHub Release | Job fails on test or build error; release & publish blocked |
| Push to `main` | Commit to `main` | Runs tests and builds native binaries, uploads build artifacts for verification | Job fails on error; reports CI status |
| Manual Dispatch | `workflow_dispatch` | Runs test and build matrix on selected branch, uploads artifacts | Job fails on error; displays build logs |
| Fresh Bootstrap | `./bootstrap.sh` on fresh machine | Installs system packages, Java 21, Coursier, ripgrep, Neovim, syncs plugins, and validates health | Gracefully warns on missing sudo or non-fatal package skips |

</frozen-after-approval>

## Code Map

- `engine/build.sbt` -- SBT build definition updated with `organization := "io.github.petrolal"`, Sonatype Central Portal publishing settings, POM metadata, and `versionScheme`.
- `engine/project/plugins.sbt` -- SBT plugins updated with `sbt-ci-release`, `sbt-sonatype`, and `sbt-pgp`.
- `.github/workflows/release-engine.yml` -- GitHub Actions CI/CD workflow defining test gate, matrix build, Sonatype publish, checksum generation, and GitHub release publication.
- `bootstrap.sh` -- Standalone root bootstrap script that detects package manager, installs system & JVM dependencies, sets up Neovim, and invokes `scripts/install.sh`.
- `scripts/install.sh` -- Neovim plugin and engine initialization script.

## Tasks & Acceptance

**Execution:**
- [x] `engine/build.sbt` -- Add Sonatype publishing settings and `io.github.petrolal` namespace -- Enables publishing `cumulus-engine` artifacts to Maven Central.
- [x] `engine/project/plugins.sbt` -- Add `sbt-ci-release` and publishing plugins -- Enables automated PGP signing and Sonatype releases in CI.
- [x] `.github/workflows/release-engine.yml` -- Create GitHub Actions release & publishing workflow -- Provides automated CI test gate, GraalVM native build matrix (Linux x86_64, Linux ARM64, macOS ARM64), Sonatype publishing, SHA-256 checksum manifest generation, and GitHub Release asset publishing.
- [x] `bootstrap.sh` -- Create root bootstrap installer -- Provides automated installation of system tools, Java/GraalVM, Coursier, and full `cumulus` neovim distribution setup.

**Acceptance Criteria:**
- Given a push to `main`, version tag `v*`, or manual `workflow_dispatch`
- When GitHub Actions runs `.github/workflows/release-engine.yml`
- Then `sbt test` executes in `engine/` before native image compilation or publishing
- And native binaries are compiled for `linux-x86_64`, `linux-aarch64`, and `darwin-arm64` using GraalVM Native Image
- And binaries are packaged with naming `cumulus-engine-linux-x86_64`, `cumulus-engine-linux-aarch64`, and `cumulus-engine-darwin-arm64`
- And for version tags (`v*`), `sbt ci-release` publishes artifacts to Sonatype Maven Central under `io.github.petrolal`
- And a `checksums.sha256` manifest containing checksums of all 3 binaries is generated and uploaded to GitHub Release
- And executing `./bootstrap.sh` detects the host OS, installs required tools, syncs plugins, and verifies installation.

## Spec Change Log

- 2026-08-16: Completed Story 7.1 implementation (build.sbt Sonatype Central publishing configuration, sbt-ci-release plugin, release-engine.yml GitHub Actions multi-arch GraalVM matrix build & Sonatype publish workflow, and root bootstrap.sh distribution installer).


## Design Notes

Publishing & Matrix Configuration:
- `engine/build.sbt`:
  - `organization := "io.github.petrolal"`
  - `sonatypeCredentialHost := "central.sonatype.com"`
  - `versionScheme := Some("early-semver")`
  - `publishTo := sonatypePublishToBundle.value`
- Secrets used in CI:
  - `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `PGP_SECRET`, `PGP_PASSPHRASE`
- Matrix runners:
  - `ubuntu-latest` (`linux-x86_64`)
  - `ubuntu-24.04-arm` (`linux-aarch64`)
  - `macos-latest` (`darwin-arm64`)

## Verification

**Commands:**
- `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release-engine.yml'))"` -- expected: Valid YAML syntax without errors
- `bash -n bootstrap.sh` -- expected: Valid bash syntax without syntax errors
- `bash -n scripts/install.sh` -- expected: Valid bash syntax without syntax errors
- `(cd engine && sbt compile)` -- expected: Successful compilation with updated build.sbt and plugins

## Suggested Review Order

**CI/CD Pipeline & Distribution**

- GitHub Actions multi-arch GraalVM native build matrix and release publication
  [`release-engine.yml:1`](../../.github/workflows/release-engine.yml#L1)

- System bootstrap installer with automatic package manager detection and setup
  [`bootstrap.sh:1`](../../bootstrap.sh#L1)

**Build & Publishing Configuration**

- Sonatype Central Portal publishing settings, POM metadata, and UTC build time
  [`build.sbt:1`](../../engine/build.sbt#L1)

- SBT CI release plugin configuration for automated PGP signing
  [`plugins.sbt:1`](../../engine/project/plugins.sbt#L1)

