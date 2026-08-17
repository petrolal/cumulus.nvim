---
title: 'Story 8.2: AWS CloudFormation & SAM Validation, Compilation & Local Testing Suite (<leader>oc)'
type: 'feature'
created: '2026-08-17'
status: 'done'
baseline_commit: '63a4f4936a520eaa22afea55cd28e4220d39fa8e'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** AWS Cloud and Serverless engineers working with AWS CloudFormation and SAM templates in Neovim lack a unified, non-blocking toolchain for validation, linting, building, local API/function testing, and policy evaluation under `<leader>oc`. Furthermore, template inspection, resource/parameter extraction, offline validation, and command assembly should be processed in the native Scala 3 engine (`cumulus-engine`), keeping Lua files minimal and thin.

**Approach:** Implement CloudFormation & SAM template parsing, resource inspection, offline validation, and DevOps tool discovery in the Scala 3 engine (`cumulus.devops.CfnSamParser`, `CfnSamValidator`), with CLI subcommands in `Main.scala`. Wire these through `engine.lua` and minimal Lua keymaps/runners in `devops.lua` under `<leader>oc` to non-blocking interactive terminals (`Snacks.terminal`), diagnostic linters (`nvim-lint`), schemas (`yamlls`), and health checks (`:checkhealth cumulus`).

## Boundaries & Constraints

**Always:**
- Offload template parsing, resource extraction, intrinsic reference validation (`!Ref`, `!GetAtt`), and DevOps tool discovery to the Scala 3 engine (`cumulus-engine`).
- Keep Lua code minimal: Lua only handles user keymaps, calling the Scala engine via `engine.lua`, and launching terminal processes (`Snacks.terminal`).
- Use non-blocking interactive terminals (`Snacks.terminal(...)` or terminal buffers) for interactive or long-running commands (`sam build`, `sam local invoke`, `sam local start-api`, `cfn-guard validate`), avoiding editor lockup.
- Scope keymaps to relevant buffer filetypes (`yaml.cfn`, `yaml.sam`, `cloudformation`, `sam`, or `yaml`/`json` files with CloudFormation/SAM indicators) using `lang_keymaps.register({...})`.
- Gracefully handle missing CLI binaries (`aws`, `sam`, `cfn-lint`, `cfn-guard`) by checking `vim.fn.executable(...)` and notifying the user with actionable installation instructions via `vim.notify`.

**Never:**
- Never duplicate complex parsing or regex-heavy validation logic in Lua when the Scala engine can do it.
- Never execute blocking Vim commands (e.g. `:!sam build`) that freeze the Neovim UI thread.
- Never leak `<leader>oc` keybindings into unrelated buffers (e.g. Java, Python, Rust, or general non-CFN YAML).

## I/O & Edge-Case Matrix

| Scenario | Trigger / Input | Expected Output / Behavior | Error Handling |
|----------|----------------|---------------------------|----------------|
| Inspect CFN / SAM Template | `cumulus-engine --cfn-inspect <file>` | Returns JSON `CumulusResponse[CfnTemplateInfo]` with parameters, resources, outputs, and serverless functions | Returns error envelope if file missing or malformed |
| Offline CFN / SAM Validation | `cumulus-engine --cfn-validate <file>` | Returns JSON `CumulusResponse[Seq[CfnValidationIssue]]` detecting missing required fields, bad intrinsics, unknown resource types | Returns structured error envelope |
| CloudFormation Validate | `<leader>ocv` in `template.yaml` | Executes `aws cloudformation validate-template --template-body file://<file>` in non-blocking terminal | Warns if `aws` CLI is missing with installation guidance |
| CloudFormation Lint | `<leader>ocl` in template buffer | Executes `cfn-lint` on the template file | Warns if `cfn-lint` is missing with `:MasonInstall cfn-lint` hint |
| SAM Validate | `<leader>ocV` in SAM template | Executes `sam validate` in terminal | Warns if `sam` CLI is missing with install URL hint |
| SAM Build | `<leader>ocb` in SAM template | Executes `sam build` in non-blocking terminal | Warns if `sam` CLI is missing |
| SAM Local Invoke | `<leader>oci` in SAM template | Prompts for Lambda function from engine-extracted list and runs `sam local invoke <func>` in interactive terminal | Warns if `sam` CLI is missing |
| SAM Local Start API | `<leader>ocr` in SAM template | Executes `sam local start-api` in interactive terminal | Warns if `sam` CLI is missing |
| Policy-as-Code Guard | `<leader>ocg` in CFN template | Executes `cfn-guard validate --template <file>` in terminal | Warns if `cfn-guard` is missing |
| Health Check | `:checkhealth cumulus` | Reports availability of `aws`, `sam`, `cfn-lint`, and `cfn-guard` | Lists missing tools as info/warn with install suggestions |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `CfnTemplateInfo`, `CfnResource`, `CfnParameter`, `CfnValidationIssue`, `SamFunctionInfo`.
- `engine/src/main/scala/cumulus/devops/CfnSamParser.scala` -- Implement YAML/JSON CloudFormation & SAM parser, resource extractor, and offline validator.
- `engine/src/main/scala/cumulus/Main.scala` -- Wire `--cfn-inspect` and `--cfn-validate` CLI commands in Scala engine.
- `engine/src/test/scala/cumulus/devops/CfnSamParserSpec.scala` -- Unit tests for CloudFormation and SAM parsing and validation in Scala engine.
- `lua/cumulus/util/engine.lua` -- Add Lua bridge helper methods (`inspect_cfn_template`, `validate_cfn_template`).
- `lua/cumulus/util/devops.lua` -- Minimal runner functions for CloudFormation and SAM (`cfn_validate`, `cfn_lint`, `sam_validate`, `sam_build`, `sam_local_invoke`, `sam_local_start_api`, `cfn_guard_validate`) utilizing engine results where appropriate.
- `lua/cumulus/core/keymaps.lua` -- Register `<leader>oc` buffer-scoped keymaps.
- `lua/cumulus/plugins/ui-whichkey.lua` -- Define WhichKey spec for `<leader>oc`.
- `lua/cumulus/health.lua` -- Add health check verification for `aws`, `sam`, `cfn-lint`, `cfn-guard`.
- `scripts/validate.sh` -- Add automated smoke test validation for Scala engine CFN/SAM APIs and Lua bridge.

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- DEFINE data models for CloudFormation & SAM template inspection and diagnostics.
- [x] `engine/src/main/scala/cumulus/devops/CfnSamParser.scala` -- IMPLEMENT Scala 3 parser and validator for CloudFormation & SAM templates.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- ADD `--cfn-inspect` and `--cfn-validate` commands with `CumulusResponse[T]` envelope.
- [x] `engine/src/test/scala/cumulus/devops/CfnSamParserSpec.scala` -- WRITE comprehensive unit tests for CFN/SAM template analysis.
- [x] `lua/cumulus/util/engine.lua` -- EXPOSE `inspect_cfn_template` and `validate_cfn_template` APIs.
- [x] `lua/cumulus/util/devops.lua` -- KEEP Lua minimal and connect SAM local invoke function selection to engine.
- [x] `lua/cumulus/health.lua` -- ADD health check reporting for CloudFormation & SAM tooling (`aws`, `sam`, `cfn-lint`, `cfn-guard`).
- [x] `scripts/validate.sh` -- UPDATE smoke test suite to cover Scala engine and Lua devops integration.

**Acceptance Criteria:**
- Given a CloudFormation or SAM YAML/JSON template
- When `cumulus-engine --cfn-inspect <file>` is executed, it returns parsed resources, serverless functions, parameters, and template format version
- And when `cumulus-engine --cfn-validate <file>` is executed, it returns syntax and intrinsic reference validation issues
- And when the user presses `<leader>ocv`, `aws cloudformation validate-template` is run in a non-blocking terminal
- And `<leader>ocl` runs `cfn-lint`
- And `<leader>ocV` runs `sam validate`
- And `<leader>ocb` runs `sam build` in a non-blocking terminal
- And `<leader>oci` fetches serverless functions from `cumulus-engine` and prompts user to pick function for `sam local invoke`
- And `<leader>ocr` launches `sam local start-api` in an interactive terminal
- And `<leader>ocg` executes `cfn-guard validate`
- And `:checkhealth cumulus` reports status of `aws`, `sam`, `cfn-lint`, and `cfn-guard`
## Spec Change Log

- 2026-08-17: Implemented Story 8.2 with native Scala 3 parsing and offline validation (`CfnSamParser.scala`, `DevopsModels.scala`, `Main.scala`), unit tests (`CfnSamParserSpec.scala`), Lua bridge APIs (`engine.lua`), non-blocking terminal runners (`devops.lua`), health checks (`health.lua`), and automated test validations (`validate.sh`).

## Design Notes

- Parsing in Scala engine uses YAML/JSON parsing with fallback heuristics to extract:
  - `AWSTemplateFormatVersion`, `Transform`
  - Resources map: `LogicalID -> { Type, Properties }`
  - Parameters map: `Name -> { Type, Default }`
  - Functions: Resources with `Type: AWS::Serverless::Function` or `AWS::Lambda::Function`
- Lua bridge `engine.lua` invokes `cumulus-engine --cfn-inspect <file>` asynchronously or synchronously for function listing in `sam local invoke`.

## Verification

**Commands:**
- `(cd engine && sbt "testOnly cumulus.devops.CfnSamParserSpec")` -- expected: All Scala engine unit tests pass.
- `nvim -u init.lua --headless "+lua require('cumulus.core.keymaps'); require('cumulus.util.devops'); require('cumulus.health'); print('✔ CloudFormation/SAM suite verified')" +qa` -- expected: Clean load without error.
- `./scripts/validate.sh` -- expected: All smoke test validations pass.

## Suggested Review Order

**Scala 3 Native Engine CFN/SAM Processor**

- CloudFormation & SAM template inspection, resource/function extraction, and offline validation engine.
  [`CfnSamParser.scala:1`](../../engine/src/main/scala/cumulus/devops/CfnSamParser.scala#L1)

- Data models for parameters, resources, SAM functions, and validation issues.
  [`DevopsModels.scala:135`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L135)

- CLI router wiring for `--cfn-inspect` and `--cfn-validate`.
  [`Main.scala:347`](../../engine/src/main/scala/cumulus/Main.scala#L347)

**Neovim Lua Bridge & DevOps Runners**

- Non-blocking SAM local invoke, build, start-api, template validation, and cfn-guard runners.
  [`devops.lua:208`](../../lua/cumulus/util/devops.lua#L208)

- Thin Lua bridge methods calling `cumulus-engine` CLI commands.
  [`engine.lua:707`](../../lua/cumulus/util/engine.lua#L707)

- Health check provider checking AWS and SAM DevOps tooling status.
  [`health.lua:96`](../../lua/cumulus/health.lua#L96)

**Tests & Validation**

- Unit test suite verifying SAM functions, CFN JSON/YAML inspection, and offline validation rules.
  [`CfnSamParserSpec.scala:1`](../../engine/src/test/scala/cumulus/devops/CfnSamParserSpec.scala#L1)

- Automated smoke test verifying DevOps CLI integrations.
  [`validate.sh:48`](../../scripts/validate.sh#L48)
