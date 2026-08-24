# cumulus.nvim

> Polyglot JVM intelligence engine and opinionated Neovim distribution for modern backend engineering (Java, Kotlin, Scala, Go, Python, and Cloud Native development).

Built in **Lua + Scala 3 & GraalVM Native Image** to pair with [`cumulus.dotfiles`](https://github.com/petrolal/cumulus.dotfiles).

---

## Contents

```
bootstrap.sh                      # System package & prerequisite installer (with SDKMAN support)
engine/                           # Scala 3 GraalVM Native engine (cumulus-engine)
  ├── build.sbt                   # SBT build configuration & Sonatype Maven Central publishing
  └── src/                        # Subcommand analyzers (POM, Gradle DAG, Spring, K8s, Logs, etc.)
lua/cumulus/                      # Neovim configuration core & plugins
  ├── core/                       # Options, keymaps, autocmds, user commands (:CumulusInstallEngine)
  ├── plugins/                    # Lazy.nvim specs (LSP, Blink CMP, Snacks, Treesitter, DAP, etc.)
  ├── theme/                      # Multi-cloud theme provider (AWS, Azure, GCP, OCI)
  └── util/                       # Engine bridge, diagnostic parser, test runner
scripts/
  ├── install.sh                  # Distro setup (plugins sync, engine binary, cn launcher/alias)
  └── validate.sh                 # Headless smoke test & health validator
.github/workflows/
  └── release-engine.yml          # Automated CI/CD for Maven Central & GitHub Releases
```

---

## Installation & Usage

### 1. The Coursier (`cs`) Flow (Recommended)

Install the CLI launcher package from Maven Central via Coursier, then run the installer:

```bash
# Stage 1: Install CLI package via Coursier
cs install io.github.petrolal::cumulus-engine --name cn

# Stage 2: Run distribution installer
cn install
```

---

### 2. Quick Shell Bootstrap (From Clone or Curl)

```bash
git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim
cd ~/.config/nvim

# Runs system package checks, engine setup, plugin sync, and validation
./bootstrap.sh
```

---

### 3. Isolated Appname Setup (Alongside Existing Neovim)

You can run Cumulus Neovim without replacing your primary `~/.config/nvim`:

```bash
git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/cumulus
cd ~/.config/cumulus
bash scripts/install.sh

# Launch Cumulus isolated instance:
cn
# or: NVIM_APPNAME=cumulus nvim
```

---

## How It Works (2-Stage Installation)

```
Stage 1: Coursier CLI
    ↓ cs install io.github.petrolal::cumulus-engine --name cn
Stage 2: Orchestrated Distribution Installer
    ↓ cn install
    ├── 1. System Package Check (apt/pacman/dnf/brew — skips existing & detects SDKMAN)
    ├── 2. Scala Native Engine Setup (compiles locally or downloads prebuilt binary)
    ├── 3. Lazy.nvim Plugin Headless Sync
    ├── 4. Configures 'cn' launcher script (~/.local/bin/cn) and shell alias
    └── 5. Runs healthchecks and smoke validations
```

---

## Scala Native Engine (`cumulus-engine`)

The backend engine is a compiled native binary that handles heavy parsing and background JVM intelligence:

- **Build Systems**: Maven POM parsing, Gradle task extraction, and multi-module DAG topological build order computation.
- **Spring Ecosystem**: Spring Boot application detection, active profile inspection, Bean dependency graph visualization, and REST endpoint extraction (`@GetMapping`, `@PostMapping`, JAX-RS).
- **Diagnostics & Testing**: Build log parser (Maven / Gradle compiler errors with column precision), stack trace symbol resolver, JUnit test output parser, and nearest test context detector.
- **DevOps & QA**: Flyway migration validator, Kubernetes manifest checker, JaCoCo XML code coverage overlay, and Checkstyle reporter.
- **Environment**: Host JDK auto-discovery, JDTLS classpath sync checker, and Git conflict marker parser.

### Requirements

- **Snacks Plugin** (`folke/snacks.nvim`): Required for interactive terminal UI. Cumulus delegates all terminal operations to Snacks without fallback.
- **sha256sum (macOS)**: macOS users must install gnu-coreutils for binary verification:
  ```bash
  brew install coreutils
  ```
  Linux distributions include `sha256sum` by default.

### In-Editor Engine Commands & Keymaps

| Command / Keymap | Action |
|---|---|
| `:CumulusInstallEngine` / `<leader>jid` | Download pre-built `cumulus-engine` binary from GitHub Releases |
| `:checkhealth cumulus` | Run complete healthcheck across Neovim, plugins, and Scala engine |
| `<leader>ct` | Switch Cloud Theme (`aws`, `azure`, `gcp`, `oci`) |
| `<leader>jt` | Run nearest Java/Kotlin test under cursor |
| `<leader>jb` | Build project (Maven / Gradle / SBT) with error diagnostics |
| `<leader>je` | Scan and list REST API endpoints |
| `<leader>js` | Detect Spring Boot application and debug configurations |

---

## Automated Deployment (GitHub Actions)

Releases are published automatically to **Maven Central** and **GitHub Releases** whenever a version tag is pushed:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow ([`.github/workflows/release-engine.yml`](.github/workflows/release-engine.yml)) performs:
1. Unit tests verification across all Scala modules.
2. Maven Central release publication (`sbt ci-release` via Sonatype Central Portal).
3. GraalVM Native Image compilation across multiple architectures:
   - `cumulus-engine-linux-x86_64` (Linux x86_64)
   - `cumulus-engine-linux-aarch64` (Linux ARM64)
   - `cumulus-engine-darwin-arm64` (macOS Apple Silicon)
4. GitHub Release creation with all native binaries and SHA-256 checksums manifest attached.

---

## Local Development & Testing

To test the engine and distribution scripts locally:

```bash
# Run 308 Scala unit tests
cd engine && sbt test

# Test distribution and engine bridge in Neovim headlessly
bash scripts/validate.sh

# Run install command from source
cd engine && sbt "run install"
```

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
