# TetraVim Installation Scripts

## Quick Reference

### For Development (Inside the Repo)
```bash
bash scripts/dev-init.sh
```
- Just symlinks config + syncs plugins
- Lightweight & fast
- Perfect for editing lua/
- No system dependencies checked
- No engine installation

### For Production Users
```bash
bash bootstrap.sh
cn install
```
- Full system setup
- Installs dependencies (git, nvim, java, ripgrep)
- Builds/downloads tetravim-engine
- Complete verification

---

## Script Details

### dev-init.sh
**Purpose:** Lightweight development setup

**What it does:**
1. Checks Neovim is installed
2. Symlinks `~/.config/nvim` → repo
3. Syncs plugins with Lazy
4. Done

**Time:** ~30 seconds (+ plugin sync time)

**When to use:**
- You're a contributor working on the repo
- You want fast iteration cycles
- You don't need the engine for development
- You already have nvim installed

**Example:**
```bash
git clone https://github.com/petrolal/tetravim.nvim.git
cd tetravim.nvim
bash scripts/dev-init.sh
nvim
```

---

### bootstrap.sh
**Purpose:** Guided installation for end users

**What it does (when in repo):**
1. Detects you're in a repo
2. Offers choice: dev-init or full install
3. If full install chosen, guides to `cn install`

**What it does (when cloned as user):**
1. Creates `cn` launcher
2. Updates shell PATH
3. Guides to `cn install`

**Time:** ~5 seconds

---

### install-cn.sh
**Purpose:** Complete installation for production

**What it does:**
1. Checks/installs system dependencies
2. Builds/downloads tetravim-engine
3. Symlinks `~/.config/nvim` → repo
4. Syncs plugins
5. Verifies health

**Time:** 5-15 minutes

**When to use:**
- You're an end user (not in repo)
- You want everything set up automatically
- You need the engine working
- You want full verification

---

### install.sh
**Purpose:** Legacy wrapper for backwards compatibility

Just delegates to `install-cn.sh`.

---

## Decision Tree

```
Are you a developer working on tetravim.nvim?
├─ YES → bash scripts/dev-init.sh
└─ NO (end user)
   ├─ In the repo? → bash bootstrap.sh
   └─ Cloned elsewhere? → bash bootstrap.sh, then cn install
```

---

## Development Workflow

After `bash scripts/dev-init.sh`:

```bash
nvim                          # Launch with symlinked config
# Edit lua files
:source /path/to/init.lua    # Reload config
```

All changes to lua/ appear immediately since it's a symlink.

---

## Troubleshooting

### dev-init.sh says "Neovim not found"
Install Neovim first:
```bash
brew install neovim        # macOS
sudo apt install neovim    # Ubuntu
sudo pacman -S neovim      # Arch
```

### Want to switch from dev to full install
```bash
rm ~/.config/nvim
bash scripts/install-cn.sh
```

### Want to switch from full to dev
```bash
rm ~/.config/nvim
bash scripts/dev-init.sh
```

---

## Script Comparison

| Feature | dev-init.sh | install-cn.sh |
|---------|------------|--------------|
| Symlink config | ✓ | ✓ |
| Sync plugins | ✓ | ✓ |
| Check deps | ✗ | ✓ |
| Install deps | ✗ | ✓ |
| Build/download engine | ✗ | ✓ |
| Health verification | ✗ | ✓ |
| Time | ~30s | 5-15m |

---

## For CI/CD

For automated testing:
```bash
bash scripts/dev-init.sh  # Fast, just config
# Run tests
```

For distribution builds:
```bash
bash scripts/install-cn.sh  # Full setup
# Build artifacts
```
