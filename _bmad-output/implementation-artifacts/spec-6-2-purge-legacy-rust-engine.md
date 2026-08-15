---
title: 'Epic 6 Story 6.2: Purge Legacy Rust Engine (crates/cumulus-core)'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: '0fcd11f'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-6-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repository still contains the legacy Rust engine directory `crates/cumulus-core/`, Cargo configs, and leftover references in `scripts/install.sh`, `scripts/validate.sh`, `.gitignore`, and error messages.

**Approach:** Completely delete `crates/cumulus-core/` and the `crates/` directory, update `.gitignore` to track Scala/SBT artifacts and ignore Scala build outputs, update scripts to build and test the Scala engine with `sbt`, and ensure zero leftover references to Rust/Cargo across the repository.

## Boundaries & Constraints

**Always:**
- Completely remove the `crates/cumulus-core/` directory and all its files (`Cargo.toml`, `Cargo.lock`, `src/`, `target/`).
- Remove the `crates/` directory if empty.
- Update `scripts/install.sh` and `scripts/validate.sh` to reference `engine/` and `sbt` instead of `crates/cumulus-core` and `cargo`.
- Update any leftover error messages (such as in `lua/cumulus/util/build-diagnostics.lua`) to reference `cumulus-engine` and `sbt` instead of `cumulus-core` and `cargo`.
- Ensure `.gitignore` ignores `engine/target/` and `engine/project/target/`.

**Never:**
- Do not leave any Rust source files, Cargo configurations, or references to `crates/cumulus-core` in the repository.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Search for Rust/Cargo in codebase | Grep for `cumulus-core`, `crates/`, `cargo` across codebase | 0 matches (except specs/planning artifacts documenting the migration) | Fix/remove any found references |
| Script validation | Run `bash scripts/validate.sh` | Runs cleanly, testing Scala engine if sbt is available | Fails with clear code if tests fail |

</frozen-after-approval>

## Code Map

- `crates/cumulus-core/` -- Deleted completely.
- `scripts/install.sh` -- Updated engine build step to use `sbt` and `engine/`.
- `scripts/validate.sh` -- Updated native engine test step to use `engine/` and `sbt test`.
- `.gitignore` -- Updated ignore patterns for Scala SBT project.
- `lua/cumulus/util/build-diagnostics.lua` -- Updated warning notification message.

## Tasks & Acceptance

**Execution:**
- [x] `crates/cumulus-core/` -- Delete `crates/cumulus-core/` and `crates/` directory -- Completely removes Rust codebase
- [x] `scripts/install.sh` -- Update installation script to build Scala native engine via `sbt` in `engine/` -- Eliminates Cargo references
- [x] `scripts/validate.sh` -- Update validation script to test Scala native engine via `sbt test` in `engine/` -- Eliminates Cargo references
- [x] `.gitignore` -- Add SBT ignore patterns (`engine/target/`, `engine/project/target/`) -- Properly ignores Scala build artifacts
- [x] `lua/cumulus/util/build-diagnostics.lua` -- Update error message mentioning `cumulus-core` and `cargo` -- Eliminates stale error messages

**Acceptance Criteria:**
- Given the repository root, when searching for `crates/cumulus-core` or `cargo build`, then zero occurrences are found in active codebase/scripts.
- Given `scripts/validate.sh`, when executed, then all validation steps pass including the Scala native helper engine test.

## Spec Change Log

## Verification

**Commands:**
- `bash scripts/validate.sh` -- expected: all validation steps pass
- `git status` -- expected: `crates/` removed, clean tree

## Suggested Review Order

**Removal of Rust Artifacts & Crates**

- Removed `crates/cumulus-core/` directory and Cargo configuration files
  [`spec-6-2-purge-legacy-rust-engine.md:42`](../../_bmad-output/implementation-artifacts/spec-6-2-purge-legacy-rust-engine.md#L42)

**Scripts & Tooling Migration**

- Updated `scripts/install.sh` dependency check and engine build step to use `sbt`
  [`scripts/install.sh:25`](../../scripts/install.sh#L25)

- Updated `scripts/validate.sh` native helper check to test `engine/` using `sbt test`
  [`scripts/validate.sh:48`](../../scripts/validate.sh#L48)

- Configured Scala/SBT build artifact ignore rules
  [`.gitignore:9`](../../.gitignore#L9)

**Diagnostics Message Cleanup**

- Replaced legacy `cumulus-core` error prompt with `cumulus-engine` instructions
  [`lua/cumulus/util/build-diagnostics.lua:22`](../../lua/cumulus/util/build-diagnostics.lua#L22)
