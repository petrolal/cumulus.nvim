---
title: 'Story 7.2: Auto-Download Binary in Engine Bridge'
type: 'feature'
created: '2026-08-16'
status: 'done'
baseline_commit: 'd62844255b0d504266f8bd90ae5ee0b135c98574'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-7-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-7-1-github-actions-graalvm-native-build-matrix.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** When users install `cumulus.nvim` on a fresh system without SBT or GraalVM, `cumulus-engine` is unavailable, causing `:checkhealth cumulus` to warn and falling back on degraded functionality unless manually compiled.

**Approach:** Implement automated platform detection, binary download from GitHub Releases, SHA-256 checksum verification, and installation to `stdpath("data")/cumulus/bin/cumulus-engine` inside `lua/cumulus/util/engine.lua`, with a user-facing command `:CumulusInstallEngine` and integration into `:checkhealth cumulus`.

## Boundaries & Constraints

**Always:**
- Use `vim.loop.os_uname()` (or `vim.uv.os_uname()`) to detect `sysname` and `machine` (`linux-x86_64`, `linux-aarch64`, `darwin-arm64`).
- Fetch the appropriate release binary and `checksums.sha256` manifest from GitHub Releases (`https://github.com/petrolal/cumulus.nvim/releases/latest/download/`).
- Verify the downloaded binary's SHA-256 hash against the manifest before marking it executable.
- Install binary into `vim.fn.stdpath("data") .. "/cumulus/bin/cumulus-engine"` and apply executable permissions (`chmod +x`).
- Invalidate cache in `engine.lua` upon installation so subsequent calls immediately detect the new binary.
- Register the user-facing command `:CumulusInstallEngine` in Neovim.
- Provide clear user notifications via `vim.notify` during download progress, verification, and completion or failure.

**Ask First:**
- Custom release URLs or mirror repositories beyond the official repository.

**Never:**
- Execute or make executable any downloaded binary whose SHA-256 hash fails verification against `checksums.sha256`.
- Block the main Neovim UI thread with synchronous blocking network calls if an async option exists.

## I/O & Edge-Case Matrix

| Scenario | Trigger / Input | Expected Output / Behavior | Error Handling |
|----------|----------------|---------------------------|----------------|
| Linux x86_64 Install | `:CumulusInstallEngine` on Linux x86_64 | Downloads `cumulus-engine-linux-x86_64`, verifies SHA-256 against `checksums.sha256`, installs to data dir, makes executable, notifies success | Fails with notify error if network or checksum mismatch occurs |
| macOS ARM64 Install | `:CumulusInstallEngine` on macOS Apple Silicon | Downloads `cumulus-engine-darwin-arm64`, verifies SHA-256, installs to data dir, notifies success | Reports platform-specific download failure |
| Unsupported Platform | `:CumulusInstallEngine` on Windows / unknown arch | Reports unsupported OS/architecture error via `vim.notify` without downloading | Early return with warning level notification |
| Checksum Mismatch | Corrupted or truncated download | Checksum verification detects hash mismatch, deletes temp file, does NOT replace binary | Notifies `[cumulus] SHA-256 checksum verification failed` with error level |
| Healthcheck Missing Engine | `:checkhealth cumulus` when engine missing | Reports warning and suggests running `:CumulusInstallEngine` | N/A |
| Healthcheck Installed Engine | `:checkhealth cumulus` after install | Reports `cumulus-engine: active` with version and commit metadata from `ping` | N/A |

</frozen-after-approval>

## Code Map

- `lua/cumulus/util/engine.lua` -- Engine bridge module where platform resolution, download logic, SHA-256 verification, and `M.install()` function are implemented.
- `lua/cumulus/core/autocmds.lua` or `lua/cumulus/core/init.lua` -- Register user command `:CumulusInstallEngine`.
- `lua/cumulus/health.lua` -- Healthcheck provider checking engine presence and prompting `:CumulusInstallEngine` when missing.
- `scripts/validate.sh` -- Smoke verification script verifying command registration, engine module installation APIs, and health integration.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/engine.lua` -- Implement `M.detect_platform()`, `M.install()`, and SHA-256 checksum verification -- Enables automated platform detection and secure downloading of pre-built `cumulus-engine`.
- [x] `lua/cumulus/core/init.lua` -- Register `:CumulusInstallEngine` command -- Exposes the command to users to trigger engine installation.
- [x] `lua/cumulus/health.lua` -- Update health check to advise `:CumulusInstallEngine` -- Guides users to install the engine with a single command when missing.
- [x] `scripts/validate.sh` -- Add validation step for `:CumulusInstallEngine` command and engine installation API -- Validates engine bridge installer in headless smoke tests.

**Acceptance Criteria:**
- Given `:checkhealth cumulus` reports "engine not found"
- When the user runs `:CumulusInstallEngine` command
- Then the host platform is resolved via `os_uname` (`linux-x86_64`, `linux-aarch64`, `darwin-arm64`)
- And the platform binary and `checksums.sha256` are downloaded from GitHub releases
- And the downloaded binary is verified against the checksum manifest
- And the verified binary is moved to `stdpath("data")/cumulus/bin/cumulus-engine` and chmod +x is applied
- And subsequent `:checkhealth cumulus` detects the active engine via `engine.ping()`
- And progress/status is notified via `vim.notify`

## Spec Change Log

- 2026-08-16: Implemented Story 7.2 (`M.detect_platform()`, `M.install()` with SHA-256 checksum validation and permission setup in `engine.lua`, `:CumulusInstallEngine` command in `core/init.lua`, healthcheck advice in `health.lua`, and smoke test validation in `scripts/validate.sh`).

## Design Notes

Platform mapping:
- `uname.sysname == "Linux"`:
  - `uname.machine == "x86_64"` &rarr; `cumulus-engine-linux-x86_64`
  - `uname.machine == "aarch64"` or `arm64` &rarr; `cumulus-engine-linux-aarch64`
- `uname.sysname == "Darwin"`:
  - `uname.machine == "arm64"` or `aarch64` &rarr; `cumulus-engine-darwin-arm64`

Download & Verification flow:
1. `M.detect_platform()` returns target name or `nil, err`.
2. Temp directory created via `vim.fn.tempname()`.
3. Download `checksums.sha256` and target binary using `curl -fsSL` (with `vim.system`).
4. Read `checksums.sha256` to extract expected SHA-256 hash for target binary name.
5. Compute hash of downloaded temp binary via `sha256sum` (Linux) or `shasum -a 256` (macOS).
6. Compare hashes. If matched, create `stdpath("data")/cumulus/bin` if needed, move temp binary to destination, set permissions `0755` (`vim.loop.fs_chmod`), and call `M.invalidate_cache()`.

## Verification

**Commands:**
- `nvim --headless -u init.lua "+lua local e = require('cumulus.util.engine'); assert(type(e.detect_platform) == 'function'); assert(type(e.install) == 'function'); print('✔ Engine install APIs verified')" +qa` -- expected: Clean zero exit code
- `nvim --headless -u init.lua "+lua assert(vim.fn.exists(':CumulusInstallEngine') == 2); print('✔ :CumulusInstallEngine command verified')" +qa` -- expected: Clean zero exit code
- `bash scripts/validate.sh` -- expected: All validations pass

## Suggested Review Order

**Engine Bridge & Auto-Download**

- Platform detection, checksum verification, and asynchronous download implementation
  [`engine.lua:62`](../../lua/cumulus/util/engine.lua#L62)

- User command `:CumulusInstallEngine` registration
  [`init.lua:6`](../../lua/cumulus/core/init.lua#L6)

**Health & Verification**

- Healthcheck guidance and command recommendation
  [`health.lua:54`](../../lua/cumulus/health.lua#L54)

- Headless smoke verification for engine bridge installer
  [`validate.sh:48`](../../scripts/validate.sh#L48)

