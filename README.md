# cumulus.nvim

> Enterprise-ready Neovim distribution for modern JVM backend engineering (Java, Kotlin, Scala, Gradle) and Cloud Native development.

Built entirely on the standard Neovim ecosystem (Lua, standard LSPs, Treesitter, DAP) to serve as a full, stable replacement for IntelliJ IDEA. It pairs seamlessly with [`cumulus.dotfiles`](https://github.com/petrolal/cumulus.dotfiles).

---

## Vision & Architecture

**Cumulus** is designed to provide best-in-class JVM and Cloud intelligence directly within Neovim.

Previously, this project relied on a custom background Scala engine. We are actively **migrating away** from that custom backend to embrace the standard Neovim plugin ecosystem. The goal is to provide parity with IntelliJ IDEA utilizing standard, stable, community-backed plugins.

### Core Ecosystem:
- **Build Systems**: Maven, Gradle, SBT integration via native language servers.
- **Java / Kotlin / Scala**: Full intelligence via `nvim-jdtls`, Kotlin Language Server, and Metals.
- **Spring Boot Ecosystem**: Deep integration using existing Neovim Spring Boot tools and DAP.
- **Diagnostics & Testing**: Native Neovim diagnostic displays, `nvim-dap` for debugging, and test execution plugins (like `neotest`).
- **DevOps & Cloud**: Flyway migrations, Kubernetes, and Docker support through standard LSPs.

---

## Installation

### Quick Shell Bootstrap

```bash
git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim
cd ~/.config/nvim
./bootstrap.sh
```

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
