# TetraVim Scripts

## Quick Reference

### For End Users
```bash
bash bootstrap.sh
```
- Syncs all Lazy.nvim plugins headlessly
- Prepares your Neovim environment

### For Contributors & Local Development
```bash
bash scripts/dev-init.sh
```
- Symlinks `~/.config/nvim` → repository
- Syncs plugins with Lazy.nvim
- Fast setup for editing configuration in-place

---

## Validation & Test Suites

The test and smoke verification suite lives in `scripts/validate*.sh`:

- **Full distribution smoke test:**
  ```bash
  bash scripts/validate.sh
  ```
- **Component verification suites:**
  - `bash scripts/validate-2-3.sh`: Spring Boot Discovery (native Tree-sitter & DAP)
  - `bash scripts/validate-refactor.sh`: Safe rename/move refactoring
  - `bash scripts/validate-extract.sh`: Method/variable/interface extraction
  - `bash scripts/validate-db.sh`: Database explorer & datasource auto-discovery
  - `bash scripts/validate-http.sh`: HTTP client & OpenAPI explorer
  - `bash scripts/validate-4-1.sh`: Git 3-way conflict resolution
  - `bash scripts/validate-4-2.sh`: In-editor code reviews (GitHub/GitLab)
  - `bash scripts/validate-dap-jvm.sh`: JVM DAP debugger & breakpoint controls
  - `bash scripts/validate-devops.sh`: DevOps tooling & root discovery guards

- **Plenary Busted specs:**
  ```bash
  nvim --headless -u init.lua -c "Lazy! load plenary.nvim" -c "PlenaryBustedDirectory lua/tetravim/tests/"
  ```

