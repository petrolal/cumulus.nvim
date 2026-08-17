# Epic 1 Context: Scala 3 Engine Foundation & Standard Protocol

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Establish the foundation for the native Scala engine (`cumulus-engine`) by configuring the SBT build environment with GraalVM `native-image` support and implementing the standardized JSON execution protocol. This delivers an AOT-compiled binary with sub-10ms startup latency and a zero-reflection JSON response envelope for seamless Neovim integration.

## Stories

- ✅ Story 1.1: SBT Scala 3 Engine Project Initialization (DONE)
- ✅ Story 1.2: Protocol Envelope (`CumulusResponse[T]`) & CLI Router with Ping (DONE)

## Requirements & Constraints

- **Engine Location & Build Tools**: Native engine source resides in `{project-root}/engine/` using Scala 3.5.2 and SBT 1.10.2 with `sbt-native-image` 0.5.0 (NativeImagePlugin) and `sbt-buildinfo` 0.12.0. ✅ IMPLEMENTED
- **Core Dependencies**: Build configuration must declare `uPickle` for JSON serialization, `os-lib` for filesystem operations, `scala-xml` for XML parsing, and `munit` for testing.
- **Compile-Time Metadata**: `sbt-buildinfo` must inject build metadata (`version`, `scalaVersion`, `gitCommit`, `buildTime`) into `BuildInfo`.
- **Protocol & Performance Standards**:
  - ✅ Binary compilation via `sbt nativeImage` producing executable `cumulus-engine` (15MB, AOT-compiled GraalVM native binary).
  - ✅ Standardized JSON output envelope (`CumulusResponse[T]`) with `success`, `data`, `error`, `error_code` fields.
  - ✅ Zero runtime reflection; JSON serialization via uPickle compile-time macros only. No reflection-based serializers.
  - ✅ Channel isolation: Stdout for JSON payloads; all logging/debug to stderr (architecture in place, logging deferred to future stories).

## Technical Decisions

- **Architecture Paradigm (AD-1)**: All engine code built using idiomatic Scala 3.4+ top-level definitions, functional case classes, and macro-based derivations.
- **Zero-Reflection Serialization (AD-2)**: Type-safe JSON serialization implemented via `upickle.default.macroRW` derivation for `CumulusResponse[T]` and data payloads.
- **CLI Subcommand Router (AD-3)**: Direct pattern matching on `args: Array[String]` in `cumulus.Main` with zero third-party CLI framework dependencies. Subcommands match `SPEC-031` 1-to-1 (initial subcommand: `ping`). Unknown commands return an `INVALID_INPUT` error envelope.
- **GraalVM Native Image Plugin (AD-4)**: ✅ AOT native compilation configured through `sbt-native-image` 0.5.0 (org.scalameta) in `project/plugins.sbt` and `build.sbt`. Plugin auto-manages GraalVM via Coursier; produces 15MB static binary via `sbt nativeImage` task.
- **Response Envelope Schema (AD-5)**:
  - Envelope layout: `{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`.
  - Error enum `CumulusError` standardizes error codes: `FILE_NOT_FOUND`, `PARSE_ERROR`, `INVALID_INPUT`, `NETWORK_ERROR`, `TIMEOUT`, `INTERNAL_ERROR`.
- **Module Structure** (✅ COMPLETE):
  - `engine/build.sbt` (Scala 3.5.2, sbt-native-image 0.5.0, dependencies, BuildInfo, nativeImageOptions)
  - `engine/project/plugins.sbt` (sbt-native-image 0.5.0, sbt-buildinfo 0.12.0)
  - `engine/project/build.properties` (sbt 1.10.2)
  - `engine/src/main/scala/cumulus/Main.scala` (CLI router with pattern-matching on subcommands, envelope helpers)
  - `engine/src/main/scala/cumulus/protocol/Envelope.scala` (`CumulusResponse[T]`, `CumulusError` enum, custom JSON serialization)

## Cross-Story Dependencies

- **Internal** (✅ SATISFIED): Story 1.2 depends on Story 1.1. Story 1.1 established the SBT project, dependencies (uPickle, munit, os-lib, scala-xml), and BuildInfo compile-time metadata. Story 1.2 implemented `CumulusResponse[T]`, `CumulusError` enum, and CLI router with `ping` subcommand.
- **Downstream**: Epic 1 foundation complete. Stories 1.3+ can implement additional subcommands (file operations, metadata, etc.) using the proven SBT build, GraalVM native-image pipeline, and protocol foundation established in 1.1 & 1.2.

## Completion Summary

**Project Status Overview:**
- ✅ **Epic 1: Scala 3 Engine Foundation & Standard Protocol** — COMPLETE
- ⏳ **Epics 2-7:** Planned but NOT STARTED

### Epic 1 Completion (DONE)

Both stories implemented, tested, and deployed to main:
- ✅ Story 1.1: SBT Scala 3 Engine Project Initialization (all 3 ACs satisfied, native binary verified)
- ✅ Story 1.2: Protocol Envelope & CLI Router with Ping (all 4 ACs satisfied, full review with patches applied)

Deliverables:
- 15MB AOT-compiled GraalVM native binary (`cumulus-engine`)
- Zero-reflection JSON protocol (`CumulusResponse[T]` with 6 standardized error codes)
- CLI subcommand router with pattern-matching (extensible for future stories)
- Build infrastructure: Scala 3.5.2, sbt 1.10.2, sbt-native-image 0.5.0, sbt-buildinfo 0.12.0

### Next Phase: Epics 2-7 (NOT YET STARTED)

Foundation complete. Ready to begin:
- **Epic 2:** Build Tooling, Multi-Module DAG & Workspace Intelligence (Stories 2.1-2.4)
- **Epic 3:** Code Intelligence & Framework Analysis (Stories 3.1-3.3)
- **Epic 4:** Testing, Diagnostics & Log Processing (Stories 4.1-4.4)
- **Epic 5:** DevOps, Integrity & Platform Tools (Stories 5.1-5.4)
- **Epic 6:** Neovim Lua Bridge Migration & Complete Rust Removal (Stories 6.1-6.2)
- **Epic 7:** CI/CD Pipeline & Cross-Platform Distribution (Stories 7.1-7.2)
