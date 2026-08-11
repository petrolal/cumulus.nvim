# Specification: SPEC-034 - REST Endpoints Interactive HTTP Client Generator

## Metadata
- **Spec ID**: SPEC-034
- **Title**: REST Endpoints Interactive HTTP Client Generator (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/endpoints.rs` (extends SPEC-018)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/endpoints.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua HTTP request string generation)

---

## Architecture

**Lua is a bridge to the Rust backend. All REST endpoint parsing and RFC 7230 `.http` client request generation live in Rust.**

```
Endpoints Picker  →  Lua (rust.generate_http_request)  →  cumulus-core generate-http-request  →  JSON Response  →  Interactive .http Buffer
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Endpoints Tool Window and HTTP Client (`Generate Request in HTTP Client`) for live REST API testing in Spring Boot and JAX-RS applications.
- **Rust Engine (`crates/cumulus-core`)**: `generate-http-request --file <path> --line <num>` inspects Spring `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, or `@RequestMapping` annotations at target line, resolves path variables and query params, constructs RFC 7230 `.http` request content, and outputs JSON payload:
  ```json
  {
    "http_method": "POST",
    "url": "http://localhost:8080/api/v1/users",
    "headers": {
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    "sample_body": "{\n  \"username\": \"string\",\n  \"email\": \"user@example.com\"\n}",
    "raw_http_text": "POST http://localhost:8080/api/v1/users\nContent-Type: application/json\nAccept: application/json\n\n{\n  \"username\": \"string\",\n  \"email\": \"user@example.com\"\n}\n"
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.generate_http_request(file_path, line_num)` invokes `cumulus-core` and returns the decoded request string.
- **UI Integration**: Selecting an endpoint in `<leader>cje` (JVM Endpoints) provides a split option to open an interactive `.http` scratch buffer ready for execution via `kulala.nvim` / `rest.nvim` / `:DB`.

---

## Goal & Intent
Upgrade the REST endpoint extractor (from SPEC-018) to provide IntelliJ Ultimate HTTP Client parity, allowing developers to generate and execute live HTTP requests directly from Spring Boot controller methods.

---

## Scope Boundaries

**In scope:**
- Extending `crates/cumulus-core/src/endpoints.rs` to generate RFC 7230 `.http` request content.
- Adding `generate-http-request` subcommand to `main.rs`.
- IPC binding in `lua/cumulus/util/rust.lua`.
- UI action in `lua/cumulus/util/endpoints.lua` to open `.http` scratch buffer.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/endpoints.rs` already extracts Spring REST endpoints. `SPEC-034` adds request template generation.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All HTTP request string construction and JSON body template generation MUST be in Rust (`crates/cumulus-core/src/endpoints.rs`).
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Request generation completes under 2ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Expansion (`crates/cumulus-core`)**
  - Extend `crates/cumulus-core/src/endpoints.rs`.
  - Add `GenerateHttpRequest { file: PathBuf, line: usize }` subcommand to `main.rs`.
  - Implement request generator (HTTP method, headers, JSON body mock).
  - Add Rust unit tests in `endpoints.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.generate_http_request(file_path, line_num)` function calling `cumulus-core generate-http-request`.

- [ ] **Task 3: UI Integration (`lua/cumulus/util/endpoints.lua`)**
  - Add option in `endpoints.lua` picker to generate and open `.http` request buffer.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/util/endpoints.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new HTTP request generator unit tests in `endpoints.rs`.
- [ ] `generate-http-request` returns valid RFC 7230 `.http` request content for Spring controllers.
- [ ] Endpoint picker allows opening interactive `.http` buffer pre-populated with headers and body.
- [ ] Zero pure-Lua HTTP request string generation code.
