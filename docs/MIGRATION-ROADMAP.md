# Cumulus Neovim: Rust Migration Roadmap & Spec Index

## Overview

This document indexes all migration-related specifications and provides the unified roadmap for migrating business logic from Lua to Rust. The migration follows a phased approach, with dependencies clearly marked.

**Current Status**: Phase 1 (Consolidation & Error Handling) ~94% complete. Ready to begin Phase 2.

---

## Migration Phases & Dependencies

### Phase 1: High-Impact Lua Consolidation & Error Standardization (Critical)
**Status**: In Progress (SPEC-031 moved to ACTIVE)

| Spec ID | Title | Status | Purpose |
|---------|-------|--------|---------|
| **SPEC-031** | Strict IPC Cleanup & Error Standardization | **ACTIVE** | ⚠️ **GATES PHASE 2** — Standardize all 18+ Rust commands to emit structured JSON envelopes (success/data/error). Enable robust Lua error handling + debug visibility. |

**Phase 1 Completion Criteria**: All Rust subcommands return `{ success: bool, data: T?, error?: string }` envelope. Lua `call_rust()` checks success flag and logs errors via `vim.notify()`.

**Unlock**: Once complete, Phase 2 (Backlog Specs Wave 1) can proceed without risk of silent failures.

---

### Phase 2: Backlog Specs Wave 1 — File Parsers (8–10 hours)
**Depends on**: Phase 1 (SPEC-031) ✓

File system & configuration parsing specs. These unblock daily developer workflows.

| Spec ID | Title | Status | Rust Scope | Effort |
|---------|-------|--------|-----------|--------|
| **SPEC-005** | JDTLS Project Sync Health Check | BACKLOG | `check-jdtls-sync`: mtime scan for pom.xml/build.gradle changes | 2–3 hrs |
| **SPEC-012** | Gradle Wrapper Version Lock & SHA-256 Verify | BACKLOG | `verify-gradle-wrapper`: parse gradle-wrapper.properties + CI YAML | 2–3 hrs |
| **SPEC-030** | Session State & Layout Sanitizer | BACKLOG | `session-sanitize`: parse .vim scripts; strip ephemeral buffers | 2–3 hrs |

**Phase 2 Unlock**: Real-time JDTLS sync detection, build consistency checks, session integrity.

---

### Phase 3: Backlog Specs Wave 2 — Complex Parsing (6–8 hours)
**Depends on**: Phase 2 (SPEC-005, 012, 030)

AST/config parsing with higher algorithmic complexity.

| Spec ID | Title | Status | Rust Scope | Effort |
|---------|-------|--------|-----------|--------|
| **SPEC-010** | Runtime Exception Stack Trace Drill-Down | BACKLOG | `parse-stacktrace`: extract frames, map to source lines | 2–3 hrs |
| **SPEC-006** | SpringBoot Debug Hotswap Engine | BACKLOG | `spring-debug-config`: parse application.yml, generate debug JVM args | 3–4 hrs |

**Phase 3 Unlock**: One-keypress debug setup, inline exception navigation.

---

### Phase 4: Backlog Specs Wave 3 — Network/Database (9–12 hours)
**Depends on**: Phase 1 (SPEC-031) for error handling

Network-dependent specs with external API/database lookups.

| Spec ID | Title | Status | Rust Scope | Effort |
|---------|-------|--------|-----------|--------|
| **SPEC-009** | Dependency Lens & Version Checker | BACKLOG | `check-dependency-versions`: query Maven Central/Gradle Portal APIs | 3–5 hrs |
| **SPEC-029** | Dependency CVE Vulnerability Scanner | BACKLOG | `audit-deps`: match coordinates to offline CVE database | 4–6 hrs |

**Phase 4 Unlock**: Automated dependency auditing, outdated-dependency warnings.

---

### Phase 5: Lua Bridge Optimization (6–8 hours, Optional)
**Depends on**: Phase 1 + Phase 2

Migrate compute-heavy Lua helpers; consolidate overlapping functionality.

| Spec ID | Title | Status | Lua→Rust Migration | Effort |
|---------|-------|--------|-------------------|--------|
| **SPEC-036** | Maven & Gradle Build Command Consolidation | BACKLOG | Migrate gradle.lua + maven.lua wrapper discovery/offline detection to Rust | 2–3 hrs |
| **SPEC-037** | Session State Window Cleanup Integration | BACKLOG | Integrate SPEC-030's `session-sanitize` with session.lua; remove buffer loops | 1–2 hrs |
| **SPEC-038** | Test Runner & Sync Orchestration Integration | BACKLOG | Refactor test-runner.lua + sync-runner.lua to pure Lua orchestration; move parsing to Rust | 3–4 hrs |

**Phase 5 Unlock**: Reduced Lua boilerplate (~250 lines removed), faster execution, improved reliability.

---

## Spec Dependency Graph

```
Phase 1: SPEC-031 (Error Standardization) — ACTIVE
  ↓
  ├─ Phase 2a: SPEC-005 (JDTLS Sync Health)
  │              ↓
  │              SPEC-037 (Session Integration) — uses SPEC-030
  │
  ├─ Phase 2b: SPEC-012 (Gradle Wrapper Verify)
  │
  ├─ Phase 2c: SPEC-030 (Session Sanitizer)
  │              ↓
  │              SPEC-037 (depends on SPEC-030)
  │
  ├─ Phase 3: SPEC-010, SPEC-006 (Complex Parsing)
  │
  ├─ Phase 4: SPEC-009, SPEC-029 (Network/Database)
  │
  └─ Phase 5 (Parallel): SPEC-036, SPEC-038 (Lua Optimization)
     (Can run after Phase 1 independently of 2–4)
```

---

## Quick Reference: Spec Summaries

### SPEC-031 — Strict IPC Cleanup & Error Standardization ⚠️ CRITICAL
**File**: `docs/specs/active/031-strict-ipc-cleanup.md`

Standardizes all 18+ Rust commands to emit JSON envelopes with `success` flag. Replaces `.unwrap()` with proper error handling. Lua `call_rust()` checks success flag and logs errors.

**Why Critical**: SPEC-005–029 all depend on robust error handling. Skipping this risks silent failures.

**Effort**: ~2–3 hours  
**Current Status**: ACTIVE — Ready for implementation

---

### SPEC-005 — JDTLS Project Sync Health Check
**File**: `docs/specs/backlog/005-jdtls-sync-health.md`

Rust command `check-jdtls-sync` scans pom.xml/build.gradle mtime against JDTLS start time. Detects if classpath is stale.

**Unblocks**: SPEC-037 (session integration).  
**Effort**: 2–3 hrs  
**Lua**: Statusline badge "🔄 Resync needed" + `<leader>cjs` keymap

---

### SPEC-006 — SpringBoot Debug Hotswap Engine
**File**: `docs/specs/backlog/006-springboot-debug.md`

Rust command `spring-debug-config` parses application.yml, detects @SpringBootApplication, generates JDWP args. One-keypress debug session.

**Effort**: 3–4 hrs  
**Lua**: `<leader>ds` → spawn debugger with auto-configured JVM args

---

### SPEC-009 — Dependency Lens & Version Checker
**File**: `docs/specs/backlog/009-dependency-lens.md`

Rust command `check-dependency-versions` queries Maven Central/Gradle Plugin Portal for latest versions. Codelens shows outdated deps.

**Effort**: 3–5 hrs (network I/O + HTTP client setup)  
**Lua**: Inline diagnostics in pom.xml / build.gradle showing available versions

---

### SPEC-010 — Runtime Exception Stack Trace Drill-Down
**File**: `docs/specs/backlog/010-exception-drill.md`

Rust command `parse-stacktrace` extracts Java frames, maps to source files + line numbers. Enables frame-by-frame navigation.

**Effort**: 2–3 hrs  
**Lua**: `<leader>cx` → parse exception from clipboard + open first frame in editor

---

### SPEC-012 — Gradle Wrapper Version Lock & SHA-256 Verification
**File**: `docs/specs/backlog/012-gradle-wrapper-lock.md`

Rust command `verify-gradle-wrapper` parses gradle-wrapper.properties, compares local vs CI Gradle versions, verifies SHA-256.

**Effort**: 2–3 hrs  
**Lua**: `:checkhealth cumulus` → Gradle Wrapper section shows version mismatch warnings

---

### SPEC-029 — Dependency CVE Vulnerability & Security Scanner
**File**: `docs/specs/backlog/029-audit-deps-cve.md`

Rust command `audit-deps` matches pom.xml/build.gradle coordinates against offline CVE database. Shows Log4Shell, Spring4Shell, etc.

**Effort**: 4–6 hrs (requires CVE DB integration)  
**Lua**: Inline diagnostics in pom.xml / build.gradle with CVE severity + fix version

---

### SPEC-030 — Session State & Layout Sanitizer
**File**: `docs/specs/backlog/030-session-sanitize.md`

Rust command `session-sanitize` parses .vim session files, strips ephemeral Snacks buffers + blank `[No Name]` windows.

**Unblocks**: SPEC-037 (Lua integration).  
**Effort**: 2–3 hrs  
**Lua**: Automatic on PersistenceSavePre; removes session pollution

---

### SPEC-036 — Maven & Gradle Build Command Consolidation
**File**: `docs/specs/backlog/036-build-cmd-consolidation.md`

Migrates gradle.lua (147 LOC) + maven.lua (152 LOC) wrapper discovery logic to Rust `get-build-command`. Lua becomes pure UI.

**Benefit**: Consolidates 60 lines of file I/O + chmod logic. Async execution.  
**Effort**: 2–3 hrs  
**Lua Reduction**: ~60 LOC removed (40% shrink)

---

### SPEC-037 — Session State Window Cleanup Integration
**File**: `docs/specs/backlog/037-session-state-integration.md`

Integrates SPEC-030's Rust `session-sanitize` with session.lua. Replaces manual buffer inspection loops with Rust .vim parsing.

**Prerequisite**: SPEC-030 (Rust implementation) + SPEC-031 (error handling)  
**Effort**: 1–2 hrs (mostly Lua wiring)  
**Lua Reduction**: ~50 LOC removed (35% shrink)

---

### SPEC-038 — Test Runner & Sync Orchestration Integration
**File**: `docs/specs/backlog/038-test-sync-orchestration.md`

Refactors test-runner.lua (140 LOC) + sync-runner.lua (152 LOC) to pure Lua orchestration. Moves test/dependency parsing to Rust backends.

**Creates Reusable Pattern**: For async workflows (test runners, linters, build tools).  
**Effort**: 3–4 hrs  
**Lua Reduction**: ~90 LOC removed (30% shrink)

---

## Implementation Checklist (For You)

**Immediate (This Week)**:
- [ ] Read through SPEC-031 in detail (`docs/specs/active/031-strict-ipc-cleanup.md`)
- [ ] Implement `CumulusError` enum + JSON envelope in Rust
- [ ] Update all 18+ subcommand handlers to use error envelope
- [ ] Update Lua `call_rust()` to check success flag + log errors
- [ ] Run `cargo test` + headless verification
- [ ] Move SPEC-031 to review/ when implementation complete
- [ ] Git commit: "SPEC-031: implement strict IPC cleanup"

**Next Sprint (Phase 2 — Backlog Specs)**:
- [ ] Pick 1–2 of: SPEC-005, SPEC-012, SPEC-030
- [ ] Implement Rust backend + Lua bridge
- [ ] Verify with acceptance criteria
- [ ] Move spec to `docs/specs/active/` then `docs/specs/review/`

**Following Sprint (Phase 5 — Lua Optimization, Optional)**:
- [ ] If Phase 2 specs are stable, start SPEC-036 / SPEC-037 / SPEC-038
- [ ] These reduce Lua boilerplate; lower priority than Phase 2

---

## Key Metrics

| Metric | Current | After All Phases |
|--------|---------|------------------|
| **Rust Modules** | 21 | 21–28 (+7 new commands) |
| **Rust LOC** | ~2,900 | ~4,500 |
| **Lua LOC** | ~1,556 | ~1,200 (22% reduction) |
| **Boilerplate Reduced** | — | ~240 lines |
| **Performance Gains** | — | +20–40% (regex lazy-compilation) |
| **Test Coverage** | 27 tests | 33+ tests (+22%) |

---

## Additional Resources

- **Artifact**: Comprehensive Rust migration analysis → https://claude.ai/code/artifact/9643697d-c47f-44a4-a100-bcefd1250cf2
- **CLAUDE.md**: Rust-First Directive + Lua Bridge Pattern
- **RUST_ARCHITECTURE_ROADMAP.md**: Broader Rust engine design
- **LSP_PERFORMANCE_PROFILING.md**: Performance baselines

---

## Notes

- All specs follow the established **Rust-First, Lua-Bridge** pattern (see CLAUDE.md).
- No specs modify frozen DevOps files (cloud-*.lua, lsp-devops.lua, tools-dap-devops.lua).
- Phase 1 (SPEC-031) is blocking; do not defer.
- Phases 2–4 can be implemented in parallel once Phase 1 lands.
- Phase 5 is optional (nice-to-have consolidation) and can run anytime after Phase 1.

---

**Last Updated**: 2026-08-10  
**Maintainer**: AI Systems Architect  
**Specs Location**: `docs/specs/backlog/`, `docs/specs/active/`, `docs/specs/review/`, `docs/specs/completed/`
