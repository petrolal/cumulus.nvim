# Epic 3 Context: Database & Cloud Services Integration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic brings the everyday capabilities of DataGrip, Postman, and gRPC GUI clients directly into the Neovim editing loop so an enterprise JVM developer never has to leave the terminal to work with data and services. It delivers three self-contained toolsets: an embedded database explorer for browsing schemas and running SQL against live databases, a native HTTP/REST client for exercising Spring and Ktor controllers, and protobuf/gRPC navigation and RPC execution for microservice development. Each toolset must feel native, stay fully asynchronous, and rely only on standard plugins and language servers.

## Stories

- Story 3.1: Embedded Database Explorer
- Story 3.2: HTTP Client & REST API Explorer
- Story 3.4: gRPC & Protobufs Integration

## Requirements & Constraints

Database explorer: browse schemas and execute SQL with results shown in a grid, built on `vim-dadbod` and `vim-dadbod-ui`; auto-discover datasource URLs and credentials from Spring Boot `application.properties` / `application.yml` / `application.yaml`, resolving environment-variable and `.env` interpolation; SQL syntax highlighting and completion driven by the live database schema; must support executing queries against a running local Postgres from a `.sql` buffer; Flyway migration script validation.

HTTP client: execute requests from `.http` files (via `kulala.nvim`); extract OpenAPI specifications and generate request templates from them; render formatted JSON/XML responses in a split buffer; pipe responses through `jq` filters into editor splits.

gRPC / protobuf: syntax highlighting, formatting, and LSP-backed navigation for `.proto` files using `buf` / `protols`; gRPC server reflection and schema inspection to discover services and methods; interactive RPC execution with structured JSON request-payload generation. No GUI backend — reflection and execution are driven from Lua plus the standard LSP.

Cross-cutting non-functional constraints: the UI must never block — all query execution, requests, and RPC calls run asynchronously with output delivered to native UI (quickfix, notifications, splits). Heavy external processes run headlessly, not in `:terminal`. Standard network access is assumed for fetching tool and LSP binaries. Scope is Java and Kotlin only; Scala/`sbt` and air-gapped installs are out of scope.

## Technical Decisions

- Strict native intelligence (AD-07): all new logic is pure Lua with Tree-sitter, or delegated to standard LSPs. No external backend, engine, bridge, or helper Bash/Python parsing scripts. The gRPC story specifically uses `buf` / `protols` and Lua — no external GUI or daemon.
- Event-driven UI (AD-01): executors and background logic emit Neovim autocommands; UI components listen and schedule renders with `vim.schedule`. Background tasks must not call render functions directly.
- Headless external tooling (AD-03): shell out via `vim.system`, capture stdout/stderr, and route results to Quickfix and notifications rather than raw terminal splits.
- Stateless project context (AD-04): no cached workspace topology. Datasource/credential discovery and OpenAPI/endpoint lookups are read from the filesystem on the fly (parse the config files each time); do not rely on file-watchers or in-memory caches.
- Decentralized plugin orchestration (AD-02): configure each toolset in its own file under `lua/tetravim/plugins/`, lazy-loaded by filetype/command (`sql`, `http`, `proto`). No shared LSP monolith.
- Direct UI coupling (AD-06): call `snacks.nvim` and `telescope.nvim` primitives directly; do not build an abstraction/facade layer.
- Module conventions: feature utilities live under `lua/tetravim/util/` (`db.lua`, `http.lua`, `openapi.lua`), plugin specs under `lua/tetravim/plugins/`.
- Resource discipline: assume a 32GB+ RAM machine but keep memory bounded; accept slightly slower first-run indexing in exchange for stability.

## UX & Interaction Patterns

- Keymap hierarchy is mnemonic and `<leader>`-based. Database operations sit under `<leader>db*` (Dadbod UI). HTTP/REST sits under `<leader>H`: `<leader>Hr` run request under cursor, `<leader>Ho` generate a `.http` file from an OpenAPI spec, `<leader>Hj` apply a `jq` filter to the JSON response.
- Lists (schemas, tables, endpoints, gRPC services/methods) are presented through a fuzzy-searchable Telescope/Snacks picker, centered floating, preview pane on by default.
- Use persistent bottom/side splits for referenceable output — result grids, response bodies. Reserve floating windows for ephemeral, task-focused steps only; never put long-running output in a float.
- Async feedback: a statusline spinner or a Snacks notification during execution, a toast on completion, and diagnostics/errors populated into the quickfix list or inline.
- Voice: concise and actionable, standard JVM/DevOps terminology, quiet success and explicit visible errors.
- Response and result buffers retain syntax highlighting and follow the canonical "Tetris" palette (strings/warnings orange, errors red, constants/enum members in the `#5B8CFF` blue tint for contrast).
- Keyboard-first: every action must be reachable without a mouse.

## Cross-Story Dependencies

- Stories 3.1 and 3.2 depend on Spring Boot application detection and config-file parsing (delivered by the native Spring discovery work in Epic 2) for datasource/credential auto-discovery and endpoint/OpenAPI awareness.
- Story 3.1's config discovery should reuse the same on-the-fly `application.yml` / `.properties` parsing pattern used for Spring detection rather than introducing a parallel mechanism.
- Stories 3.2 and 3.4 share two patterns worth factoring together: structured JSON request-payload generation, and rendering formatted responses into split buffers.
- All three stories depend on external binaries/LSPs (`buf`, `protols`, dadbod DB adapters, `kulala.nvim`) being provisioned through the headless install / Mason path owned by Epic 5.
