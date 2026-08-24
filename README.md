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

## Migration Guide: Epic 4 Breaking Changes (v2026-08-24)

As part of Epic 4 (Engine IPC Cleanup), several UI action functions were removed from the engine bridge (`lua/cumulus/util/engine.lua`) and moved to the plugin layer. This maintains clean architecture where the engine bridge is pure IPC marshaling only.

### Removed Functions

The following functions are **no longer available** in the engine module:
- `M.select_bean()` — Interactive Spring Bean picker
- `M.select_endpoint()` — Interactive REST endpoint picker
- `M.optimize_imports_buffer()` — Optimize imports in current buffer
- `M.validate_k8s_manifest_buffer()` — Validate K8s YAML in current buffer
- `M.validate_migrations_action()` — Validate Flyway migrations
- `M.resolve_git_conflicts()` — Interactive Git conflict resolver
- `M.view_coverage()` — Load and display JaCoCo coverage
- `M.search_indexed_logs()` — Search and jump to log entries
- `M.populate_build_diagnostics()` — Parse build logs and populate diagnostics
- `M.clear_build_diagnostics()` — Clear build diagnostics

### Migration Strategy

If you have custom keybindings or commands that call these removed functions, you can recreate them in your Lazy.nvim config by overriding plugins. Here are three patterns:

#### Pattern 1: Register in a Custom Plugin Module

Create a file `lua/cumulus/plugins/custom-ui.lua`:

```lua
return {
  {
    "petrolal/cumulus.nvim",
    config = function()
      local engine = require("cumulus.util.engine")
      local ui = require("cumulus.util.ui")
      
      -- Recreate select_bean with custom UI logic
      local function select_bean()
        if not engine.is_available() then return end
        local beans = engine.parse_spring_beans(vim.fn.getcwd())
        if not beans or #beans == 0 then
          ui.notify_warn("No Spring beans found")
          return
        end
        vim.ui.select(beans, {
          prompt = "Select Spring Bean:",
          format_item = function(item)
            return string.format("%s (%s)", item.bean_name, item.class_name)
          end,
        }, function(choice)
          if choice and choice.file then
            vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
            vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
          end
        end)
      end
      
      vim.keymap.set("n", "<leader>jsb", select_bean, { desc = "Select Spring Bean" })
    end,
  }
}
```

#### Pattern 2: Direct Keybinding Override

In your Lazy.nvim plugin spec:

```lua
{
  "petrolal/cumulus.nvim",
  config = function()
    local engine = require("cumulus.util.engine")
    local ui = require("cumulus.util.ui")
    
    vim.keymap.set("n", "<leader>jse", function()
      if not engine.is_available() then return end
      local eps = engine.extract_endpoints(vim.fn.getcwd())
      if not eps or #eps == 0 then
        ui.notify_warn("No endpoints found")
        return
      end
      vim.ui.select(eps, {
        prompt = "REST Endpoints:",
        format_item = function(item)
          return string.format("[%s] %s", item.http_method, item.path)
        end,
      }, function(choice)
        if choice and choice.file then
          vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
          vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
        end
      end)
    end, { desc = "Select REST Endpoint" })
  end,
}
```

#### Pattern 3: Use Lower-Level Engine Wrappers

The core engine wrappers are still available:
- `engine.parse_spring_beans(dir)` — Get Spring beans for a directory
- `engine.extract_endpoints(dir)` — Get REST endpoints
- `engine.optimize_imports(code)` — Optimize imports (returns modified lines)
- `engine.validate_k8s_manifest(yaml_content)` — Validate K8s YAML
- `engine.validate_migrations(dir)` — Validate Flyway migrations
- `engine.parse_git_conflicts(content)` — Parse Git conflict markers
- `engine.parse_coverage(xml_path)` — Load JaCoCo XML report
- `engine.index_log(content)` — Index log lines

Build your own UI logic using these wrappers:

```lua
local engine = require("cumulus.util.engine")

local function my_custom_bean_picker()
  local beans = engine.parse_spring_beans(vim.fn.getcwd())
  -- ... your custom UI code here
end
```

### Notification API Change

Notification functions are now in a separate module to avoid polluting the IPC bridge:

**Before (removed):**
```lua
local engine = require("cumulus.util.engine")
engine.notify_info("Message")
engine.notify_err("Error")
engine.run_term("make build")
```

**After (use `cumulus.util.ui`):**
```lua
local ui = require("cumulus.util.ui")
ui.notify_info("Message")
ui.notify_err("Error")
ui.run_term("make build")
```

### Snacks Terminal Plugin

Terminal commands require the [Snacks](https://github.com/folke/snacks.nvim) plugin to be installed. Verify your Lazy.nvim plugin manager includes:

```lua
{
  "folke/snacks.nvim",
  opts = {
    terminal = { enabled = true }
  }
}
```

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
