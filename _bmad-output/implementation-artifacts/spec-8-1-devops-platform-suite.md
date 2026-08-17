---
title: 'DevOps & Infrastructure Platform Suite (Terraform, CloudFormation, Ansible)'
type: 'feature'
created: '2026-08-17'
status: 'done'
baseline_commit: '4f9d34fddf60d521ceac593fd505d9741c5ec22b'
review_loop_iteration: 0
context: ['_bmad-output/planning-artifacts/epics.md', '_bmad-output/implementation-artifacts/epic-8-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** DevOps and Cloud engineers working in Terraform, CloudFormation/SAM, and Ansible lack interactive compiler, linter, test, and runner tooling in Neovim. Currently, keymaps under `<leader>o` execute blocking `:!` commands or are missing entirely (such as CloudFormation/SAM `<leader>oc` and comprehensive Terraform/Ansible commands).

**Approach:** Implement a comprehensive, buffer-scoped developer toolchain under `<leader>o` for Terraform/OpenTofu (`<leader>ot`), AWS CloudFormation/SAM (`<leader>oc`), and Ansible (`<leader>oy`), wired to non-blocking interactive terminals (`Snacks.terminal`), diagnostic linters (`nvim-lint`), formatters (`conform.nvim`), and Mason tool installer with WhichKey hierarchy mirroring the JVM platform experience (`<leader>j`).

## Boundaries & Constraints

**Always:**
- Use non-blocking interactive terminals (`Snacks.terminal(...)` or terminal buffers) for interactive or long-running commands (`terraform plan/apply`, `sam build`, `sam local start-api`, `ansible-playbook`), avoiding editor lockup.
- Scope keymaps to relevant buffer filetypes using `lang_keymaps.register({...})` so that DevOps commands only appear when editing relevant IaC/DevOps buffers.
- Gracefully handle missing CLI binaries by checking `vim.fn.executable(...)` and notifying the user with actionable installation instructions via `vim.notify`.

**Never:**
- Never execute blocking Vim commands (e.g. `:!terraform apply`) that lock Neovim input.
- Never register global keybindings under `<leader>o` that show up in unrelated buffers (e.g. Java, Python, Rust).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Terraform Plan | `<leader>otp` in `main.tf` | Launches `terraform plan` (or `tofu plan`) in `Snacks.terminal` | Warns if neither `terraform` nor `tofu` in PATH |
| Terraform Validate | `<leader>otv` in `main.tf` | Executes validation and displays diagnostics | Displays error toast if binary missing |
| CloudFormation Validate | `<leader>ocv` in `template.yaml` | Validates CFN template via `aws cloudformation validate-template` | Warns if `aws` CLI is missing |
| SAM Build / Run | `<leader>ocb` or `<leader>ocr` in SAM template | Executes `sam build` or `sam local start-api` in `Snacks.terminal` | Warns if `sam` CLI is missing |
| Ansible Syntax Check | `<leader>oys` in `playbook.yaml` | Executes `ansible-playbook --syntax-check` on current file | Warns if `ansible-playbook` missing |
| Ansible Dry-Run | `<leader>oyc` in `playbook.yaml` | Executes `ansible-playbook --check` in `Snacks.terminal` | Warns if `ansible-playbook` missing |
| Non-DevOps Buffer | Editing `Main.java` | `<leader>ot`, `<leader>oc`, `<leader>oy` do not appear in WhichKey | N/A |

</frozen-after-approval>

## Code Map

- `lua/cumulus/core/keymaps.lua` -- Register language-scoped keymaps for Terraform (`<leader>ot`), CloudFormation (`<leader>oc`), and Ansible (`<leader>oy`).
- `lua/cumulus/plugins/ui-whichkey.lua` -- Define WhichKey specs for `<leader>o`, `<leader>ot`, `<leader>oc`, `<leader>oy`, `<leader>od`, `<leader>ok`.
- `lua/cumulus/plugins/tools-linting.lua` -- Configure `nvim-lint` for `terraform` (`tflint`), `cloudformation` / `yaml` (`cfn_lint`), and `ansible` / `yaml.ansible` (`ansible_lint`).
- `lua/cumulus/plugins/tools-formatting.lua` -- Configure `conform.nvim` formatters for Terraform (`terraform_fmt`) and YAML.
- `lua/cumulus/plugins/tools-mason.lua` -- Ensure `terraform-ls`, `tflint`, `cfn-lint`, `ansible-language-server`, `ansible-lint`, and `yaml-language-server` are in `ensure_installed`.
- `lua/cumulus/plugins/cloud-cloudformation-ansible.lua` -- Configure schema associations and filetype settings for CloudFormation and Ansible.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/plugins/ui-whichkey.lua` -- ADD `<leader>oc` (cloudformation/sam) group and update DevOps labels with icons.
- [x] `lua/cumulus/core/keymaps.lua` -- EXPAND `lang_keymaps.register` for Terraform (`<leader>ot`), CloudFormation/SAM (`<leader>oc`), and Ansible (`<leader>oy`) with helper functions using `Snacks.terminal` and executable checks.
- [x] `lua/cumulus/plugins/tools-linting.lua` -- UPDATE `linters_by_ft` to ensure `yaml.ansible`, `ansible`, `yaml.cfn`, `terraform` have accurate linter bindings.
- [x] `lua/cumulus/plugins/tools-formatting.lua` -- ENSURE `conform.nvim` formatters are set for `terraform`, `tf`, `hcl`.
- [x] `lua/cumulus/plugins/tools-mason.lua` -- VERIFY `ensure_installed` includes all DevOps LSPs, linters, and formatters.

**Acceptance Criteria:**
- Given a `terraform` or `hcl` buffer, when `<leader>ot` is pressed, WhichKey shows the complete Terraform menu (`init`, `validate`, `plan`, `apply`, `fmt`, `tflint`, `security`, `output`).
- Given a CloudFormation/SAM buffer, when `<leader>oc` is pressed, WhichKey shows the CloudFormation menu (`validate`, `cfn-lint`, `sam validate`, `sam build`, `sam local invoke`, `sam local start-api`, `cfn-guard`).
- Given an Ansible buffer, when `<leader>oy` is pressed, WhichKey shows the Ansible menu (`syntax-check`, `lint`, `dry-run check`, `run playbook`, `inventory graph`, `doc lookup`, `vault`).
- Given an unrelated buffer (e.g. Java), when opening `<leader>o`, none of the buffer-local subgroups bleed through.

## Design Notes

- Commands that require user interaction or take significant time run through `Snacks.terminal(cmd)`.
- Terraform command runner detects whether `tofu` or `terraform` is installed:
  ```lua
  local function get_tf_cmd()
    if vim.fn.executable("tofu") == 1 then return "tofu" end
    if vim.fn.executable("terraform") == 1 then return "terraform" end
    return nil
  end
  ```
- Filetype detection for CloudFormation supports both `filetype=yaml.cfn` / `yaml` buffers containing CloudFormation patterns (`AWSTemplateFormatVersion`).

## Verification

**Commands:**
- `nvim --headless +"lua require('cumulus.core.keymaps')" +qa` -- expected: Clean load with zero errors.
- `nvim --headless +"checkhealth" +qa` -- expected: All modules pass health checks.

## Suggested Review Order

**DevOps Runners & Terminal Execution**

- Comprehensive CLI runners for Terraform, CloudFormation/SAM, and Ansible via `Snacks.terminal`.
  [`devops.lua:1`](../../lua/cumulus/util/devops.lua#L1)

**Keymap Registrations & Scoping**

- Buffer-scoped keymap bindings under `<leader>ot`, `<leader>oc`, `<leader>oy` with condition checks.
  [`keymaps.lua:273`](../../lua/cumulus/core/keymaps.lua#L273)

**WhichKey Integration**

- DevOps group labels and icons in WhichKey menu.
  [`ui-whichkey.lua:37`](../../lua/cumulus/plugins/ui-whichkey.lua#L37)

**Diagnostics & Linters**

- Filetype bindings for `tflint`, `cfn_lint`, and `ansible_lint` in `nvim-lint`.
  [`tools-linting.lua:9`](../../lua/cumulus/plugins/tools-linting.lua#L9)
