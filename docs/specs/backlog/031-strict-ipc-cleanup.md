# Specification: SPEC-031 - Strict IPC Cleanup & Error Standardization

## Metadata
- **Spec ID**: SPEC-031
- **Title**: Strict IPC Cleanup & Standardized Error Handling (Phase 1 Critical)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/main.rs` (extends)
  - `crates/cumulus-core/src/lib.rs` (new)
  - `lua/cumulus/util/rust.lua` (extends)
  - All subcommand handlers in `crates/cumulus-core/src/`

- **Implementation**: Rust (Lua bridge standardization)

---

## Architecture

**Standardized error envelope for all Rust commands. All 18+ subcommands emit structured JSON on success or failure, enabling robust Lua error handling and debug visibility.**

```
Any Rust Subcommand  →  Result<T, CumulusError>  →  JSON Envelope  →  Lua (checks success flag)  →  UI/Notification
```

- **Error Envelope (JSON)**: Every `cumulus-core` subcommand outputs a unified response:
  ```json
  {
    "success": true,
    "data": { /* command-specific result */ },
    "error": null
  }
  ```
  or on failure:
  ```json
  {
    "success": false,
    "data": null,
    "error": "File not found: /path/to/pom.xml",
    "error_code": "FILE_NOT_FOUND"
  }
  ```
- **Rust Implementation**: Define `CumulusError` enum with structured error variants (`FileNotFound`, `ParseError`, `InvalidInput`, `NetworkError`, `Timeout`). All handlers use `Result<T, CumulusError>` return type; `serde_json::to_string()` serializes the envelope.
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `call_rust()` helper checks `envelope.success` flag. If false, logs error to `vim.notify()` at WARN level and returns `nil`. If true, returns `envelope.data` decoded as Lua table. All 18+ IPC calls inherit this behavior automatically.

---

## Goal & Intent

Eliminate silent JSON decode failures and improve debuggability of Rust binary failures. Currently, when `cumulus-core` subcommands fail (missing file, parse error, network timeout), the Lua bridge catches the error silently with no visibility. Developers cannot distinguish between:
- Rust binary missing
- Rust subcommand not found
- Subcommand execution failed silently
- Subcommand output corrupted

Standardized error envelopes and Lua error logging transform debugging from "why did this silently do nothing?" to "saw error: File not found at /path/to/pom.xml".

---

## Scope Boundaries

**In scope:**
- Define `CumulusError` enum in `crates/cumulus-core/src/lib.rs`.
- Update all 18+ subcommand handlers to return `Result<T, CumulusError>`.
- Wrap all command outputs in standardized JSON envelope (success/data/error/error_code).
- Update `lua/cumulus/util/rust.lua` `call_rust()` helper to check `success` flag and log errors.
- Add stderr capture for diagnostic logging.

**Out of scope:**
- Modifying frozen DevOps specs.
- Auto-recovery from errors (Lua is responsible for retry/fallback logic if needed).
- Network error recovery strategies (that belongs in individual SPEC handlers like SPEC-009, SPEC-029).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/main.rs` already routes all subcommands; no new command infrastructure needed.
- `lua/cumulus/util/rust.lua` already has `call_rust()` helper (Phase 1 consolidation, commit 4b3350f); only needs envelope check added.
- All 18+ subcommand handlers are isolated in `src/` modules, so error handling can be added uniformly.

---

## Constraints & Guardrails

1. **Rust-First Directive**: Error handling logic MUST be in Rust. Lua only checks the success flag and logs.
2. **Backward Compatibility**: Existing Lua callers still work; they just get `nil` on error instead of silent success. Add optional debug logging parameter to `call_rust()` for developer visibility.
3. **Zero Free Files**: Error types live in `crates/cumulus-core/src/lib.rs` only.
4. **Performance Budget**: JSON envelope serialization must add <1ms per call.

---

## Execution Checklist

- [ ] **Task 1: Define Error Type in Rust (`crates/cumulus-core/src/lib.rs`)**
  - Create `lib.rs` if not present (or extend existing).
  - Define `CumulusError` enum with variants:
    ```rust
    pub enum CumulusError {
      FileNotFound(String),
      ParseError(String),
      InvalidInput(String),
      NetworkError(String),
      Timeout,
      Internal(String),
    }
    ```
  - Implement `Display` and `serde::Serialize` for CumulusError.
  - Define `CumulusResponse<T>` struct:
    ```rust
    #[derive(Serialize)]
    pub struct CumulusResponse<T> {
      pub success: bool,
      pub data: Option<T>,
      pub error: Option<String>,
      pub error_code: Option<String>,
    }
    ```

- [ ] **Task 2: Update Main Handler in `main.rs`**
  - Modify all subcommand handlers to wrap output in `CumulusResponse` before `println!()`.
  - Example:
    ```rust
    match cmd_result {
      Ok(data) => {
        let resp = CumulusResponse { success: true, data: Some(data), error: None, error_code: None };
        println!("{}", serde_json::to_string(&resp)?);
      }
      Err(e) => {
        let resp = CumulusResponse { 
          success: false, 
          data: None, 
          error: Some(e.to_string()), 
          error_code: Some(e.error_code()) 
        };
        eprintln!("{}", serde_json::to_string(&resp)?);
      }
    }
    ```

- [ ] **Task 3: Update All Subcommand Modules**
  - Replace `.unwrap()` with `?` operator in all 18+ subcommand handlers.
  - Example: In `log_parser.rs`, change:
    ```rust
    let content = std::fs::read_to_string(&file).unwrap();
    ```
    to:
    ```rust
    let content = std::fs::read_to_string(&file)
      .map_err(|e| CumulusError::FileNotFound(format!("{}: {}", file.display(), e)))?;
    ```

- [ ] **Task 4: Update Lua Bridge (`lua/cumulus/util/rust.lua`)**
  - Modify `call_rust()` helper to check `envelope.success` flag:
    ```lua
    local function call_rust(args, stdin_content)
      local result = vim.system(...)  -- existing logic
      local ok, decoded = pcall(vim.json.decode, result.stdout)
      if not ok or not decoded then
        vim.notify("[cumulus] JSON decode failed", vim.log.levels.WARN)
        return nil
      end
      if not decoded.success then
        vim.notify(
          "[cumulus] " .. (decoded.error or "Unknown error"),
          vim.log.levels.WARN
        )
        return nil
      end
      return decoded.data
    end
    ```
  - Add optional debug parameter: `call_rust(args, stdin_content, { debug = true })` logs success responses to `vim.notify()`.

- [ ] **Task 5: Add Unit Tests**
  - Add `#[cfg(test)]` module to `src/lib.rs`:
    ```rust
    #[cfg(test)]
    mod tests {
      use super::*;

      #[test]
      fn test_error_serialization() {
        let err = CumulusError::FileNotFound("test.txt".into());
        let resp = CumulusResponse { ... };
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("\"success\":false"));
      }
    }
    ```
  - Add integration test: call each subcommand with invalid input, verify JSON envelope structure.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
cargo build --manifest-path crates/cumulus-core/Cargo.toml --release
luac -p lua/cumulus/util/rust.lua
nvim --headless "+checkhealth cumulus" +qa
bash scripts/validate.sh
```

### Acceptance Criteria
- [ ] `cargo test` passes with new error handling tests.
- [ ] All 18+ subcommands output valid JSON with `success` flag.
- [ ] Calling a subcommand with missing file returns `{ "success": false, "error": "..." }`.
- [ ] `call_rust()` returns `nil` when `success == false` and logs error via `vim.notify()`.
- [ ] Lua callers all still work (backward compatible).
- [ ] Zero `.unwrap()` calls remain in subcommand handlers (except for intentional panics in testing).
- [ ] Startup latency unaffected (<1ms per call overhead).
