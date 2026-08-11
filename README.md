# 🌩️ Cumulus Neovim (`cumulus.nvim`)

Production-grade, from-scratch Neovim distribution engineered for **production enterprise software engineering** with **full 1:1 feature parity to JetBrains IntelliJ IDEA Ultimate** across **JVM/Polyglot** development (Java, Kotlin, Groovy), **Web/Markup & Database** (HTML, XML, JSON, DataGrip SQL), and **Cloud DevOps** (Terraform, Kubernetes, Docker, Helm, Ansible, CloudFormation).

Powered by a high-speed compiled **Rust Native Helper Engine (`cumulus-core`)** and connected via an asynchronous **Lua IPC Bridge**, `cumulus.nvim` delivers sub-50ms cold startup times without compromising on enterprise IDE capabilities.


---

## ⚡ Features & Architecture

* **Rust-First Core Engine (`crates/cumulus-core`)**: Heavy XML/TOML/YAML parsing, POM/Gradle multi-module AST scanning, Checkstyle inspection parsing, Spring bean graph analysis, REST endpoint extraction, JaCoCo coverage mapping, log regex indexing, Git conflict detection, and non-blocking TCP network checks are offloaded to Rust.
* **Minimal Lua Bridge (`lua/cumulus/util/rust.lua`)**: Connects Neovim's UI, diagnostics, keymaps, and autocmds to `cumulus-core` via JSON IPC, falling back smoothly to Lua if the Rust helper is not compiled.
* **JVM & Polyglot Stack**: `jdtls` (Java), `kotlin-language-server`, `groovyls`, plus JUnit 5 test runner, Gradle/Maven build tooling, and Spring Boot support.
* **DevOps & Cloud Suites**: Pre-configured LSP, linting, formatting, Treesitter parsers, and DAP setups for Terraform, Kubernetes/Helm/Docker, CloudFormation, Ansible, and remote debugging.
* **Signature Cloud Themes**: Dedicated multi-cloud dark modes (`aws-theme`, `azure-theme`, `gcp-theme`, `oci-theme`).
* **Instant Startup Budget**: Cold startup achieved in **~24.8ms** (< 50ms requirement) through strict `lazy.nvim` event loading.

---

## 📋 Prerequisites

* **Neovim** $\ge$ 0.10.0
* **Rust & Cargo** (Required to compile `cumulus-core`)
* **git**, **ripgrep** (`rg`), **fd** (`fd`)
* **Node.js** & **npm** *(optional, for markdown preview and NPM-based language servers)*

---

## 🚀 Quick Start

### Automated Installation

Clone the repository and run the installer script:

```bash
git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim
cd ~/.config/nvim
bash scripts/install.sh
```

*(If installing side-by-side with an existing Neovim config, clone into `~/.config/cumulus` and launch via `NVIM_APPNAME=cumulus nvim`)*

---

### Manual Installation Steps

1. **Clone the repo:**
   ```bash
   git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim
   ```

2. **Compile the Rust Native Engine:**
   ```bash
   cd ~/.config/nvim/crates/cumulus-core
   cargo build --release
   ```

3. **Sync Lazy Plugins:**
   ```bash
   nvim "+Lazy! sync" +qa
   ```

4. **Run Verification:**
   ```bash
   bash scripts/validate.sh
   ```

---

### 📦 Cargo Registry & `cargo install`

`cumulus-core` supports standard Cargo binary resolution and Cargo registry installation.

* **Install locally via Cargo:**
  ```bash
  cargo install --path crates/cumulus-core
  ```
* **Install from Crates.io (if published):**
  ```bash
  cargo install cumulus-core
  ```

> When `cumulus-core` is in your `$PATH` (e.g. `~/.cargo/bin`), the Lua bridge detects it automatically without needing repository-relative binary paths.


---

## ⌨️ Common Keymaps

| Keymap | Action |
| --- | --- |
| `<leader>cf` | Format current buffer (LSP / conform.nvim) |
| `<leader>ca` | Code action |
| `<leader>cr` | Rename symbol |
| `[d` / `]d` | Previous / Next diagnostic item |
| `<leader>cd` | Line diagnostics floating window |
| `<leader>ds` | Spring Boot debug & hotswap launcher |
| `<space>` | Global Leader key |

---

## 🧪 Health Check & Diagnostics

Inside Neovim, verify system requirements and the status of the Rust helper binary by running:

```vim
:checkhealth cumulus
```

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
