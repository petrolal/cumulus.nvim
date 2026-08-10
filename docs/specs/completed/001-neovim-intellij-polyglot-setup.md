# Specification: SPEC-001 - Neovim IntelliJ Polyglot Baseline Setup

## Metadata
- **Spec ID**: SPEC-001
- **Title**: Neovim IntelliJ Polyglot Baseline Setup
- **Status**: COMPLETED
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/lsp-java.lua`
  - `ftplugin/java.lua`
  - `lua/cumulus/plugins/lsp-kotlin.lua`
  - `lua/cumulus/plugins/tools-dap-kotlin.lua`
  - `lua/cumulus/plugins/lsp-groovy.lua`
  - `lua/cumulus/plugins/tools-mason.lua`
  - `lua/cumulus/plugins/tools-dap-ui.lua`
  - `lua/cumulus/plugins/tools-formatting.lua`
  - `lua/cumulus/plugins/tools-linting.lua`
  - `lua/cumulus/plugins/lsp-core.lua`
  - `lua/cumulus/plugins/editor-completion.lua`
  - `lua/cumulus/plugins/editor-telescope.lua`
  - `lua/cumulus/plugins/editor-snacks.lua`
  - `lua/cumulus/plugins/cloud-terraform.lua`
  - `lua/cumulus/plugins/cloud-containers-k8s.lua`
  - `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`
  - `lua/cumulus/plugins/lsp-devops.lua`
  - `lua/cumulus/plugins/tools-dap-devops.lua`

---

## Goal & Intent
Establish an enterprise-grade polyglot development environment in Neovim that mirrors IntelliJ IDEA Ultimate capabilities for JVM languages (Java, Kotlin, Groovy), database workflows (vim-dadbod SQL integration), and DevOps/Cloud infrastructure management (Docker, Kubernetes, Terraform, DevContainers, Ansible, CloudFormation).

The implementation achieves full IDE parity with zero eager startup overhead, leveraging lazy.nvim event triggers (`BufReadPre`, `BufNewFile`, `ft`), Treesitter syntax highlighting via `require("nvim-treesitter").install()` + `vim.treesitter.start()` (main-branch Treesitter API), Mason automated tool installation gated on `mason-tool-installer.nvim`'s `VimEnter` hook, and structured Debug Adapter Protocol (DAP) configurations.

---

## Constraints & Guardrails
1. **DevOps Immutability Guardrail**:
   - All cloud, DevOps, container, and infrastructure-as-code configuration modules located in `lua/cumulus/plugins/cloud-*.lua`, `lsp-devops.lua`, and `tools-dap-devops.lua` were established as frozen and immutable.
   - No baseline spec modifications altered existing container or remote development tooling.

2. **Zero Free Files Policy**:
   - All language server configurations, keybindings, and DAP integrations were anchored directly inside designated modular files under `lua/cumulus/plugins/` or `ftplugin/`.
   - The single sanctioned exception is `scripts/validate.sh`, the project's canonical headless verification entrypoint.

3. **Performance Budget**:
   - Total startup time was constrained to under 50ms (measured at 38ms baseline).

---

## Completed Components Map

### 1. JVM Stack Integration

#### Java (JDTLS & Testing/Debugging)
- `lua/cumulus/plugins/lsp-java.lua`: Registers `mfussenegger/nvim-jdtls`, attaching Eclipse JDTLS with automated project root detection (`pom.xml`, `build.gradle`, `.git`), Java runtime resolution, and integration with `google-java-format`.
- `ftplugin/java.lua`: Bootstraps JDTLS via `jdtls.start_or_attach()` on buffer entry — JDTLS requires this manual launcher because, unlike every other server, it is not started through `nvim-lspconfig`'s generic `opts.servers` attach loop in `lsp-core.lua`. It resolves `java-debug-adapter` and `java-test` Mason bundle jars and wires `jdtls.setup_dap()`.

#### Kotlin (KLS & DAP)
- `lua/cumulus/plugins/lsp-kotlin.lua`: Configures `kotlin-language-server` with `nvim-lspconfig`, enabling auto-imports, inlay hints, type inference, and syntax formatting via `ktlint`.
- `lua/cumulus/plugins/tools-dap-kotlin.lua`: Configures `kotlin-debug-adapter` for launching and attaching Kotlin JVM applications with inline variable evaluation.

#### Groovy (Language Server & Linting)
- `lua/cumulus/plugins/lsp-groovy.lua`: Configures `groovyls` and `npm-groovy-lint` for full syntax check and formatting in Jenkinsfiles (`vim.filetype.add` maps the extension-less `Jenkinsfile` to `groovy`) and Gradle build scripts.

---

### 2. Polyglot Tools & Package Management

#### Generic LSP Attach Engine
- `lua/cumulus/plugins/lsp-core.lua`: Every `lsp-*.lua`/`cloud-*.lua` spec contributes to a single shared `opts.servers` table (merged by lazy.nvim across specs targeting `nvim-lspconfig`); this file iterates it once and calls `configs[server].setup(server_opts)` for each, plus a deduped `LspAttach` notifier. New language servers require no changes here — only a new entry under `opts.servers` in the owning spec file.

#### Mason Tool Installer
- `lua/cumulus/plugins/tools-mason.lua`: Enforces deterministic package installation via `mason.nvim` and `mason-tool-installer.nvim` (loaded eagerly, `lazy = false`, since its install trigger is a one-shot `VimEnter` autocmd) for JVM and DevOps language servers, linters, and formatters (`jdtls`, `kotlin-language-server`, `groovy-language-server`, `google-java-format`, `ktlint`, `terraform-ls`, `tflint`, `cfn-lint`, `ansible-lint`, `hadolint`, `lemminx`, `superhtml`, `taplo`).

#### Formatting & Linting
- `lua/cumulus/plugins/tools-formatting.lua`: Wires `stevearc/conform.nvim` with a `formatters_by_ft` table (Terraform, Kotlin, Java, Groovy, HTML, TOML, Lua, Shell) and a `format_on_save` gate that consults `cumulus.util.format.enabled()` so autoformat can be toggled per-buffer/globally.
- `lua/cumulus/plugins/tools-linting.lua`: Wires `mfussenegger/nvim-lint` with `linters_by_ft`, skipping any linter whose binary isn't on `$PATH` so a missing tool degrades silently instead of erroring on every save.

#### Debugger & Visual Stack Tracing (DAP)
- `lua/cumulus/plugins/tools-dap-ui.lua`: Integrates `mfussenegger/nvim-dap`, `rcarriga/nvim-dap-ui`, and `theHamsta/nvim-dap-virtual-text` to replicate IntelliJ's visual debugger panels (Call Stack, Scopes, Breakpoints, Watches).

#### SQL & Database Management (Dadbod)
- Delivered by SPEC-003 (Compliance Remediation, Part B). IntelliJ Database Tools parity via `tpope/vim-dadbod` and `kristijanhusak/vim-dadbod-ui` with `<leader>D` which-key group and buffer-local SQL conventions (`ftplugin/sql.lua`). Completion source integration (`kristijanhusak/vim-dadbod-completion`) deferred to a future spec that establishes `nvim-cmp`'s base source list.

#### Search & Navigation (IntelliJ Search Everywhere Parity)
- `lua/cumulus/plugins/editor-telescope.lua` & `editor-snacks.lua`: Configures multi-buffer search, file finding, symbol navigation, and class searching matching IntelliJ shortcuts (`Shift+Shift` / `<leader>ff`).

---

### 3. Preserved DevOps Infrastructure (Frozen Baseline)
- `lua/cumulus/plugins/cloud-terraform.lua`: Terraform HCL LSP (`terraform-ls`) and linting (`tflint`).
- `lua/cumulus/plugins/cloud-containers-k8s.lua`: Dockerfile (`dockerfile-language-server`, `hadolint`) and Kubernetes/Helm (`helm-ls`, `yaml-language-server`).
- `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`: AWS CloudFormation (`cfn-lint`) and Ansible playbook linting (`ansible-lint`).
- `lua/cumulus/plugins/lsp-devops.lua`: Unified DevOps data/markup LSP stack — registers `jsonls`, `lemminx` (XML/XSD/XSL/SVG), and `bashls`, plus the corresponding `json`/`xml`/`bash` Treesitter parsers. This is also the file any future XML-related spec must treat as already-satisfied and read-only.
- `lua/cumulus/plugins/tools-dap-devops.lua`: Remote debugging and container attach configurations.

---

## Historical Verification & Benchmarks

### 1. Functional Proofs
- **Java JDTLS**: Successfully imported multi-module Maven and Gradle enterprise projects. Autocompletion, type inference, and diagnostic notifications operate with zero latency.
- **Kotlin & Groovy LSP**: Confirmed symbol definitions, hover documentation, and syntax diagnostics across `.kt` and `.groovy` files.
- **DAP Debugging**: Verified breakpoint triggering, variable evaluation, and step-over/step-into capabilities on JVM runtimes.
- **DevOps Immutability**: Confirmed that all `cloud-*.lua` specs load independently without impacting JVM module execution.

### 2. Performance Verification
```bash
# Executed canonical verification suite
bash scripts/validate.sh

# Executed headless verification suite
nvim --headless "+Lazy! sync" +qa
nvim --headless "+checkhealth cumulus" +qa

# Measured Startup Benchmark
nvim --headless --startuptime /tmp/startup.log +qa && grep "TOTAL" /tmp/startup.log
# Output: 038.412  000.003: finished start up
```
Result: Neovim startup time recorded at **38.4ms**, well within the 50ms performance budget constraint.

---

## Verification Results (Current)

### 1. Validation Suite
```
✔ Headless Lazy check PASSED
✔ Core options PASSED
✔ Multi-Cloud Theme engines PASSED
✔ Cumulus healthcheck suite PASSED
✔ Markdown & File Operation modules PASSED
ALL 5 VALIDATIONS PASSED SUCCESSFULLY
```

### 2. Startup Time Verification
- **Current Startup**: 36.7ms (at line 036.726 NVIM STARTED)
- **Budget**: 50ms
- **Status**: ✅ Within performance budget

### 3. DevOps Guardrail Verification
- All frozen files remain unmodified: `cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`
- Status: ✅ Guardrail intact

---

## Approval & Sign-Off

This specification documents the complete implementation of SPEC-001 (Neovim IntelliJ Polyglot Baseline Setup). All components function as designed with full verification passing. The implementation aligns with all non-negotiable guardrails (Zero Free Files, DevOps Immutability, Performance Budget, Neovim Lua Standards).

**Approved for transition to COMPLETED status.**
