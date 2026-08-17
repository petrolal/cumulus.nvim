# Epic 8 Context: DevOps & Infrastructure Platform Suite (<leader>o)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Provide Cloud and DevOps engineers with an interactive, buffer-scoped developer toolchain under `<leader>o` for Terraform/OpenTofu, AWS CloudFormation/SAM, and Ansible in Neovim. Operations run in non-blocking interactive terminals (`Snacks.terminal`), integrate with diagnostic linters (`nvim-lint`), formatters (`conform.nvim`), and Mason tool installer without cluttering keymaps for unrelated languages.

## Stories

- Story 8.1: Terraform & OpenTofu Tooling Suite (`<leader>ot`)
- Story 8.2: AWS CloudFormation & SAM Validation, Compilation & Local Testing Suite (`<leader>oc`)
- Story 8.3: Ansible Playbook Syntax Checking, Linting, Execution & Inventory Suite (`<leader>oy`)
- Story 8.4: DevOps Language-Scoped Buffers, Mason Automation & WhichKey Integration

## Requirements & Constraints

- **Non-blocking UI**: Long-running or interactive commands (`terraform plan/apply`, `sam build`, `sam local invoke/start-api`, `ansible-playbook`) MUST run in non-blocking terminals (`Snacks.terminal` / dedicated terminal buffers), never freezing the editor with blocking `:!`.
- **Buffer-local scoping**: DevOps keybindings must only be registered and visible in WhichKey when editing matching filetypes (`terraform`, `terraform-vars`, `hcl`, `yaml.cfn`, `yaml.sam`, `yaml.ansible`, `ansible`).
- **Binary detection & graceful degradation**: Auto-detect `tofu` vs `terraform`, `aws` vs `sam`, and `ansible-playbook`. If tools are missing, notify the user with installation hints instead of failing silently.
- **Mason package management**: Ensure all language servers, linters, and formatters are declared in `tools-mason.lua` and wired into `tools-linting.lua` and `tools-formatting.lua`.

## Technical Decisions

- **Keymap namespace**: `<leader>o` (DevOps/Infra), with `<leader>ot` (Terraform/OpenTofu), `<leader>oc` (CloudFormation/SAM), `<leader>oy` (Ansible), `<leader>od` (Docker), `<leader>ok` (Helm/K8s).
- **Buffer Registration**: Register via `require("cumulus.core.lang-keymaps").register({ filetypes = {...}, group = "<leader>oX", ... })`.
- **Terminal Execution**: Use `Snacks.terminal(cmd)` or terminal split buffers for interactive processes.
- **Diagnostics**: Register `tflint` for Terraform, `cfn_lint` for CloudFormation, and `ansible_lint` for Ansible in `tools-linting.lua`.
- **Formatting**: Register `terraform_fmt` in `tools-formatting.lua`.

## Cross-Story Dependencies

- Stories 8.1, 8.2, 8.3, and 8.4 can be developed iteratively. Story 8.1 establishes Terraform/OpenTofu, Story 8.2 adds CloudFormation/SAM, Story 8.3 adds Ansible, and Story 8.4 validates the full WhichKey, Mason, and buffer-scoping integration.
