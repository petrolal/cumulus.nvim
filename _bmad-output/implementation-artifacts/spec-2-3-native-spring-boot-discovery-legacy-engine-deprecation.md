---
title: 'Native Spring Boot Discovery & Legacy Engine Deprecation'
type: 'refactor'
created: '2026-09-02'
status: 'in-progress'
review_loop_iteration: 1
context: []
baseline_commit: '096ecc2a6682b3c29bbcee642f52ba9c80775503'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Spring Boot discovery — the bean picker (`<leader>jsb`), REST endpoint picker (`<leader>jse`), and Spring Boot app / DAP-config detection — is entirely delegated to the deprecated Scala `tetravim-engine` binary via `engine.select_bean`, `engine.select_endpoint`, `engine.parse_spring_beans`, `engine.extract_endpoints`, `engine.detect_springboot_app`, and `engine.generate_dap_config`. When the binary is absent these paths hard-`error()` through `assert_available("jvm-build")`, and the epic mandates retiring the engine for this feature area.

**Approach:** Add a pure-Lua + Tree-sitter Spring discovery module (`spring.lua`) that detects the Spring Boot application root and main class, extracts beans with their injected dependencies, and extracts REST endpoints by parsing Java/Kotlin controller ASTs. Surface beans and endpoints through first-class Telescope pickers with a preview pane (`spring-picker.lua`), rebuild the Spring Boot DAP launch/attach config natively, and remove the six engine Spring functions along with every Spring caller of `engine.lua`.

## Boundaries & Constraints

**Always:**
- Discovery, AST parsing, and project-root / build-tool detection are pure Lua + `vim.treesitter`, or delegated to an already-attached LSP. No new work in the Scala engine; no Bash/Python/external parser helpers.
- All filesystem scanning is asynchronous (`vim.system` + `vim.schedule`); the editor UI never blocks. Reuse the `rg`→`grep` fallback pattern and the `SCAN_TIMEOUT_MS` budget from `refactor-treesitter.lua`.
- Degrade gracefully: missing `rg`/`grep`, missing Tree-sitter parser, no project root, or zero results → a single `vim.notify` at WARN/INFO and a clean `return`. Never `error()`.
- Java and Kotlin only.
- `springboot-debug.setup_springboot_dap` still runs inside jdtls `on_attach` for every Java buffer — keep it cheap and silent-failing, and de-duplicate before appending to `dap.configurations.java`.
- Picker jump idiom stays `vim.cmd("edit " .. vim.fn.fnameescape(file))` + `vim.api.nvim_win_set_cursor(0, { line, col })`; the endpoint jump lands the cursor on the mapping-annotation line.

**Ask First:**
- Adding `spring-boot.nvim` or any JDTLS Spring extension as a Lazy dependency — default is no new plugin; only raise this if native Tree-sitter cannot produce bean/endpoint data of acceptable quality.
- Removing or renaming any non-Spring `engine.lua` function, or changing `core/devops.lua` root discovery (it deliberately has no local fallback).
- Binding bare `<leader>js` to an action — it is the established which-key group prefix ("spring & frameworks"); this spec uses `<leader>jsd`.

**Never:**
- Scala / `sbt` support; reintroducing `beans.lua` or `endpoints.lua` (validate.sh guards their absence).
- Persistent caches of workspace topology, or Neovim file-watchers — query on demand.
- Imperative render calls from background callbacks.
- A `tetravim.ui` facade — call `telescope` directly.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Endpoint picker, Spring MVC | Java project, `@RestController` + `@GetMapping("/x")` | Telescope picker row `[GET] /prefix/x  Class.handler`; `<CR>` opens file, cursor on the `@GetMapping` line | N/A |
| Endpoint picker, JAX-RS | class with `@Path("/r")` + `@GET` | row `[GET] /r  Class.handler` | N/A |
| Bean picker | `@Service class Foo` constructor-injecting `Bar`, `Baz` | row `foo (Foo) -> [bar, baz]`; preview pane shows `foo` with direct deps `bar`,`baz` and its dependents; `<CR>` jumps to class declaration | N/A |
| Detect app | root has `pom.xml` + `@SpringBootApplication` class | `<leader>jsd` notifies `Spring Boot: <name> (maven) — com.x.App` | N/A |
| DAP launch | `on_attach` fires for a Java buffer in a Spring Boot project | one `{ type=java, request=launch, mainClass=… }` added to `dap.configurations.java`; a second buffer adds no duplicate | silent `return` if no main class |
| No project root | triggered outside any Maven/Gradle project | single INFO notify "No Maven/Gradle project root found"; no picker | graceful `return` |
| `rg` and `grep` absent | neither on `$PATH` | WARN notify "ripgrep or grep required for Spring discovery" | graceful `return` |
| No parser | `nvim-treesitter` java parser not installed | WARN notify "Tree-sitter java parser not available" | graceful `return` |
| Zero endpoints / beans | project has none | INFO notify "No Spring Boot / JAX-RS endpoints found in project" (current wording preserved) | graceful `return` |

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/engine.lua` -- REMOVE `extract_endpoints` (L603-609), `parse_spring_beans` (L628-634), `detect_springboot_app` (L746-748), `generate_dap_config` (L1058-1062), `select_bean` (L1069-1090), `select_endpoint` (L1093-1113). All are leaf functions used only by Spring callers. Leave every other function, plus `is_available`/`assert_available` (L94-137), untouched.
- `lua/tetravim/util/refactor-treesitter.lua` -- REUSE (do not modify): `_ts_root_for(content, lang)` (L289), `is_inside_comment_or_string(content, lang, row, col)` (L345), `LANG_BY_EXT` (`java`/`kt`/`kts`), `raw_hits_async` / `_grep_fallback` (L382/L452) as the async candidate-scan template, `SCAN_TIMEOUT_MS = 15000`.
- `lua/tetravim/util/spring.lua` -- NEW. Native discovery. **No synchronous `vim.system(...):wait()` anywhere** — every project-wide scan is async (`vim.system` with a `vim.schedule`-wrapped completion handler). Public API:
  - `detect_root(start_path?) -> { root, build_tool, project_name } | nil` -- **synchronous and cheap**: only the upward `vim.fs.find` marker walk plus a bounded read of `pom.xml` / `settings.gradle(.kts)`. No project-wide `rg`/`grep`. Safe to call on the main loop (pickers, health, `on_attach`).
  - `find_main_class(root, cb)` -- **async**: the `@SpringBootApplication` `rg`→`grep` prefilter + Tree-sitter confirmation + FQN build; `cb(fqn | nil)`.
  - `detect_app(start_path?, cb)` -- **async**: `cb({ root, build_tool, project_name, main_class } | nil)`. Composes `detect_root` (sync) + `find_main_class` (async).
  - `build_dap_config(root?, cb)` -- **async**: `cb({ launch, attach } | nil)`; `cb(nil)` when there is no root or no main class.
  - `find_endpoints(root, cb)` async → `cb({ { file, line, http_method, path, class_name, handler_name } })`.
  - `find_beans(root, cb)` async → `cb({ { file, line, bean_name, class_name, injected_deps } })`.
  Tree-sitter query text held as `local` string constants, parsed with `vim.treesitter.query.parse(lang, ...)`.
- `lua/tetravim/util/spring-picker.lua` -- NEW. First real `telescope.pickers.new` in the repo. Public API: `pick_endpoint()`, `pick_bean()`, `detect_app()`. Bean picker sets a `require("telescope.previewers").new_buffer_previewer` that renders the dependency graph; endpoint picker uses the default file previewer. `attach_mappings` binds `<CR>` → edit + set cursor (`pcall` the `:edit`; bail if the row has no `file`). Depends on `spring.lua`. Graceful WARN/INFO notify on empty/unavailable, mirroring current `engine.select_*` wording. Resolve the scan root via `spring.detect_root()` (sync, never blocks); **if it returns `nil`, emit the frozen INFO `"No Maven/Gradle project root found"` and return without scanning** (matrix row "No project root" applies to the pickers, not only `detect_app`). Check Telescope availability up front, before launching the scan; guard every `require("telescope.*")` in one `pcall`.
- `lua/tetravim/util/springboot-debug.lua` -- REWRITE both functions to consume the **async** `require("tetravim.util.spring").build_dap_config(root, cb)` instead of `engine.generate_dap_config`. `setup_springboot_dap` (runs in `on_attach` for every Java buffer) fires the async call and does the register + de-dup inside the `vim.schedule`d callback; it never blocks and never notifies on failure (silent `return` / `cb(nil)` → do nothing). `launch_debug` (`<leader>jrd`) registers in the callback then calls `dap.continue()`; it `pcall`s `require("tetravim.util.spring")` and `require("dap")`, and on any failure emits a single WARN/INFO `vim.notify` and returns — **never `error()`, never `vim.log.levels.ERROR`**. Keep the `.launch` required / `.attach` optional contract. De-dup guard matches on config `name`, and only de-dups when `name` is non-nil (two nil-named configs both insert).
- `lua/tetravim/util/jvm.lua` -- L424-426 `<leader>jse` → `require("tetravim.util.spring-picker").pick_endpoint()`; L428-430 `<leader>jsb` → `require("tetravim.util.spring-picker").pick_bean()`; ADD `<leader>jsd` → `require("tetravim.util.spring-picker").detect_app()`, desc `"Spring: Detect Boot App"`. `<leader>jrd` (L419) and `<leader>jsm` (L432, Flyway) unchanged.
- `ftplugin/java.lua` -- L62-65 unchanged; verify `root_dir` is still passed into `setup_springboot_dap`.
- `scripts/validate.sh` -- L69/71/72 currently assert `engine.generate_dap_config` / `engine.select_bean` / `engine.select_endpoint` are functions. REPLACE with `s = require('tetravim.util.spring')`, `p = require('tetravim.util.spring-picker')`, and assert `type(s.build_dap_config) == 'function'`, `type(s.find_beans) == 'function'`, `type(s.find_endpoints) == 'function'`, `type(p.pick_bean) == 'function'`, `type(p.pick_endpoint) == 'function'`. Keep L97/98 (`beans` / `endpoints` purge guards) unchanged.
- `lua/tetravim/health.lua` -- ADD a `vim.health.start("TetraVim Spring Boot Discovery (Story 2.3)")` section: OK when the `nvim-treesitter` java parser is present and (`rg` or `grep`) is on `$PATH`; WARN otherwise. For the project-root line, call `spring.detect_root()` (sync) only — **never** trigger the async / main-class scan from `:checkhealth`. Do not touch the SPEC-2.1 section (L116-129).
- `lua/tetravim/tests/spring_spec.lua` -- NEW. Static shape + fixture-string parse assertions. Model on `lua/tetravim/tests/refactor_spec.lua` (mocked `_G.require("jdtls")` + `vim.fn.maparg` callback invocation).
- `scripts/validate-2-3.sh` -- NEW. Runtime smoke. Model on `scripts/validate-refactor.sh` (mocked-LSP seam, `cquit 1` on failure).

## Tasks & Acceptance

**Execution:**
- [ ] `lua/tetravim/util/spring.lua` -- create the native discovery module. `detect_root` (sync): upward `vim.fs.find({ "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts" }, { upward = true, path = start_path or cwd })`; `build_tool` from which file matched; `project_name` from pom `<artifactId>`, else `settings.gradle` `rootProject.name`, else root basename. `find_main_class(root, cb)` (async): `rg`/`grep`-prefilter for `@SpringBootApplication` (globs `*.java` `*.kt` `*.kts`) then confirm with Tree-sitter and build the FQN from the file's `package` + class name; non-anchored `package%s+([%w_.]+)` fallback for the FQN. `detect_app(start_path, cb)` / `build_dap_config(root, cb)` (async): compose the `java` launch + attach tables from `detect_root` + `find_main_class`; `cb(nil)` when no root or no main class. `find_endpoints` / `find_beans` (async): word-bounded `rg`/`grep` candidate prefilter on annotation tokens → `_ts_root_for` per matched file → capture with the local query constants (`class_declaration` **and** `interface_declaration` for stereotypes) → assemble rows, skipping hits inside comments/strings; require the `kotlin` parser too when `.kt`/`.kts` candidates are present; a scan that fails/times out → frozen WARN + `cb(nil)`, never an empty result. **No `vim.system(...):wait()` anywhere.** Rationale: one native, non-blocking source of Spring facts replacing four engine subcommands.
- [ ] `lua/tetravim/util/spring-picker.lua` -- create Telescope pickers `pick_endpoint`, `pick_bean`, `detect_app`. Bean previewer renders an indented graph (bean → its direct injected deps, then its direct dependents) computed from the full bean list. `attach_mappings` `<CR>` = `pcall`ed `edit` + `nvim_win_set_cursor` (endpoint → annotation line); bail if the row has no `file`. Resolve root via `spring.detect_root()`; `nil` → frozen INFO + return, no scan. Telescope availability checked up front; all `require("telescope.*")` in one `pcall`. Graceful WARN/INFO notify on empty / unavailable. Rationale: replaces the `engine.select_bean` / `select_endpoint` `vim.ui.select` flows with the epic's preview-pane UX.
- [ ] `lua/tetravim/util/springboot-debug.lua` -- consume the async `spring.build_dap_config(root, cb)`; register + name-based de-dup (only when `name` is non-nil) inside the `vim.schedule`d callback before `table.insert(dap.configurations.java, …)`. `setup_springboot_dap` stays silent-failing; `launch_debug` `pcall`s its `require`s and degrades with a single WARN/INFO — never `error()` / `ERROR`. Rationale: removes the last engine call in the Spring debug path, keeps `on_attach` non-blocking, prevents duplicate configs across buffers.
- [ ] `lua/tetravim/util/jvm.lua` -- repoint `<leader>jse` / `<leader>jsb` to `spring-picker`, add `<leader>jsd`. Rationale: wire the native pickers to the existing keymaps.
- [ ] `lua/tetravim/util/engine.lua` -- delete the six Spring functions (line anchors in Code Map). Rationale: retire the Scala engine for this feature area.
- [ ] `scripts/validate.sh` -- update the L69/71/72 assertions to target `spring` / `spring-picker`. Rationale: keep the smoke suite green after the engine surface shrinks.
- [ ] `lua/tetravim/health.lua` -- add the Spring Boot Discovery health section (project-root line uses `spring.detect_root()` only). Rationale: user-visible diagnostic for the new native path.
- [ ] `lua/tetravim/tests/spring_spec.lua` -- static shape checks + parse fixtures for one Spring MVC controller, one JAX-RS resource, one `@Service` with constructor injection, one `@Repository` interface; assert row shapes and `injected_deps`. Keep the two degradation assertions for I/O-matrix rows 7 & 8 (parser-unavailable test stubs the real probe, per Design Notes). Add an executable engine-purge assertion (`require('tetravim.util.engine').<fn>` is `nil` for all six) that fails the run — plenary-busted, not the `validate.sh` `+lua` wrapper. Covers the I/O-matrix parsing rows.
- [ ] `scripts/validate-2-3.sh` -- runtime smoke: mocked jdtls `on_attach` adds exactly one Spring Boot launch config (idempotent on a second buffer); `<leader>jse` / `<leader>jsb` / `<leader>jsd` maparg callbacks resolve and dispatch; **also** stub `dap` and invoke the `<leader>jrd` (`launch_debug`) callback, asserting the launch config lands in `dap.configurations.java` and `dap.continue` is called. `cquit 1` on failure. Covers the DAP + keymap I/O rows.

**Acceptance Criteria:**
- Given a Java Spring Boot project with the `tetravim-engine` binary absent, when I press `<leader>jse`, then a Telescope picker lists every `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`/`@RequestMapping` and JAX-RS `@GET`/`@POST`/`@PUT`/`@DELETE` endpoint in the workspace and no Lua error is raised.
- Given the endpoint picker is open, when I select a row, then the controller file opens with the cursor on that mapping annotation.
- Given `@Service`/`@Component`/`@Repository`/`@RestController` classes, when I press `<leader>jsb`, then the picker lists each bean as `name (Class) -> [deps]` and the preview pane shows that bean's direct dependencies and direct dependents as an indented tree.
- Given a Spring Boot project, when jdtls attaches to a Java buffer, then exactly one native `java` launch configuration is present in `dap.configurations.java`, and attaching a second buffer adds no duplicate.
- Given no Maven/Gradle root, or no `rg`/`grep`, or no Tree-sitter parser, when any Spring discovery command runs, then a single WARN/INFO notification is shown and no Lua error is raised.
- Given the codebase after this change, when `grep -n "tetravim.util.engine" lua/tetravim/util/jvm.lua lua/tetravim/util/springboot-debug.lua` runs, then it shows no Spring-discovery references, and `require('tetravim.util.engine').select_bean` is `nil`.
- Given `bash scripts/validate.sh`, when it runs, then step 6 passes with the updated `spring` / `spring-picker` assertions and the `beans.lua` / `endpoints.lua` purge guards still hold.
- Given `:PlenaryBustedDirectory lua/tetravim/tests/` and `bash scripts/validate-2-3.sh`, when they run, then both pass.

## Spec Change Log

- 2026-09-03 — Implementation verification: added two `spring_spec.lua` assertions covering I/O-matrix rows 7 (`rg`/`grep` both absent → single WARN + `cb(nil)`) and 8 (Tree-sitter java parser unavailable → WARN + `cb(nil)`), which the initial test set left uncovered. No production-code change. Spec intent unchanged.
- 2026-09-03 — **bad_spec loopback (review iteration 1).**
  - **Triggering finding:** `spring.lua`'s `_detect` / `find_main_class` ran a *synchronous* `vim.system(cmd):wait()` project-wide `@SpringBootApplication` scan (`run_sync`, 15 s timeout). That path is reached from `build_dap_config`, which `springboot-debug.setup_springboot_dap` calls inside jdtls `on_attach` for **every** Java buffer — directly violating the frozen "Always" constraints "All filesystem scanning is asynchronous … the editor UI never blocks" and "keep it cheap and silent-failing". Also hangs `:checkhealth`. Reported independently by all three review layers.
  - **Root cause (non-frozen):** the `## Code Map` defined `build_dap_config(root?) -> {…}|nil` and `detect_app(start_path?) -> {…}|nil` as synchronous return-value functions, forcing a blocking scan.
  - **Amended:** `## Code Map` `spring.lua` bullet split into a cheap **synchronous `detect_root(start_path?)`** (upward marker walk + bounded `pom.xml`/`settings.gradle` read only — no project scan) plus **async** `find_main_class(root, cb)` / `detect_app(start_path?, cb)` / `build_dap_config(root?, cb)` / `find_endpoints(root, cb)` / `find_beans(root, cb)`; "**No `vim.system(...):wait()` anywhere**". `spring-picker.lua`, `springboot-debug.lua`, `health.lua` bullets rewritten for the callback contract (picker/health call only `detect_root()`; `springboot-debug` registers + de-dups inside the `vim.schedule`d callback; `launch_debug` never `error()`s). `## Design Notes` "Async contract" bullet expanded to all five async functions + the no-`:wait()` rule; five new bullets added (Scan-tool / parser probes; Kotlin coverage incl. `.kts`; Prefilter precision / word boundaries; Scan failure ≠ empty; Stereotype targets incl. `interface_declaration`) folding in the surviving `patch` findings. `## Tasks & Acceptance` execution checkboxes reset to `[ ]` and two verification tasks added (executable engine-purge assertion via plenary-busted; `launch_debug` invocation smoke in `validate-2-3.sh`).
  - **Known-bad state avoided:** up to a 15 s editor freeze on every Java buffer attach in a large repo (and an indefinite `:checkhealth` hang) caused by a blocking project-wide scan in a hot `on_attach` path.
  - **KEEP (must survive re-derivation):** Tree-sitter endpoint parsing (`_endpoints_in_content`, `endpoint_from_method`, `join_paths`/`norm_segment`, `@RequestMapping(method=RequestMethod.X)` verb extraction, class-level `@RequestMapping`/`@Path` base-path prefixing, full JAX-RS `@GET/@POST/@PUT/@DELETE/@Path`, comment/string skipping via `refactor_ts._is_comment_or_string_node`); bean parsing (`_beans_in_content`, `injected_deps` for sole/`@Autowired` constructor + `@Autowired` field/setter + Kotlin `primary_constructor`, `decapitalize` preserving a leading all-caps run); `spring-picker.lua` structure (`pick_endpoint` sorted `[GET] /path  Class.handler` rows + grep previewer; `pick_bean` `new_buffer_previewer` "Bean Dependency Graph" rendering direct deps + direct dependents indented; `detect_app` notify `Spring Boot: %s (%s) — %s`; `<CR>` → `:edit` + `nvim_win_set_cursor` jump); `_candidate_files_async` rg→grep fallback style (`res.code == 0 or res.code == 1` success, `vim.schedule`d callback) — extend this same pattern to `find_main_class`; `dedup_insert` name-based dedup in `springboot-debug.lua`; the DAP config table shape from Design Notes; `jvm.lua` keymap repoints (`<leader>jse`/`jsb`/`jsd`) leaving `<leader>jrd` / `<leader>jsm` untouched; `engine.lua` deletion of exactly the six Spring functions leaving `is_available`/`assert_available` and all non-Spring wrappers intact; `health.lua` "TetraVim Spring Boot Discovery (Story 2.3)" section; `scripts/validate.sh` step-6 `spring`/`spring-picker` assertion updates; `spring_spec.lua` fixtures + the row-7/row-8 degradation tests (row-8 must stub the *real* parser probe); coverage of I/O-matrix rows 7 & 8 (per the 2026-09-03 verification entry above).

## Design Notes

- **Keymap deviation:** `<leader>js` is the established which-key group prefix ("spring & frameworks"), so Spring Boot app detection binds to `<leader>jsd`, not bare `<leader>js`. Confirm with the human at the checkpoint.
- **No plugin dependency:** bean/endpoint data comes from Tree-sitter queries run via `vim.treesitter.query.parse(lang, <const>)` against files pre-filtered by `rg`/`grep`. `spring-boot.nvim` is intentionally not added (stability / Neovim-native steer); revisit only if query quality proves inadequate.
- **Endpoint parsing:** match `annotation` / `marker_annotation` nodes whose name is one of `GetMapping`/`PostMapping`/`PutMapping`/`DeleteMapping`/`PatchMapping`/`RequestMapping` (Spring) or `GET`/`POST`/`PUT`/`DELETE`/`Path` (JAX-RS). Path = first string-literal argument or the `value=`/`path=` element; prepend the enclosing class's `@RequestMapping` value. HTTP method = the annotation identifier, or `method = RequestMethod.X` for `@RequestMapping`. Kotlin uses the same annotation identifiers with `(annotation (user_type))` / call-expression argument shape.
- **Bean `injected_deps`:** constructor-injection parameters (the sole constructor, or the `@Autowired` one), plus `@Autowired` fields and setters. Display name = decapitalized parameter/field type.
- **DAP config shape:** `launch = { type = "java", request = "launch", name = "Spring Boot: <project_name>", mainClass = <fqn>, projectName = <project_name>, console = "integratedTerminal" }`; `attach = { type = "java", request = "attach", name = "Spring Boot: <project_name> (attach)", hostName = "127.0.0.1", port = 5005 }`. Omit `.configurations` so `setup_springboot_dap` takes the launch+attach branch.
- **Async contract:** `find_endpoints(root, cb)` / `find_beans(root, cb)` / `find_main_class(root, cb)` / `detect_app(start_path, cb)` / `build_dap_config(root, cb)` all run their `rg`/`grep` prefilter via `vim.system` with a `vim.schedule`-wrapped completion handler; the picker (or DAP register, or notification) happens from inside that callback. Honor `SCAN_TIMEOUT_MS`. **The module must not contain a `:wait()` on `vim.system`.** Only `detect_root()` — the upward marker walk + `pom.xml`/`settings.gradle` read — is synchronous, because it does no project-wide scan.
- **Scan-tool / parser probes:** do not rely on `pcall(vim.treesitter.language.add, lang)` raising — on Neovim ≥ 0.10 it returns `nil, err` instead of erroring, so that `pcall` is always truthy. Probe a parser the way `refactor-treesitter.lua` does (attempt to build a parser / check the documented return value) so the frozen "no parser → WARN" path is actually reachable. The `spring_spec.lua` parser-unavailable test must stub whatever the real probe calls, not a function that only `error()`s.
- **Kotlin coverage:** the `rg`/`grep` prefilter globs `*.java`, `*.kt`, **and `*.kts`** (matching `LANG_BY_EXT`), in both `find_main_class` and the endpoint/bean candidate scan. When the candidate set contains any `.kt`/`.kts` file, also require the Tree-sitter `kotlin` parser — if it is missing, take the same WARN + `cb(nil)` degradation as the missing-`java`-parser path.
- **Prefilter precision:** the `rg`/`grep` annotation-token pattern is word-bounded so `@Path` does not match `@PathVariable` and `@GET` does not match a `@GETTER`. Tree-sitter still authoritatively filters the results; the boundary only keeps the candidate scan inside the `SCAN_TIMEOUT_MS` budget.
- **Scan failure ≠ empty:** if both `rg` and `grep` are unavailable, or the spawned scan exits with a code other than 0/1 (match / no-match) or times out, emit the frozen WARN and `cb(nil)`. Never report a failed or timed-out scan to the user as "no endpoints / beans found".
- **Stereotype targets:** `@Service` / `@Component` / `@Repository` / `@RestController` are matched on `class_declaration` **and `interface_declaration`** (Spring Data `@Repository` is almost always an interface). `injected_deps` for an interface bean is legitimately empty. Explicit names (`@Service("x")`) are out of scope for this story (see deferred-work).

## Verification

**Commands:**
- `stylua lua/ ftplugin/ init.lua` -- expected: exit 0, no diff (2-space, 120 col)
- `bash scripts/validate.sh` -- expected: all 7 steps pass; step 6 passes with the updated Spring assertions and the `beans`/`endpoints` purge guards
- `nvim --headless -c "PlenaryBustedDirectory lua/tetravim/tests/" -c qa` -- expected: `spring_spec.lua` green, no regressions in existing specs
- `bash scripts/validate-2-3.sh` -- expected: exit 0 (script uses `cquit 1` on failure)
- `grep -rn "generate_dap_config\|select_bean\|select_endpoint\|parse_spring_beans\|extract_endpoints\|detect_springboot_app" lua/` -- expected: zero matches
