# Active Plan – Cumulus Rust Migration

## Architecture Principle

> **Lua is a bridge to the Rust backend. That is it.**

```
Neovim  →  Lua (bridge)  →  cumulus-core (Rust binary)
```

| Layer | Responsibility |
|-------|---------------|
| **Rust** (`crates/cumulus-core`) | All logic: parsing, file I/O, network, validation, analysis |
| **Lua** (`lua/cumulus/`) | One job: call the binary, pass results to Neovim APIs |

**Rules:**
- No Lua parsing. No Lua analysis. No Lua fallbacks.
- If the binary is missing → fail explicitly, never fall back to Lua logic.
- New features: implement in Rust first, then write the minimal Lua bridge.

---

## Implementation Pattern (per spec)

```
R1  New Rust module in crates/cumulus-core/src/<feature>.rs
R2  New subcommand wired in crates/cumulus-core/src/main.rs
R3  Binding added to lua/cumulus/util/rust.lua (the only Lua bridge file)
R4  Keymap/autocmd wired in keymaps.lua or autocmds.lua (Neovim glue only)
R5  Unit test in crates/cumulus-core/tests/
R6  Checklist updated, cargo test + validate.sh pass
```

---

## Pending — Review Queue (`docs/specs/review/`)

- [ ] `003-compliance-remediation.md`
- [ ] `007-test-runner.md`
- [ ] `011-offline-mode.md`
- [ ] `013-code-inspections.md`
- [ ] `016-rust-helper-migration.md`
- [ ] `017-rust-multimodule-stacktrace.md`
- [ ] `018-endpoints-extractor.md`
- [ ] `019-coverage-parser.md`
- [ ] `020-migration-validator.md`
- [ ] `021-spring-beans.md`
- [ ] `022-log-indexer.md`
- [ ] `023-import-optimizer.md`
- [ ] `024-k8s-validator.md`
- [ ] `025-git-conflicts.md`

## Pending — Backlog (`docs/specs/backlog/`)

- [ ] `002-html-xml-markup-expansion.md`
- [ ] `004-build-error-capture.md`
- [ ] `005-jdtls-sync-health.md`
- [ ] `006-springboot-debug.md`
- [ ] `008-multimodule-nav.md`
- [ ] `009-dependency-lens.md`
- [ ] `010-exception-drill.md`
- [ ] `012-gradle-wrapper-lock.md`
- [ ] `014-health-checks.md`
- [ ] `015-nvimrc-template.md`
- [ ] `026-dependency-resolver.md`
- [ ] `027-codelens-engine.md`
- [ ] `028-build-order-dag.md`
- [ ] `029-audit-deps-cve.md`
- [ ] `030-session-sanitize.md`

---

## Existing Lua Files to Refactor (Lua logic → Rust)

These files still contain logic that must move to Rust:

| File | Lua logic to remove | Rust subcommand to create |
|------|--------------------|-----------------------------|
| `lua/cumulus/util/maven.lua` | `pom.xml` string matching for plugin goals | `parse-pom-goals` |
| `lua/cumulus/util/gradle.lua` | `gradle tasks` output parsing loop | `parse-gradle-tasks` |
| `lua/cumulus/util/test-runner.lua` | `detect_test_info()` regex on buffer lines | `detect-test-context` |
| `lua/cumulus/util/multimodule.lua` | Both Lua fallback parsers | remove fallbacks entirely |

---

## Validation

After each spec is implemented:
```bash
cargo test
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua
```


## Backlog
- Specs pending migration are listed in `docs/specs/review/`:
  - `003‑compliance‑remediation.md`
  - `007‑test‑runner.md`
  - `011‑offline‑mode.md`
  - `013‑code‑inspections.md`
  - `016‑rust‑helper‑migration.md`
  - `017‑rust‑multimodule‑stacktrace.md`
  - `018‑endpoints‑extractor.md`
  - `019‑coverage‑parser.md`
  - `020‑migration‑validator.md`
  - `021‑spring‑beans.md`
  - `022‑log‑indexer.md`
  - `023‑import‑optimizer.md`
  - `024‑k8s‑validator.md`
  - `025‑git‑conflicts.md`

## Review
- After each Rust module and Lua bridge are added, run `luac -p` on the Lua file and `cargo check` on the Rust code.
- Update the checklist in the spec file, moving it from `review/` to `active/` once the implementation is verified.

## Active
- **Context**
  - The subagent quota has been exhausted, preventing immediate execution of the teamwork subagents.
  - A timer has been scheduled for **9 897 seconds** (≈ 2 h 45 m) to notify when the quota resets.
- **Plan Steps**
  1. **Wait for quota reset** – The timer set via `schedule` will fire with the prompt:
     > "Quota reset reached. Ready to retry subagent invocations for pending specs."
  2. **On timer notification** – Re‑invoke the teamwork subagents one‑by‑one for each spec in the **Backlog** (see above).
  3. **Verification** – After each batch completes, run `bash scripts/validate.sh` and ensure all checks pass.
  4. **Completion** – When all specs have moved to `docs/specs/completed/` and validation succeeds, mark the overall task as **DONE**.

**Notes**
- This plan follows the **Rust migration idea**: each spec is implemented by adding a dedicated Rust module and corresponding Lua bridge, then validated before promotion.
- No subscription upgrade is required; the plan works within the current quota limits.
- If the quota does not reset as expected, consider upgrading the subscription or handling the remaining specs manually.
