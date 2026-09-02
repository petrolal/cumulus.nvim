# Epic 3 Context: Database & Cloud Services Integration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Eliminate the need for external tools like DataGrip, DBeaver, Postman, or AWS Consoles by bringing database exploration and REST API testing capabilities directly into the Neovim ecosystem. This is the "ecosystem parity" epic: it lets a backend/API developer browse schemas, run SQL, and exercise HTTP endpoints without ever leaving the editor, reinforcing the product's core promise of a keyboard-driven, single-tool replacement for IntelliJ IDEA and its companion tools.

## Stories

- Story 3.1: Embedded Database Explorer
- Story 3.2: HTTP Client & REST API Explorer

## Requirements & Constraints

- Users must be able to execute SQL queries against a live local database (e.g., Postgres) directly from a `.sql` buffer and see results without switching tools.
- Database exploration should include schema browsing, and query results should render in a grid-like view.
- Database credentials should be auto-discoverable from Spring config files (`application.yml` / `application.properties`) rather than requiring manual entry.
- SQL editing should get syntax highlighting and auto-completion informed by the live database schema.
- Flyway migration script validation is part of the database/DevOps tooling surface for this epic.
- `.http` files must be directly executable, with formatted JSON/XML responses viewable in a split buffer.
- REST API tooling should support extracting OpenAPI specs to auto-generate request templates, and should support `jq` filtering on JSON responses.
- All operations in this epic are user-initiated, synchronous-feeling actions on demand (query execution, request execution) — background/async execution must still never block the editor UI (applies project-wide, not epic-specific).
- No new custom backend/engine surface area: functionality must be delivered via native Lua, Tree-sitter, LSP, or well-established Neovim plugins — not by extending the legacy Scala `tetravim-engine`.

## Technical Decisions

- **Buffer-first, not sidebar-first:** Follow the project's stateless, buffer-based interaction model (as used for file management via `oil.nvim`) — database and HTTP interactions should feel like editing/executing standard Neovim buffers, not driving a bespoke stateful GUI panel.
- **Event-driven UI updates:** Any background execution (query run, HTTP request) must emit Neovim autocommands and let UI components render asynchronously via `vim.schedule` — never update UI directly from a background callback.
- **Headless external execution:** If either feature shells out to external processes, use `vim.system` (not `:terminal`), with output piped into native Neovim UI (splits, quickfix, notifications).
- **Stateless project context:** Don't cache database connection info or discovered credentials in memory across sessions; discover them on demand by parsing config files (e.g., `application.yml`/`application.properties`) or querying live sources.
- **Named plugin integrations to use:**
  - Database Explorer: `vim-dadbod` and `vim-dadbod-ui`.
  - Decentralized plugin loading: configs for these features must be isolated by filetype/command in `lua/tetravim/plugins/`, not merged into a global config file.
- **Result/response display:** Long-lived or referenceable output (query result grids, HTTP responses) belongs in persistent splits, not floating windows — floating windows are reserved for ephemeral, task-focused interactions (pickers, momentary confirmations).

## UX & Interaction Patterns

- Query results and HTTP responses should appear in a bottom-drawer-style split (consistent with how build logs and test output are shown elsewhere in the IDE) — not a floating window, since this is referenceable, potentially long output.
- Any list-style interaction (e.g., selecting a saved query, choosing an endpoint from an extracted OpenAPI spec) should use the standard fuzzy-searchable Telescope/Snacks picker pattern already used for other pickers in the IDE, with preview enabled.
- Keymaps for this epic should slot into the existing mnemonic `<leader>`-based hierarchy (e.g., `<leader>o` is already reserved for DevOps/Operations workflows) rather than introducing an inconsistent scheme.
- Success/failure of a query or HTTP request should follow the established async pattern: a spinner/notification while in flight, then a toast notification on completion, with detailed errors surfaced explicitly (not silently swallowed).
- Rounded borders and the active dark colorscheme (Catppuccin/Tokyonight) apply to any floating pickers this epic introduces, consistent with the rest of the IDE.

## Cross-Story Dependencies

- Story 3.1's credential auto-discovery depends on parsing the same Spring Boot project config files (`application.yml`/`application.properties`) that Epic 1's Spring integration work (endpoint/bean discovery) already parses — reuse that discovery logic where possible rather than re-implementing it.
- Story 3.2's OpenAPI-based request template generation is conceptually related to Spring REST endpoint discovery (Epic 1) — both surface a project's API surface — but Story 3.2 sources templates from OpenAPI specs rather than Tree-sitter AST parsing of controllers.
- Both stories are independent of each other and can be built in parallel; neither blocks the other.
