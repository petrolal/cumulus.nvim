<!-- bmad:context -->
<!-- Verified 2026-08-25 against 76bae38. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## cumulus.nvim

Enterprise-ready Neovim distribution for JVM backend engineering. Lua, Lazy.nvim, Mason. Migrating strictly to native Neovim LSPs and plugins instead of a custom backend.

## Policy

- Never add Scala or `sbt` code; the custom `cumulus-engine` is deprecated and removed. Delegate heavy lifting to standard community LSPs.

## Where things are

- Plugin specifications (Lazy): `lua/cumulus/plugins/`
- DevOps toolings & bridges: `lua/cumulus/util/devops.lua`

## Running and verifying

- Run `bash scripts/validate.sh` to perform headless validation of shell scripts, module loading, global keymaps, and DevOps utilities. 

## Conventions that differ from defaults

- DevOps keymaps (e.g., `<leader>ot` for Terraform, `<leader>oy` for Ansible) are registered globally, not buffer-scoped.

<!-- /bmad:context -->
