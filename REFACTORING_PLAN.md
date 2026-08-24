# Cumulus: Architecture Refactoring Plan

## Vision

**Cumulus: IntelliJ-Parity IDE for JVM Languages & Cloud-Native Development**

Cumulus Neovim is not just an editor—it's a complete IDE replacement for:
- ☕ Java development
- 🎯 Kotlin/JVM languages
- ☁️ Cloud-native projects (Kubernetes, Docker, Terraform)
- 🔍 Polyglot backend engineering
- 📊 LSP-based intelligence across languages

**One-shot installer philosophy:** Users should go from zero to full IDE in ONE command.

---

## Goal

**Complete separation of concerns with seamless user experience:**
- `cumulus.dotfiles` = **Backend System** (cumulus-lsp: JVM intelligence engine, manages everything)
- `cumulus.nvim` = **Frontend IDE** (Neovim UI/config layer, pure IDE focused)
- **Installation:** Single command, automatic setup, zero manual configuration

---

## Architecture After Refactoring

```
cumulus.dotfiles/  ────────────────────────────────────────┐
                                                            │
├── cumulus-lsp/           ← LSP engine (moved from nvim)  │
│   ├── src/main/scala/    │                               │
│   │   ├── cumulus/       │                               │
│   │   │   ├── lsp/       ├─ JVM Intelligence            │
│   │   │   ├── engine/    │  (Reusable for other tools)  │
│   │   │   └── ...        │                               │
│   │   └── Main.scala     │                               │
│   ├── build.sbt          │                               │
│   └── nativeImage/       │                               │
│                          │                               │
├── cumulus-cli/           ├─ Main CLI dispatcher          │
│   └── src/main/scala/    │  (cumulus nvim setup, etc)   │
│       └── cumulus/cli/   │                               │
│                          │                               │
├── config/nvim/           ├─ Shared Neovim config        │
│   ├── init.lua           │  (all projects read this)    │
│   └── lua/               │                               │
│                          │                               │
├── scripts/               │                               │
│   ├── build-lsp.sh       ├─ Build & install scripts     │
│   └── install.sh         │                               │
│                          │                               │
└── bootstrap.sh           ├─ System entry point          │
                          │  (builds cumulus-lsp first)
                          │
└─ BACKEND COMPLETE ──────────────────────────────────────┘

~/.cumulus/                 ← Shared configuration root
├── bin/
│   └── cumulus-lsp        ← Compiled LSP engine
├── config/nvim/           ← Init + config (shared)
└── config/sway/           ← Desktop config

cumulus.nvim/  ────────────────────────────────────────────┐
                                                            │
├── lua/                   ← UI layer only                 │
│   ├── cumulus/           │                               │
│   │   ├── plugins/       ├─ Configuration & UI           │
│   │   ├── config/        │  (Neovim keybindings,        │
│   │   └── util/          │   plugin specs, etc)          │
│   └── user/              │                               │
│                          │
├── init.lua               ├─ Entry point                  │
│                          │ (loads from ~/.cumulus/)      │
│                          │
├── bootstrap.sh           ├─ Links to dotfiles LSP       │
│                          │ (no build logic)             │
│                          │
└── scripts/               │                               │
    └── dev-init.sh        ├─ Dev setup only              │
                          │  (already uses dotfiles)
                          │
└─ FRONTEND COMPLETE ─────────────────────────────────────┘
```

---

## Dependency Flow

```
User runs: cumulus install
           ↓
    Build cumulus-lsp (backend)
           ↓
    Setup config in ~/.cumulus/
           ↓
    Link cumulus.nvim → ~/.cumulus/
           ↓
    READY

User runs: cumulus nvim
           ↓
    Neovim loads init.lua (from ~/.cumulus/)
           ↓
    Lua plugins load cumulus-lsp (from ~/.cumulus/bin/)
           ↓
    LSP engine provides intelligence
           ↓
    NVIM RUNNING
```

---

## Changes Required

### 1. cumulus.dotfiles

**Move engine → cumulus-lsp:**
```bash
mv engine/ cumulus-lsp/
```

**Update build.sbt:**
```scala
// OLD
lazy val root = (project in file("."))

// NEW
lazy val cumulusLsp = (project in file("cumulus-lsp"))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "cumulus-lsp",
    Compile / mainClass := Some("cumulus.lsp.Main"),
    // ... rest of config
  )

lazy val cumulusCli = (project in file("cumulus-cli"))
  .settings(
    name := "cumulus-cli",
    Compile / mainClass := Some("cumulus.cli.CumulusCli"),
  )

lazy val root = (project in file("root"))
  .aggregate(cumulusLsp, cumulusCli)
  .settings(publish / skip := true)
```

**Update Main.scala dispatcher:**
```scala
// Add to dispatchModule function:
case "nvim" => handleNvimSubcommand(args)

private def handleNvimSubcommand(args: List[String]): Either[CumulusError, Unit] =
  args match
    case "setup" :: rest => NvimManager.setup(ctx, rest)
    case "sync" :: rest => NvimManager.sync(ctx, rest)
    case "status" :: rest => NvimManager.status(ctx, rest)
    case rest => NvimManager.launch(ctx, rest)
```

**Create NvimManager module:**
```scala
// src/main/scala/cumulus/dotfiles/nvim/NvimManager.scala
package cumulus.dotfiles.nvim

object NvimManager:
  def setup(ctx: Context, args: List[String]): Either[CumulusError, Unit] = {
    // Run scripts/install-cn.sh from cumulus.nvim
    // (which links to ~/.cumulus/config/nvim/)
  }
  
  def sync(ctx: Context, args: List[String]): Either[CumulusError, Unit] = {
    // Same as setup
  }
  
  def status(ctx: Context, args: List[String]): Either[CumulusError, Unit] = {
    // Check if cumulus.nvim is installed
    // Check if LSP engine is available
  }
  
  def launch(ctx: Context, args: List[String]): Either[CumulusError, Unit] = {
    // Launch nvim with cumulus config
  }
```

**Update bootstrap.sh:**
```bash
# OLD: Full installation
bash scripts/install-cn.sh

# NEW: 
# 1. Build cumulus-lsp first
sbt "cumulusLsp/nativeImage"

# 2. Install cumulus-lsp to ~/.cumulus/bin/
mkdir -p ~/.cumulus/bin/
cp cumulus-lsp/target/release/cumulus-lsp ~/.cumulus/bin/

# 3. Link config
mkdir -p ~/.cumulus/config/nvim
cp -r config/nvim/* ~/.cumulus/config/nvim/

# 4. Setup ~/.cumulus paths
export PATH="$HOME/.cumulus/bin:$PATH"
```

---

### 2. cumulus.nvim

**Remove engine directory:**
```bash
rm -rf engine/
rm -rf engine-build-scripts/
```

**Remove from bootstrap.sh:**
```bash
# DELETE:
# - Any engine building
# - Any sbt calls
# - Any nativeImage compilation

# KEEP:
# - Config linking to ~/.cumulus/
# - Plugin syncing
# - Dev setup (dev-init.sh)
```

**Update init.lua:**
```lua
-- ~/.cumulus/config/nvim/init.lua (shared location)

-- Load plugins from shared location
require("cumulus.plugins")

-- LSP is now provided by cumulus-lsp (from dotfiles)
-- It's available via PATH at ~/.cumulus/bin/cumulus-lsp
-- Plugins will auto-detect and use it

-- No engine-specific code here
```

**Simplify bootstrap.sh:**
```bash
#!/usr/bin/env bash
# cumulus.nvim bootstrap
# LIGHTWEIGHT - just links config

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Verify cumulus.dotfiles is installed
if [ ! -d ~/.cumulus ] || [ ! -f ~/.cumulus/bin/cumulus-lsp ]; then
  echo "✖ cumulus-lsp not found"
  echo "Install cumulus.dotfiles first: git clone cumulus.dotfiles"
  echo "Then run: bash cumulus.dotfiles/bootstrap.sh"
  exit 1
fi

# Link config
ln -sf ~/.cumulus/config/nvim ~/.config/nvim

# Done!
echo "✔ Cumulus.nvim configured"
echo "✔ LSP engine available at ~/.cumulus/bin/cumulus-lsp"
echo "Run: nvim"
```

**Remove engine/ from plugins:**
```lua
-- Before:
{
  "petrolal/cumulus-engine",  -- REMOVE
  -- ...
}

-- After:
{
  -- Engine now provided externally
  -- Plugins just use it if available
  -- ...
}
```

**Remove engine build from scripts/:**
```bash
rm -rf scripts/build-engine.sh
rm -rf scripts/install-engine.sh
# Keep only dev-init.sh and other non-engine scripts
```

---

## File Structure After

```
cumulus.dotfiles/
├── cumulus-lsp/              ← MOVED from nvim/engine
│   ├── src/main/scala/cumulus/lsp/
│   │   ├── Main.scala
│   │   ├── LspResolver.scala
│   │   ├── LspCapabilityDetector.scala
│   │   └── ...
│   ├── build.sbt
│   ├── project/plugins.sbt
│   └── ...
├── cumulus-cli/              ← Already here
├── src/main/scala/cumulus/
│   └── dotfiles/nvim/NvimManager.scala  ← NEW
├── config/nvim/              ← Config directory
└── build.sbt                 ← Updated

cumulus.nvim/
├── lua/                      ← ONLY UI config
│   └── cumulus/
│       ├── plugins/
│       ├── config/
│       └── util/
├── init.lua
├── bootstrap.sh              ← SIMPLIFIED
├── scripts/
│   └── dev-init.sh
└── REFACTORED: NO engine/
```

---

## Deduplication

### Files to move to cumulus-lsp:

```
OLD: cumulus.nvim/engine/src/main/scala/cumulus/
├── lsp/LspResolver.scala          ← MOVE
├── lsp/LspCapabilityDetector.scala ← MOVE
├── lsp/TreeSitterResolver.scala    ← MOVE
└── ... (all engine code)

NEW: cumulus.dotfiles/cumulus-lsp/src/main/scala/cumulus/lsp/
├── LspResolver.scala
├── LspCapabilityDetector.scala
├── TreeSitterResolver.scala
└── ... (all engine code, no duplication)
```

### Shared utilities:

```
cumulus.dotfiles/cumulus-lsp/src/main/scala/cumulus/util/
├── FileOps.scala       ← Shared file operations
├── ProcessOps.scala    ← Shared process operations
└── ConfigOps.scala     ← Shared config operations

(Used by both cumulus-lsp and cumulus-cli)
```

---

## Performance Improvements

### 1. Lazy Loading
```lua
-- plugins.lua
-- Load LSP only when needed
{
  "nvim-neorg/neorg",
  config = function()
    -- LSP auto-detected from ~/.cumulus/bin/cumulus-lsp
    -- Lazy loads on first use
  end,
}
```

### 2. Binary Caching
```bash
# cumulus-lsp compiled once
~/.cumulus/bin/cumulus-lsp  ← Single native image
# Reused by all nvim instances
```

### 3. Plugin Startup
```lua
-- Lazy initialization
require("cumulus.lsp").init_if_available()
-- Only initializes if ~/.cumulus/bin/cumulus-lsp exists
```

---

## Installation Steps (After Refactoring)

### ONE-SHOT INSTALLATION (Recommended)

**User runs ONE command, gets everything:**

```bash
git clone https://github.com/petrolal/cumulus.dotfiles.git
cd cumulus.dotfiles
bash bootstrap.sh

# This AUTOMATICALLY:
# 1. ✅ Builds cumulus-lsp (JVM intelligence engine)
# 2. ✅ Clones cumulus.nvim frontend (if not present)
# 3. ✅ Creates ~/.cumulus/ shared config directory
# 4. ✅ Links cumulus.nvim → ~/.cumulus/
# 5. ✅ Links ~/.config/nvim → ~/.cumulus/config/nvim/
# 6. ✅ Syncs Neovim plugins
# 7. ✅ Configures IDE for JVM/Cloud development
# 8. ✅ Runs health check
# 9. ✅ DONE!

cumulus install
# Full system setup (including nvim)

nvim
# ✨ Full IDE with JVM intelligence ready!
```

**What `bash bootstrap.sh` does internally:**

```bash
#!/usr/bin/env bash
# cumulus.dotfiles bootstrap - ONE-SHOT INSTALLER

# 1. Build cumulus-lsp (backend engine)
echo "[1/8] Building cumulus-lsp JVM engine..."
sbt "cumulusLsp/nativeImage"
mkdir -p ~/.cumulus/bin/
cp cumulus-lsp/target/release/cumulus-lsp ~/.cumulus/bin/

# 2. Clone cumulus.nvim frontend
echo "[2/8] Setting up Cumulus Neovim IDE..."
if [ ! -d ~/.config/nvim ] || [ -z "$(ls -A ~/.config/nvim)" ]; then
  git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim
else
  echo "✔ cumulus.nvim already present"
fi

# 3. Create shared config root
echo "[3/8] Creating shared configuration..."
mkdir -p ~/.cumulus/config/nvim
mkdir -p ~/.cumulus/config/sway
mkdir -p ~/.cumulus/config/themes

# 4. Link cumulus.nvim → ~/.cumulus/
echo "[4/8] Linking Neovim configuration..."
ln -sf ~/.cumulus/config/nvim ~/.config/nvim

# 5. Copy Neovim config to ~/.cumulus/
echo "[5/8] Installing Neovim configuration..."
cp -r ~/.config/nvim/lua/cumulus/* ~/.cumulus/config/nvim/lua/
cp ~/.config/nvim/init.lua ~/.cumulus/config/nvim/

# 6. Link Neovim → shared config
ln -sf ~/.cumulus/config/nvim ~/.config/nvim

# 7. Sync plugins
echo "[6/8] Syncing Neovim plugins..."
nvim --headless "+Lazy! sync" +qa

# 8. Setup Sway config
echo "[7/8] Setting up desktop configuration..."
cp -r config/sway ~/.cumulus/config/
ln -sf ~/.cumulus/config/sway ~/.config/sway

# 9. Verify
echo "[8/8] Running health check..."
nvim --headless "+checkhealth cumulus" +qa

echo ""
echo "════════════════════════════════════════════════════"
echo "  ✨ Cumulus IDE Ready!"
echo "════════════════════════════════════════════════════"
echo ""
echo "IDE Features:"
echo "  ✓ JVM Intelligence Engine (cumulus-lsp)"
echo "  ✓ Java/Kotlin/Scala Development"
echo "  ✓ Cloud-Native Support"
echo "  ✓ LSP-based Code Intelligence"
echo "  ✓ IntelliJ Parity Features"
echo ""
echo "Launch:"
echo "  nvim              # Start IDE"
echo "  cumulus nvim      # Launch with full setup"
echo "  cumulus install   # Update everything"
echo ""
```

### Step-by-Step (Manual, if needed)

**Step 1: Install cumulus.dotfiles with cumulus-lsp**

```bash
git clone https://github.com/petrolal/cumulus.dotfiles.git
cd cumulus.dotfiles
bash bootstrap.sh
# Builds cumulus-lsp, sets up ~/. cumulus/, installs nvim frontend

cumulus install
# Full system setup
```

**Result:** Everything is installed and linked automatically.

### For macOS (Future)

```bash
# On macOS (when ready)
git clone https://github.com/petrolal/cumulus.dotfiles.git
cd cumulus.dotfiles
bash bootstrap.sh

# This will:
# - Skip Sway/Linux desktop setup
# - Install cumulus-lsp (or download macOS version)
# - Setup cumulus.nvim IDE
# - Ready for JVM development on macOS
```

---

## One-Shot Installer Features

The `bash bootstrap.sh` in cumulus.dotfiles handles **everything automatically**:

| Step | What It Does | Result |
|------|-------------|--------|
| 1 | Build cumulus-lsp | JVM intelligence engine compiled |
| 2 | Clone cumulus.nvim | Frontend downloaded to ~/.config/nvim |
| 3 | Create ~/.cumulus/ | Shared config directory |
| 4-5 | Setup symlinks | All projects linked to ~/.cumulus/ |
| 6 | Sync plugins | Lazy.nvim plugins installed |
| 7 | Configure IDE | IDE settings for JVM development |
| 8 | Health check | Verify everything works |

**Result:** User goes from `git clone` → `bash bootstrap.sh` → `nvim` (full IDE ready)

### IDE Features Enabled by Default

After one-shot installation, user has:
- ✅ LSP for Java/Kotlin/Scala/Groovy
- ✅ Code completion (powered by cumulus-lsp)
- ✅ Error diagnostics
- ✅ Symbol navigation
- ✅ Refactoring tools
- ✅ Cloud tools integration
- ✅ Custom Cumulus keybindings
- ✅ IntelliJ-familiar workflow

### No Manual Configuration

**User doesn't need to:**
- [ ] Clone cumulus.nvim separately ❌ (bootstrap does it)
- [ ] Build engine manually ❌ (bootstrap builds it)
- [ ] Create symlinks ❌ (bootstrap creates them)
- [ ] Setup config directories ❌ (bootstrap sets them up)
- [ ] Sync plugins ❌ (bootstrap syncs them)
- [ ] Configure environment variables ❌ (bootstrap configures them)

**Everything is automated in one command:**
```bash
bash bootstrap.sh
```

---

## Benefits of This Refactoring

| Aspect | Before | After |
|--------|--------|-------|
| **Engine location** | cumulus.nvim | cumulus.dotfiles |
| **Duplication** | Engine tied to nvim | Reusable cumulus-lsp |
| **cumulus.nvim size** | Large (includes engine) | Small (config only) |
| **Dependency** | Independent | Depends on dotfiles |
| **macOS support** | Complex (move engine) | Simple (different LSP) |
| **Backend reuse** | Nvim-only | Any tool can use it |
| **Build time** | Slow (includes engine) | Fast (config only) |
| **Responsibility** | Mixed | Clear separation |

---

## Migration Checklist

### cumulus.dotfiles
- [ ] Move engine/ → cumulus-lsp/
- [ ] Update build.sbt with cumulus-lsp module
- [ ] Update Main.scala to add NvimManager
- [ ] Create cumulus/dotfiles/nvim/NvimManager.scala
- [ ] Update bootstrap.sh to build cumulus-lsp
- [ ] Update scripts/ to use cumulus-lsp
- [ ] Test: sbt build completes
- [ ] Test: cumulus install works
- [ ] Test: cumulus nvim setup works

### cumulus.nvim
- [ ] Remove engine/ directory
- [ ] Remove engine build scripts
- [ ] Simplify bootstrap.sh (no build)
- [ ] Update init.lua (no engine refs)
- [ ] Verify plugins don't reference engine
- [ ] Add check for ~/.cumulus/bin/cumulus-lsp
- [ ] Test: nvim starts with dotfiles installed
- [ ] Test: LSP works (via dotfiles cumulus-lsp)

### Testing
- [ ] Clean install from both repos
- [ ] cumulus nvim setup works
- [ ] Neovim launches and LSP available
- [ ] Code completion works
- [ ] Theme switching works
- [ ] Config changes apply immediately

---

## Timeline

**Phase 1: Planning** ✅ (This document)

**Phase 2: Implementation**
1. Move engine to cumulus-lsp
2. Update build systems
3. Update bootstrap scripts
4. Test basic functionality

**Phase 3: Verification**
1. Clean install test
2. Multi-project integration test
3. Performance benchmarking
4. Documentation update

---

## Future Extensions

Once cumulus-lsp is decoupled:

```
cumulus-lsp/  (Reusable JVM intelligence)
├── Helix editor integration
├── Kakoune editor integration
├── Standalone LSP server
├── VS Code LSP bridge
└── Other tools that need JVM intelligence
```

---

**Status:** ✅ Plan complete, ready for implementation  
**Complexity:** High (multi-project refactor)  
**Benefit:** Clean architecture, reusable engine, smaller nvim project
