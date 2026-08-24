# Cumulus CLI Architecture

## Overview

The `cn` command is now a **proper Scala CLI application** distributed via Coursier, matching the cumulus.dotfiles architecture pattern.

```
User runs: bash bootstrap.sh
              ↓
Detects Coursier
    ↓              ↓
  YES            NO
    ↓              ↓
cs install    Shell script
(JAR)         launcher
    ↓              ↓
~/.local/bin/cn created
    ↓
cn install  (runs install-cn.sh)
    ↓
Cumulus setup complete
```

---

## Three-Tier Architecture

### Tier 1: Build System (sbt)
```
engine/build.sbt
├── cumulus-engine  (Scala intelligence engine)
└── cumulus-cli     (CLI application) 
```

**Files:**
- `engine/build.sbt` - Multi-module build definition
- `engine/project/plugins.sbt` - Includes `sbt-assembly` for JAR creation
- `engine/cumulus-cli/src/main/scala/cumulus/cli/CumulusCli.scala` - CLI source

**Build command:**
```bash
sbt "cli/assembly"
# Output: engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar (7.5MB)
```

### Tier 2: Distribution (Coursier)
```
Coursier ("cs" command)
├── Installs JAR to ~/.local/bin
├── Verifies main class: cumulus.cli.CumulusCli
└── Creates wrapper script: ~/.local/bin/cn
```

**Installation:**
```bash
# Via Coursier (if available)
cs install file://./engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar \
  --main-class cumulus.cli.CumulusCli \
  --output cn \
  --directory ~/.local/bin

# Or via shell script (fallback)
bash bootstrap.sh
```

### Tier 3: Usage (CLI Commands)
```
cn [command] [args]
├── cn install       → Runs scripts/install-cn.sh
├── cn update        → Same as install
├── cn --version     → Show version
├── cn --help        → Show help
└── cn [args]        → Pass through to nvim
```

---

## File Organization

```
cumulus.nvim/
├── bootstrap.sh                      ← Entry point (detects Coursier)
├── COURSIER_CLI.md                   ← Coursier setup guide
├── CLI_ARCHITECTURE.md               ← This file
├── scripts/
│   ├── dev-init.sh                   ← Lightweight dev setup
│   ├── install-cn.sh                 ← Main installer
│   └── README.md                     ← Script reference
├── engine/
│   ├── build.sbt                     ← Multi-module build
│   ├── cumulus-cli/                  ← CLI module
│   │   ├── src/main/scala/cumulus/cli/
│   │   │   └── CumulusCli.scala      ← CLI implementation
│   │   ├── coursier.json             ← Metadata
│   │   └── target/scala-3.5.2/
│   │       └── cumulus-cli.jar       ← Built artifact (7.5MB)
│   ├── project/
│   │   └── plugins.sbt               ← sbt-assembly plugin
│   └── ... (cumulus-engine module)
└── .github/workflows/
    └── publish-cli.yml               ← GitHub Actions CI/CD
```

---

## How It Works

### Step 1: User runs bootstrap
```bash
bash bootstrap.sh
```

**bootstrap.sh logic:**
1. Check if running in repo (offers dev-init vs full install)
2. If not in repo, proceed with full installation
3. Check if Coursier is available:
   - **YES:** Build JAR locally, install via `cs install`
   - **NO:** Create shell script launcher

### Step 2: Coursier installation (if available)
```bash
# bootstrap.sh automatically runs:
sbt "cli/assembly"  # Build JAR

cs install \
  --force \
  --directory ~/.local/bin \
  file://.../cumulus-cli.jar \
  --main-class cumulus.cli.CumulusCli \
  --output cn
```

**Result:** 
- `~/.local/bin/cn` → Coursier-wrapped JAR launcher
- Executable with all dependencies bundled

### Step 3: User runs cn install
```bash
cn install
```

**What happens:**
1. `cn` JAR starts
2. CumulusCli.main() is called
3. Detects "install" command
4. Finds Cumulus repo at `~/.config/nvim`
5. Executes `bash ~/.config/nvim/scripts/install-cn.sh`
6. Installation proceeds normally

### Step 4: User launches Cumulus
```bash
nvim
# Or
cn
```

Both work identically (cn is just a wrapper around nvim).

---

## CLI Source Code Overview

### Main Class: `CumulusCli`
```scala
package cumulus.cli

object CumulusCli:
  def main(args: Array[String]): Unit =
    args.headOption match
      case Some("install") => installCumulus()  // Run installer
      case Some("update")  => updateCumulus()   // Same as install
      case Some("--help")  => printHelp()       // Show usage
      case Some("--version") => printVersion()  // Show version
      case Some(cmd) if cmd.startsWith("-") =>
        launchNvim(args)                        // Pass to nvim
      case Some(_) => launchNvim(args)          // Pass to nvim
      case None => launchNvim(Array.empty)      // Just launch nvim
```

### Key Methods
- `installCumulus()` - Locates repo, runs install-cn.sh
- `updateCumulus()` - Same as install (idempotent)
- `launchNvim(args)` - Launch nvim with args via os.proc
- `findCumulusRepo()` - Find ~/.config/nvim symlink
- `printHelp()` / `printVersion()` - Display info

---

## Comparison: Old vs New

| Aspect | Old | New |
|--------|-----|-----|
| **CLI Implementation** | Shell script | Scala JVM app |
| **Distribution** | Embedded in bootstrap.sh | Coursier + JAR |
| **Binary Size** | ~1KB | 7.5MB (includes JVM classes) |
| **Installation** | Copy shell script | cs install or fallback |
| **Startup** | Instant (~50ms) | ~1-2s (JVM startup) |
| **Maintainability** | Low (shell) | High (Scala) |
| **Pattern Match** | Custom | Matches cumulus.dotfiles |

---

## Deployment Flow

### For Development
```bash
git clone https://github.com/petrolal/cumulus.nvim.git
cd cumulus.nvim
bash scripts/dev-init.sh        # 30 seconds
nvim                             # Start editing
```

No CLI needed for development.

### For End Users
```bash
# Clone
git clone https://github.com/petrolal/cumulus.nvim.git

# Install (either method works)
cd cumulus.nvim
bash bootstrap.sh

# Then
cn install                       # 5-15 minutes

# Done
nvim
```

### For GitHub Actions (Future)
```yaml
- name: Build CLI
  run: sbt "cli/assembly"

- name: Create Release
  uses: softprops/action-gh-release@v1
  with:
    files: engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar
```

---

## Performance Characteristics

### JAR Size
```
cumulus-cli.jar:  7.5MB
  ├── Scala runtime (2.5MB)
  ├── JVM classes (3MB)
  ├── os-lib library (800KB)
  └── CLI code (200KB)
```

### Startup Time
```
Shell script:    ~50ms
Java JAR:        ~1-2s (JVM warmup)
```

**Note:** Startup time only matters for `cn install`. Regular `nvim` (without cn wrapper) launches normally.

### Memory Usage
```
JVM heap:        100-150MB (after startup)
Scala runtime:   Minimal (no reflection)
```

---

## Future Enhancements

### 1. Native Image (GraalVM)
Currently the engine uses GraalVM native image for speed. CLI could too:
```bash
sbt "cli/nativeImage"
# Output: binary without JVM startup overhead
```

### 2. Maven Central Publishing
```bash
sbt "cli/publishSigned"
# Users: cs install petrolal/cumulus-cli
```

### 3. Enhanced CLI Features
- `cn doctor` - Health diagnostics
- `cn plugins` - Plugin management
- `cn config` - Configuration helper
- `cn version` - Check for updates

### 4. Shebang Support
```bash
#!/usr/bin/env cn
# Script inside nvim config
```

---

## Architecture Decision Rationale

### Why Scala?
- ✅ Already used for the engine
- ✅ Compiles to portable JVM bytecode
- ✅ Type-safe, expressive
- ✅ Can eventually use GraalVM native image
- ✅ Matches cumulus.dotfiles philosophy

### Why Coursier?
- ✅ Follows cumulus.dotfiles pattern
- ✅ Automatic dependency resolution
- ✅ Works across all platforms (JVM-based)
- ✅ Version management built-in
- ✅ Supports Maven Central publishing

### Why Fallback to Shell Script?
- ✅ Some users may not have Coursier
- ✅ Shell script is always available
- ✅ Maintains backwards compatibility
- ✅ Lightweight fallback option

---

## Related Documentation

- **[COURSIER_CLI.md](./COURSIER_CLI.md)** - Coursier setup and publishing
- **[INSTALL.md](./INSTALL.md)** - User installation guide
- **[INSTALLATION_FLOW.md](./INSTALLATION_FLOW.md)** - Install architecture
- **[scripts/README.md](./scripts/README.md)** - Script reference
- **[CLAUDE.md](./CLAUDE.md)** - Project guidelines

---

## Quick Reference

```bash
# Build CLI JAR
sbt "cli/assembly"

# Test locally
java -jar engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar --help

# Install via Coursier (manual)
cs install \
  --force \
  --directory ~/.local/bin \
  file://path/to/cumulus-cli.jar \
  --main-class cumulus.cli.CumulusCli \
  --output cn

# Test installation
cn --version
cn --help

# Run full installation
cn install
```

---

## Troubleshooting

### JAR won't build
```bash
cd engine
sbt clean
sbt "cli/assembly"
```

### cs install fails
```bash
# Fallback to shell script
bash bootstrap.sh  # Uses fallback automatically
```

### CLI not in PATH after bootstrap
```bash
# Reload shell
source ~/.bashrc
# Or restart terminal
```

---

**Last updated:** 2026-08-24
**CLI Version:** 0.1.0
**Status:** Ready for beta testing
