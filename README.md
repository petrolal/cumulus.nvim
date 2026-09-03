# tetravim.nvim

> Enterprise-ready Neovim distribution for modern JVM backend engineering (Java, Kotlin, Scala, Gradle) and Cloud Native development.

Built entirely on the standard Neovim ecosystem (Lua, standard LSPs, Treesitter, DAP) to serve as a full, stable replacement for IntelliJ IDEA. It pairs seamlessly with [`tetravim.dotfiles`](https://github.com/petrolal/tetravim.dotfiles).

---

## Vision & Architecture

**TetraVim** is designed to provide best-in-class JVM and Cloud intelligence directly within Neovim.

TetraVim is **pure native Neovim** — standard LSPs, Tree-sitter, Mason tools, and Lua utilities. There is no `tetravim-engine`, no Scala backend, and no bridge; the goal is parity with IntelliJ IDEA using standard, stable, community-backed plugins.

### Core Ecosystem:
- **Build Systems**: Maven, Gradle, SBT integration via native language servers.
- **Java / Kotlin / Scala**: Full intelligence via `nvim-jdtls`, Kotlin Language Server, and Metals.
- **Spring Boot Ecosystem**: Deep integration using existing Neovim Spring Boot tools and DAP.
- **Diagnostics & Testing**: Native Neovim diagnostic displays, `nvim-dap` for debugging, and test execution plugins (like `neotest`).
- **DevOps & Cloud**: Flyway migrations, Kubernetes, and Docker support through standard LSPs.

---

## Requirements

| Requirement | Notes |
| --- | --- |
| **Neovim ≥ 0.11** | Uses the native `vim.lsp.config`/`vim.lsp.enable` API, `vim.diagnostic.jump`, and `winborder`. |
| **A Nerd Font (v3.0+)** | **Required.** The dashboard, statusline, bufferline, winbar breadcrumbs, which-key groups, file-tree and completion menus all render Nerd Font glyphs — without one you get tofu boxes (`􏿽`). Install any patched font from [nerdfonts.com](https://www.nerdfonts.com/font-downloads) (e.g. *JetBrainsMono Nerd Font*, *FiraCode Nerd Font*) and select it as your **terminal**'s font. `vim.g.have_nerd_font` is already set. |
| **True-color terminal** | `termguicolors` is enabled; use a terminal with 24-bit colour (WezTerm, Kitty, Alacritty, Ghostty, modern iTerm2 / Windows Terminal). |
| **`git`, `ripgrep`, `make`, a C compiler** | For `lazy.nvim`, Telescope live-grep, and `telescope-fzf-native` / treesitter parser builds. |

### Appearance toggles

| Keys | Action |
| --- | --- |
| `<leader>ut` | Toggle background transparency (lets a translucent terminal show through). |
| `<leader>cb` | Open the winbar breadcrumb symbol picker. |
| `[;` / `];` | Jump to / descend into the enclosing code context. |

---

## Installation

### Quick Shell Bootstrap

```bash
git clone https://github.com/petrolal/tetravim.nvim.git ~/.config/nvim
cd ~/.config/nvim
./bootstrap.sh
```

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
