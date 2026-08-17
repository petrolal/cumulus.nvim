---
title: 'Story 8.3: Ansible Playbook Syntax Checking, Linting, Execution & Inventory Suite (<leader>oy)'
type: 'feature'
created: '2026-08-17'
status: 'done'
baseline_commit: 'f6e172d3acadeffa8c22b18b214c7dc47cd42420'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-8-2-cloudformation-sam-suite.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Infrastructure and DevOps engineers working with Ansible automation playbooks and roles in Neovim lack a unified, non-blocking toolchain under `<leader>oy` for syntax verification, linting, dry-runs, interactive playbook runs, inventory graph inspection, module doc lookup, and vault management. Furthermore, playbook structure extraction, inventory hierarchy parsing, and offline syntax validation should be handled in the native Scala 3 engine (`cumulus-engine`), keeping Lua files minimal and thin.

**Approach:** Implement native Scala 3 Ansible parsing, play/task extraction, offline validation, and inventory graph parsing in `cumulus.devops.AnsibleParser`, exposed via CLI subcommands in `Main.scala`. Wire these through `engine.lua` and minimal Lua runners in `devops.lua` under `<leader>oy` to non-blocking interactive terminals (`Snacks.terminal`), diagnostic linters (`nvim-lint`), schemas (`ansiblels`), and health checks (`:checkhealth cumulus`).

## Boundaries & Constraints

**Always:**
- Offload playbook AST inspection, role/task extraction, inventory tree parsing, and offline YAML syntax validation to the Scala 3 engine (`cumulus-engine`).
- Keep Lua code minimal: Lua only handles user keymaps, calling the Scala engine via `engine.lua`, and launching terminal processes (`Snacks.terminal`).
- Use non-blocking interactive terminals (`Snacks.terminal(...)` or terminal buffers) for interactive or long-running commands (`ansible-playbook`, `ansible-playbook --check`), avoiding editor lockup.
- Scope keymaps to relevant buffer filetypes (`yaml.ansible`, `ansible`, or `yaml` files matching Ansible playbook indicators) using `lang_keymaps.register({...})`.
- Gracefully handle missing CLI binaries (`ansible-playbook`, `ansible-lint`, `ansible-inventory`, `ansible-doc`, `ansible-vault`) by checking `vim.fn.executable(...)` and notifying the user with actionable installation instructions via `vim.notify`.

**Never:**
- Never execute blocking Vim commands (e.g. `:!ansible-playbook`) that freeze the Neovim UI thread.
- Never leak `<leader>oy` keybindings into unrelated buffers (e.g. Java, Python, Rust, or general non-Ansible YAML).

## I/O & Edge-Case Matrix

| Scenario | Trigger / Input | Expected Output / Behavior | Error Handling |
|----------|----------------|---------------------------|----------------|
| Inspect Ansible Playbook | `cumulus-engine --ansible-inspect <file>` | Returns JSON `CumulusResponse[AnsiblePlaybookInfo]` with plays, hosts, tasks, and roles | Returns error envelope if file missing or malformed |
| Offline Ansible Validation | `cumulus-engine --ansible-validate <file>` | Returns JSON `CumulusResponse[Seq[AnsibleValidationIssue]]` detecting unquoted templates, bad keys, syntax errors | Returns structured error envelope |
| Parse Inventory Graph | `cumulus-engine --ansible-inventory-parse <json_or_graph>` | Returns JSON `CumulusResponse[Seq[AnsibleInventoryGroup]]` with structured host/group hierarchies | Returns error envelope on unparseable format |
| Ansible Syntax Check | `<leader>oys` in `playbook.yaml` | Executes `ansible-playbook --syntax-check <file>` in non-blocking terminal | Warns if `ansible-playbook` missing with install guidance |
| Ansible Lint | `<leader>oyl` in playbook buffer | Executes `ansible-lint` on the active playbook file | Warns if `ansible-lint` is missing with `:MasonInstall ansible-lint` hint |
| Ansible Dry-Run Check | `<leader>oyc` in `playbook.yaml` | Executes `ansible-playbook --check <file>` in interactive terminal | Warns if `ansible-playbook` missing |
| Ansible Run Playbook | `<leader>oyr` in `playbook.yaml` | Executes `ansible-playbook <file>` in interactive terminal with prompt support | Warns if `ansible-playbook` missing |
| Ansible Inventory Graph | `<leader>oyi` in Ansible buffer | Executes `ansible-inventory --graph` in non-blocking terminal | Warns if `ansible-inventory` missing |
| Ansible Doc Lookup | `<leader>oyd` in Ansible buffer | Prompts for module name with `vim.ui.input` and runs `ansible-doc <module>` | Returns cleanly if user cancels with Esc; warns if `ansible-doc` missing |
| Ansible Vault Action | `<leader>oyv` in active file | Prompts for action (`view`, `encrypt`, `decrypt`, `edit`) via `vim.ui.select` and runs `ansible-vault <action> <file>` | Returns cleanly if user cancels with Esc; warns if `ansible-vault` missing |
| Health Check | `:checkhealth cumulus` | Reports availability of `ansible-playbook`, `ansible-lint`, `ansible-inventory`, `ansible-vault`, and `ansible-doc` | Lists missing tools as info with install suggestions |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `AnsiblePlaybookInfo`, `AnsiblePlayInfo`, `AnsibleTaskInfo`, `AnsibleValidationIssue`, `AnsibleInventoryGroup`.
- `engine/src/main/scala/cumulus/devops/AnsibleParser.scala` -- Implement YAML Ansible playbook parser, task/role extractor, offline syntax validator, and inventory graph transformer.
- `engine/src/main/scala/cumulus/Main.scala` -- Wire `--ansible-inspect`, `--ansible-validate`, and `--ansible-inventory-parse` CLI commands in Scala engine.
- `engine/src/test/scala/cumulus/devops/AnsibleParserSpec.scala` -- Unit tests for Ansible playbook parsing, inventory graph parsing, and validation in Scala engine.
- `lua/cumulus/util/engine.lua` -- Add Lua bridge helper methods (`inspect_ansible_playbook`, `validate_ansible_playbook`, `parse_ansible_inventory`).
- `lua/cumulus/util/devops.lua` -- Minimal runner functions for Ansible (`ansible_syntax_check`, `ansible_lint`, `ansible_dry_run`, `ansible_run_playbook`, `ansible_inventory_graph`, `ansible_doc_lookup`, `ansible_vault_action`) with cancellation guards.
- `lua/cumulus/core/keymaps.lua` -- Register `<leader>oy` buffer-scoped keymaps.
- `lua/cumulus/plugins/ui-whichkey.lua` -- Verify WhichKey spec for `<leader>oy`.
- `lua/cumulus/health.lua` -- Add health check verification for `ansible-playbook`, `ansible-lint`, `ansible-inventory`, `ansible-vault`, `ansible-doc`.
- `scripts/validate.sh` -- Add automated smoke test validation for Scala engine Ansible APIs and Lua bridge.

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- DEFINE data models for Ansible playbook inspection, validation, and inventory hierarchy.
- [x] `engine/src/main/scala/cumulus/devops/AnsibleParser.scala` -- IMPLEMENT Scala 3 parser and validator for Ansible playbooks and inventory graph.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- ADD `--ansible-inspect`, `--ansible-validate`, and `--ansible-inventory-parse` commands with `CumulusResponse[T]` envelope.
- [x] `engine/src/test/scala/cumulus/devops/AnsibleParserSpec.scala` -- WRITE comprehensive unit tests for Ansible analysis.
- [x] `lua/cumulus/util/engine.lua` -- EXPOSE `inspect_ansible_playbook`, `validate_ansible_playbook`, and `parse_ansible_inventory` APIs with input guards.
- [x] `lua/cumulus/util/devops.lua` -- ENHANCE Ansible runner functions with input/cancellation safety and engine integration.
- [x] `lua/cumulus/health.lua` -- ADD health check reporting for Ansible tooling (`ansible-playbook`, `ansible-lint`, `ansible-inventory`, `ansible-vault`, `ansible-doc`).
- [x] `scripts/validate.sh` -- UPDATE smoke test suite to cover Scala engine and Lua Ansible devops integration.

**Acceptance Criteria:**
- Given an Ansible playbook YAML file
- When `cumulus-engine --ansible-inspect <file>` is executed, it returns parsed plays, target hosts, task lists, and roles
- And when `cumulus-engine --ansible-validate <file>` is executed, it returns syntax errors and unquoted templating issues
- And when `cumulus-engine --ansible-inventory-parse <file>` is executed, it returns structured group and host trees
- And when the user presses `<leader>oys`, `ansible-playbook --syntax-check` runs in a non-blocking terminal
- And `<leader>oyl` runs `ansible-lint`
- And `<leader>oyc` executes `ansible-playbook --check` in an interactive terminal
- And `<leader>oyr` executes `ansible-playbook` in an interactive terminal
- And `<leader>oyi` runs `ansible-inventory --graph`
- And `<leader>oyd` prompts for module name with Esc cancel support and displays `ansible-doc`
- And `<leader>oyv` prompts for vault action (`view`, `encrypt`, `decrypt`, `edit`) and runs `ansible-vault`
- And `:checkhealth cumulus` reports status of Ansible tooling
- And all Scala engine tests (`sbt test`) pass without error

## Spec Change Log

- 2026-08-17: Implemented Story 8.3 with native Scala 3 Ansible parsing, offline syntax validation, and inventory hierarchy analysis (`AnsibleParser.scala`, `DevopsModels.scala`, `Main.scala`), unit tests (`AnsibleParserSpec.scala`), Lua bridge APIs (`engine.lua`), health checks (`health.lua`), and automated test validations (`validate.sh`).

## Design Notes

- Ansible playbook parsing extracts:
  - Play list: `name`, `hosts`, `gather_facts`, `roles`, `tasks`
  - Task list: `name`, `module` (action name, e.g. `ansible.builtin.copy`, `apt`, `service`, `template`), `line`
  - Validation rules: YAML syntax, missing mandatory keys, unquoted template expressions starting with `{{`
- Non-blocking execution uses `Snacks.terminal` or fallback split buffer to avoid locking the editor during playbook runs.

## Verification

**Commands:**
- `(cd engine && sbt "testOnly cumulus.devops.AnsibleParserSpec")` -- expected: All Scala engine unit tests pass.
- `nvim -u init.lua --headless "+lua require('cumulus.core.keymaps'); require('cumulus.util.devops'); require('cumulus.health'); print('✔ Ansible suite verified')" +qa` -- expected: Clean load without error.
- `./scripts/validate.sh` -- expected: All smoke test validations pass.

## Suggested Review Order

**Scala 3 Native Engine Ansible Processor**

- Playbook parser, task/role extractor, offline syntax validator, and inventory graph transformer.
  [`AnsibleParser.scala:1`](../../engine/src/main/scala/cumulus/devops/AnsibleParser.scala#L1)

- Case class data models for plays, tasks, validation issues, and inventory groups.
  [`DevopsModels.scala:182`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L182)

- CLI subcommands routing for `--ansible-inspect`, `--ansible-validate`, and `--ansible-inventory-parse`.
  [`Main.scala:363`](../../engine/src/main/scala/cumulus/Main.scala#L363)

**Neovim Lua Bridge & Health Checks**

- Engine bridge methods for Ansible playbook inspection and inventory parsing.
  [`engine.lua:731`](../../lua/cumulus/util/engine.lua#L731)

- Health check provider checking Ansible CLI tools (`ansible-playbook`, `ansible-lint`, `ansible-inventory`, `ansible-vault`, `ansible-doc`).
  [`health.lua:112`](../../lua/cumulus/health.lua#L112)

**Tests & Validation**

- Unit tests for playbook inspection, role extraction, offline validation, and inventory parsing.
  [`AnsibleParserSpec.scala:1`](../../engine/src/test/scala/cumulus/devops/AnsibleParserSpec.scala#L1)

- Smoke test suite validating all 319 Scala tests and Neovim integrations.
  [`validate.sh:48`](../../scripts/validate.sh#L48)
