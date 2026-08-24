# Cumulus: Monorepo Architecture - Epics & Stories

**Product Vision:** Transform cumulus.nvim and cumulus.dotfiles into a unified monorepo delivering a one-shot IDE installation for JVM-focused backend engineers with complete LSP intelligence, cloud-native tools, and IntelliJ-parity features.

**Status:** Ready for Sprint Planning  
**Last Updated:** 2026-08-24  
**Created By:** Monorepo Architecture Party  

---

## Executive Summary

This plan consolidates two independent GitHub repositories into a single monorepo while maintaining architectural independence of the LSP engine for future reuse. Users go from `git clone` → `bash bootstrap.sh` → full IDE ready in <10 minutes. The monorepo enables:

- ✅ Single installation point (no coordination between repos)
- ✅ Unified CI/CD (one pipeline, one release cycle)
- ✅ Clear architecture (backend/frontend/config separation maintained)
- ✅ Platform scaling (Linux now, macOS/Windows later as branches)
- ✅ Engine reusability (cumulus-lsp publishable independently)

---

## Epic Structure

```
Epic 1: Monorepo Foundation
  ↓ (enables)
Epic 2: Unified Installer & Bootstrap
  ↓ (enables)
Epic 3: IDE Configuration & Symlinks
  ↓ (enables)
Epic 4: Platform Support (macOS/Windows future)
```

---

# EPIC 1: Monorepo Foundation

**Goal:** Consolidate cumulus.nvim and cumulus.dotfiles into a single repository with clean internal boundaries, maintaining architectural independence of cumulus-lsp.

**Value Statement:** Users have one repository to clone; operators have one CI pipeline; developers see clear boundaries between LSP engine, Neovim IDE, and configuration.

**Definition of Done:**
- Monorepo structure established
- cumulus-lsp isolated as independent module
- cumulus-nvim frontend isolated as independent module
- Unified build.sbt and configuration
- All tests passing
- Documentation updated

**Estimated Effort:** 40 story points  
**Timeline:** 2-3 sprints

---

## Story 1.1: Repository Consolidation

**As a** user  
**I want to** clone a single repository and get the complete IDE  
**So that** I don't have to understand or manage two GitHub projects  

**Acceptance Criteria:**
- [ ] cumulus.nvim contents merged into cumulus.dotfiles/cumulus-nvim/ subdirectory
- [ ] cumulus.dotfiles becomes the primary repository (renamed git remote)
- [ ] Old cumulus.nvim GitHub repo marked as deprecated
- [ ] Migration guide created for existing users
- [ ] Directory structure preserved (lua/, init.lua, scripts/)
- [ ] Git history preserved from both repos
- [ ] All branches/tags migrated

**Story Points:** 5  
**Dependencies:** None  
**Owner:** DevOps  
**Sprint:** Sprint 1

**Tasks:**
1. Create new cumulus.dotfiles branch: `feature/monorepo-consolidation`
2. Create cumulus-nvim/ subdirectory
3. Copy cumulus.nvim contents while preserving git history
4. Update .gitignore for combined structure
5. Test repo cloning and basic navigation
6. Create MIGRATION_GUIDE.md for existing users

---

## Story 1.2: Unified Build Configuration (build.sbt)

**As a** developer  
**I want to** build the entire system with a single `sbt` command  
**So that** I don't manage separate build matrices for backend/frontend  

**Acceptance Criteria:**
- [ ] Single build.sbt at monorepo root
- [ ] Three modules defined: cumulus-lsp, cumulus-cli, cumulus-nvim
- [ ] cumulus-lsp builds independently
- [ ] cumulus-cli builds independently
- [ ] cumulus-nvim has no build (config only)
- [ ] All builds pass: `sbt compile`
- [ ] Native image builds: `sbt cumulusLsp/nativeImage`
- [ ] Cross-platform settings included (Linux/macOS/Windows paths)

**Story Points:** 8  
**Dependencies:** Story 1.1  
**Owner:** Build/Infrastructure  
**Sprint:** Sprint 1

**Tasks:**
1. Create unified build.sbt with multi-module structure
2. Update cumulus-lsp/build.sbt references
3. Update cumulus-cli/build.sbt references
4. Configure assembly settings for both modules
5. Add macOS/Windows platform detection
6. Test full build chain locally
7. Verify native image generation

---

## Story 1.3: Shared Build Infrastructure

**As a** operator  
**I want to** have common build dependencies and plugins managed centrally  
**So that** I maintain consistency across all modules  

**Acceptance Criteria:**
- [ ] Shared project/plugins.sbt with common plugins
- [ ] Common library versions defined in build.sbt
- [ ] Each module can override versions if needed
- [ ] sbt-assembly configured for both JVM modules
- [ ] NativeImagePlugin configured for cumulus-lsp
- [ ] BuildInfoPlugin configured for version tracking
- [ ] All plugins load without errors

**Story Points:** 5  
**Dependencies:** Story 1.2  
**Owner:** Build/Infrastructure  
**Sprint:** Sprint 1

**Tasks:**
1. Consolidate project/plugins.sbt
2. Define common library versions
3. Configure assembly defaults
4. Setup build info tracking
5. Test plugin loading for all modules

---

## Story 1.4: Test Infrastructure Unification

**As a** developer  
**I want to** run all tests from root with `sbt test`  
**So that** I catch integration issues early  

**Acceptance Criteria:**
- [ ] All cumulus-lsp tests pass: `sbt cumulusLsp/test`
- [ ] All cumulus-cli tests pass: `sbt cumulusCli/test`
- [ ] Full test suite runs: `sbt test`
- [ ] Coverage reporting works
- [ ] Tests can run in parallel
- [ ] CI integration test config updated

**Story Points:** 5  
**Dependencies:** Story 1.2  
**Owner:** QA/Testing  
**Sprint:** Sprint 1

**Tasks:**
1. Update test configurations for multi-module
2. Verify all test sources found
3. Setup coverage reporting
4. Configure parallel test execution
5. Update CI test commands

---

## Story 1.5: Documentation Structure Update

**As a** contributor  
**I want to** understand the new monorepo structure  
**So that** I can find files and understand boundaries  

**Acceptance Criteria:**
- [ ] MONOREPO_STRUCTURE.md created (directory layout, module purposes)
- [ ] Updated README.md explains single installation
- [ ] MIGRATION_GUIDE.md helps users transition
- [ ] Developer guides updated for new paths
- [ ] Architecture diagram shows monorepo layout
- [ ] All docs reference new paths consistently

**Story Points:** 3  
**Dependencies:** Story 1.1  
**Owner:** Documentation  
**Sprint:** Sprint 1

**Tasks:**
1. Create MONOREPO_STRUCTURE.md
2. Update main README.md
3. Create MIGRATION_GUIDE.md
4. Update all developer docs
5. Create architecture diagram
6. Update all path references

---

# EPIC 2: Unified Installer & Bootstrap

**Goal:** Create a single `bash bootstrap.sh` that installs and configures the entire IDE system automatically with zero manual steps.

**Value Statement:** Users experience seamless, one-shot installation; operators have reliable, repeatable setup process.

**Definition of Done:**
- bootstrap.sh installs cumulus-lsp, cumulus-nvim, and configuration
- All symlinks created automatically
- ~/.cumulus/ structure established
- Plugins synced
- Health check verifies installation
- Works on Linux and (prepared for) macOS

**Estimated Effort:** 35 story points  
**Timeline:** 2 sprints  
**Depends On:** Epic 1

---

## Story 2.1: Enhanced Bootstrap Script Architecture

**As a** user  
**I want to** run one command and have a complete IDE  
**So that** I don't need to understand the system architecture  

**Acceptance Criteria:**
- [ ] bash bootstrap.sh detects OS (Linux, macOS)
- [ ] Builds cumulus-lsp with progress indication
- [ ] Clones cumulus-nvim if not present
- [ ] Creates ~/.cumulus/config/ structure
- [ ] Shows 8-step progress (Build → Configure → Verify)
- [ ] Handles errors gracefully with recovery suggestions
- [ ] Non-interactive (no prompts unless error recovery)
- [ ] Completes in <10 minutes on Linux, <15 min on macOS

**Story Points:** 8  
**Dependencies:** Epic 1  
**Owner:** DevOps  
**Sprint:** Sprint 2

**Tasks:**
1. Rewrite bootstrap.sh with step-by-step progress
2. Add OS detection logic
3. Add error handling with recovery
4. Add timing/progress output
5. Test full flow start-to-finish
6. Document each step
7. Create troubleshooting guide

---

## Story 2.2: Engine Building & Installation

**As a** bootstrap process  
**I want to** build cumulus-lsp and install it to ~/.cumulus/bin/  
**So that** the IDE has JVM intelligence available  

**Acceptance Criteria:**
- [ ] Bootstrap detects if cumulus-lsp already built
- [ ] Runs `sbt cumulusLsp/nativeImage` if needed
- [ ] Copies binary to ~/.cumulus/bin/cumulus-lsp
- [ ] Verifies binary is executable
- [ ] Tests binary runs: `cumulus-lsp --version`
- [ ] Handles build failures with clear errors
- [ ] Skips rebuild if binary exists and unchanged

**Story Points:** 5  
**Dependencies:** Story 2.1  
**Owner:** Build/Infrastructure  
**Sprint:** Sprint 2

**Tasks:**
1. Add sbt compile step to bootstrap
2. Add native image compilation
3. Add binary placement logic
4. Add verification checks
5. Add caching logic (skip if built)
6. Add error recovery
7. Test on fresh system

---

## Story 2.3: Frontend Installation & Linking

**As a** bootstrap process  
**I want to** clone cumulus-nvim and link it to ~/.cumulus/  
**So that** the IDE configuration is shared and accessible  

**Acceptance Criteria:**
- [ ] Bootstrap clones cumulus-nvim to ~/.config/nvim if missing
- [ ] Creates ~/.cumulus/config/nvim/ directory
- [ ] Copies lua/ and init.lua to ~/.cumulus/config/nvim/
- [ ] Creates symlink: ~/.config/nvim → ~/.cumulus/config/nvim/
- [ ] Verifies symlink is valid
- [ ] Handles existing config gracefully (backup + new setup)
- [ ] Tests `nvim --version` works

**Story Points:** 5  
**Dependencies:** Story 2.1  
**Owner:** Frontend/Config  
**Sprint:** Sprint 2

**Tasks:**
1. Add git clone logic for cumulus-nvim
2. Add config directory creation
3. Add symlink creation with validation
4. Add existing config backup logic
5. Add verification steps
6. Test on fresh system
7. Test on system with existing config

---

## Story 2.4: Plugin Synchronization

**As a** bootstrap process  
**I want to** sync all Neovim plugins automatically  
**So that** the IDE is feature-complete after bootstrap  

**Acceptance Criteria:**
- [ ] Bootstrap runs `nvim --headless "+Lazy! sync" +qa`
- [ ] All plugins from lua/cumulus/plugins/ installed
- [ ] LSP integration plugins installed
- [ ] Completion plugins installed
- [ ] UI enhancement plugins installed
- [ ] Cloud tool plugins installed
- [ ] Handles plugin download failures
- [ ] Completes in <3 minutes on typical connection

**Story Points:** 3  
**Dependencies:** Story 2.3  
**Owner:** Frontend/Plugins  
**Sprint:** Sprint 2

**Tasks:**
1. Add Lazy plugin sync step
2. Add error handling for network issues
3. Add timeout handling
4. Test on fresh system
5. Verify all plugins install
6. Test retry logic

---

## Story 2.5: Health Check & Verification

**As a** bootstrap process  
**I want to** verify everything installed correctly  
**So that** user knows if setup was successful  

**Acceptance Criteria:**
- [ ] Runs `nvim --headless "+checkhealth cumulus" +qa`
- [ ] Checks cumulus-lsp binary exists and runs
- [ ] Checks LSP config is correct
- [ ] Checks plugins are loaded
- [ ] Checks symlinks are valid
- [ ] Reports clear status (✓ all good or ✗ issues)
- [ ] Lists any manual fixes needed
- [ ] Exits with code 0 on success, 1 on critical failures

**Story Points:** 5  
**Dependencies:** Story 2.4  
**Owner:** QA/Testing  
**Sprint:** Sprint 2

**Tasks:**
1. Create cumulus.nvim health check plugin
2. Add LSP verification
3. Add symlink validation
4. Add plugin verification
5. Add reporting logic
6. Test all success/failure paths

---

## Story 2.6: Desktop Environment Setup (Linux)

**As a** bootstrap process  
**I want to** configure Sway and other desktop tools  
**So that** the full desktop/IDE experience is ready  

**Acceptance Criteria:**
- [ ] Copies Sway config to ~/.cumulus/config/sway/
- [ ] Creates symlink: ~/.config/sway → ~/.cumulus/config/sway/
- [ ] Copies theme configs to ~/.cumulus/config/themes/
- [ ] Copies other tool configs (kitty, wofi, etc.)
- [ ] Sets active theme to default (nord or user preference)
- [ ] Skips on macOS (detected by OS check)
- [ ] Handles existing configs (backup + new setup)

**Story Points:** 5  
**Dependencies:** Story 2.1  
**Owner:** Desktop/Config  
**Sprint:** Sprint 2

**Tasks:**
1. Add Sway config copying logic
2. Add symlink creation
3. Add other tool config setup
4. Add theme initialization
5. Add OS detection for skip logic
6. Test on Linux
7. Test skip on macOS detection

---

# EPIC 3: IDE Configuration & Symlinks

**Goal:** Establish shared configuration at ~/.cumulus/ that both cumulus-lsp and cumulus-nvim read from, with automatic linking and no manual configuration required.

**Value Statement:** Single point of configuration; changes apply automatically to both backend and frontend; DRY principle enforced.

**Definition of Done:**
- ~/.cumulus/ is single source of truth for all config
- All symlinks created automatically by bootstrap
- No manual `ln` commands required
- Config changes apply immediately
- nvim and dotfiles both read same files

**Estimated Effort:** 25 story points  
**Timeline:** 1-2 sprints  
**Depends On:** Epic 2

---

## Story 3.1: Shared Configuration Root Structure

**As a** system  
**I want to** have ~/.cumulus/ be the single source of truth  
**So that** configuration is centralized and manageable  

**Acceptance Criteria:**
- [ ] ~/.cumulus/ directory created by bootstrap
- [ ] Subdirectories created: config/, bin/, logs/
- [ ] config/ has: nvim/, sway/, themes/, other/
- [ ] settings.yaml created with defaults
- [ ] Environment variables configured in bootstrap
- [ ] All paths documented in SHARED_CONFIGURATION.md
- [ ] Bootstrap verifies all directories exist after creation

**Story Points:** 3  
**Dependencies:** Story 2.1  
**Owner:** Infrastructure  
**Sprint:** Sprint 2

**Tasks:**
1. Add ~/.cumulus/ creation to bootstrap
2. Add subdirectory creation
3. Add settings.yaml creation
4. Add environment variable setup
5. Test directory structure
6. Verify all paths accessible

---

## Story 3.2: Neovim Configuration Linking

**As a** nvim user  
**I want to** edit ~/.cumulus/config/nvim/init.lua and have changes apply to nvim  
**So that** there's a single source of truth for IDE configuration  

**Acceptance Criteria:**
- [ ] ~/.config/nvim → ~/.cumulus/config/nvim/ (symlink)
- [ ] ~/cumulus.nvim/init.lua → ~/.cumulus/config/nvim/init.lua (symlink)
- [ ] ~/cumulus.nvim/lua/cumulus/ → ~/.cumulus/config/nvim/lua/cumulus/ (symlink)
- [ ] Changes to ~/.cumulus/config/nvim/ visible to nvim immediately
- [ ] :source $MYVIMRC reloads from shared location
- [ ] symlink validation in health check

**Story Points:** 3  
**Dependencies:** Story 3.1  
**Owner:** Frontend/Config  
**Sprint:** Sprint 3

**Tasks:**
1. Add symlink creation to bootstrap
2. Add symlink validation
3. Test nvim sees changes
4. Test :source works
5. Add to health check
6. Document in user guide

---

## Story 3.3: LSP Configuration & Defaults

**As a** cumulus-lsp  
**I want to** read LSP configuration from ~/.cumulus/config/nvim/lsp-config.yaml  
**So that** LSP behavior is configurable without code changes  

**Acceptance Criteria:**
- [ ] cumulus-lsp reads ~/.cumulus/config/nvim/lsp-config.yaml
- [ ] Defaults provided for all LSP languages (Java, Kotlin, Scala, Groovy)
- [ ] Language versions configurable
- [ ] LSP server paths configurable
- [ ] LSP options (timeout, cache, etc.) configurable
- [ ] Changes to config file picked up on nvim restart
- [ ] Invalid config handled gracefully with defaults

**Story Points:** 5  
**Dependencies:** Story 3.1  
**Owner:** Backend/LSP  
**Sprint:** Sprint 3

**Tasks:**
1. Create lsp-config.yaml template
2. Add config reading to cumulus-lsp Main.scala
3. Add defaults for each language
4. Add error handling for invalid config
5. Test config reading
6. Test defaults applied
7. Document config options

---

## Story 3.4: Theme System Integration

**As a** user  
**I want to** change the theme once and have it apply to desktop + nvim  
**So that** the entire system has consistent appearance  

**Acceptance Criteria:**
- [ ] ~/.cumulus/config/themes/active.yaml specifies active theme
- [ ] Sway reads theme from ~/.cumulus/config/themes/active.yaml
- [ ] Nvim reads theme from ~/.cumulus/config/themes/active.yaml
- [ ] Both apply same colors, fonts, styling
- [ ] `cumulus theme nord` updates active.yaml and applies theme
- [ ] Reload required for nvim, immediate for Sway (HUP signal)
- [ ] Default theme set to "nord" (or user preference)

**Story Points:** 5  
**Dependencies:** Story 3.1, Story 2.6  
**Owner:** UI/Design  
**Sprint:** Sprint 3

**Tasks:**
1. Create theme YAML structure
2. Add theme reading to cumulus-lsp
3. Add theme reading to Sway config
4. Add theme reading to nvim init.lua
5. Create `cumulus theme` subcommand
6. Test theme switching
7. Document theme customization

---

## Story 3.5: Plugin Configuration Unification

**As a** user  
**I want to** configure plugins in ~/.cumulus/config/nvim/plugins.lua  
**So that** plugin specs are in one place  

**Acceptance Criteria:**
- [ ] ~/.cumulus/config/nvim/lua/cumulus/plugins.lua is single source
- [ ] Lazy.nvim reads from this location
- [ ] Plugin specs for LSP, completion, UI all here
- [ ] User can customize in ~/.cumulus/config/nvim/lua/user/plugins.lua
- [ ] Changes applied on nvim restart
- [ ] All plugins documented with purpose

**Story Points:** 3  
**Dependencies:** Story 3.2  
**Owner:** Frontend/Plugins  
**Sprint:** Sprint 3

**Tasks:**
1. Move all plugins.lua specs to ~/.cumulus/config/nvim/
2. Create symlink from cumulus-nvim/lua/cumulus/plugins → shared
3. Add user plugin location
4. Test plugin loading
5. Document plugin structure

---

# EPIC 4: IDE Configuration for JVM Languages

**Goal:** Configure cumulus-nvim as a complete IDE for Java, Kotlin, Scala, Groovy, and cloud-native development with IntelliJ-parity features.

**Value Statement:** Backend engineers have a professional IDE alternative to IntelliJ with full language support, cloud tools, and familiar workflows.

**Definition of Done:**
- LSP configured for all JVM languages
- Code completion working (LSP + nvim-cmp)
- Refactoring tools available
- Cloud tool support (K8s, Docker, Terraform)
- IntelliJ keybindings by default
- Test runner integration
- Debugger support

**Estimated Effort:** 40 story points  
**Timeline:** 2-3 sprints  
**Depends On:** Epic 3

---

## Story 4.1: Java Language Server Setup

**As a** Java developer  
**I want to** have full IDE features for Java (completion, go-to-definition, refactoring)  
**So that** I can develop Java projects efficiently in nvim  

**Acceptance Criteria:**
- [ ] Eclipse JDT Language Server (or equivalent) installed
- [ ] LSP configured for .java files
- [ ] Code completion works (omni-complete via LSP)
- [ ] Go to definition works (Ctrl+click or `:Lsp goto_definition`)
- [ ] Find references works (`:Lsp references`)
- [ ] Rename refactoring works (`:Lsp rename`)
- [ ] Hover documentation shows type info
- [ ] Error diagnostics shown in gutter
- [ ] Works with Maven and Gradle projects

**Story Points:** 8  
**Dependencies:** Epic 4  
**Owner:** Language Support  
**Sprint:** Sprint 4

**Tasks:**
1. Download/configure Eclipse JDT LSP
2. Configure nvim LSP client for Java
3. Setup project detection (Maven/Gradle)
4. Configure keybindings
5. Test with sample Java project
6. Verify all features
7. Document Java development workflow

---

## Story 4.2: Kotlin Language Server Setup

**As a** Kotlin developer  
**I want to** have full IDE features for Kotlin (completion, navigation, refactoring)  
**So that** I can develop Kotlin projects efficiently in nvim  

**Acceptance Criteria:**
- [ ] Kotlin Language Server installed
- [ ] LSP configured for .kt files
- [ ] Code completion works
- [ ] Go to definition works
- [ ] Find references works
- [ ] Rename refactoring works
- [ ] Hover documentation works
- [ ] Error diagnostics shown
- [ ] Works with Gradle projects

**Story Points:** 8  
**Dependencies:** Epic 4  
**Owner:** Language Support  
**Sprint:** Sprint 4

**Tasks:**
1. Download/configure Kotlin Language Server
2. Configure nvim LSP client for Kotlin
3. Setup Gradle project detection
4. Configure keybindings
5. Test with sample Kotlin project
6. Verify all features
7. Document Kotlin development workflow

---

## Story 4.3: Scala & Groovy Support

**As a** Scala/Groovy developer  
**I want to** have IDE features for Scala and Groovy  
**So that** I can develop polyglot JVM projects in nvim  

**Acceptance Criteria:**
- [ ] Scala Language Server (Metals or equivalent) installed
- [ ] Groovy LSP installed
- [ ] Both configured for respective file types
- [ ] Code completion works for both
- [ ] Navigation works for both
- [ ] Error diagnostics shown for both
- [ ] Works with sbt (Scala) and Gradle (Groovy) projects

**Story Points:** 8  
**Dependencies:** Epic 4  
**Owner:** Language Support  
**Sprint:** Sprint 4

**Tasks:**
1. Download/configure Scala Language Server
2. Download/configure Groovy LSP
3. Configure nvim LSP clients
4. Setup project detection
5. Test with sample projects
6. Verify features
7. Document workflows

---

## Story 4.4: Cloud Native Tool Support

**As a** cloud engineer  
**I want to** have IDE features for Kubernetes YAML, Docker, Terraform, and cloud CLIs  
**So that** I can develop cloud-native infrastructure in nvim  

**Acceptance Criteria:**
- [ ] Kubernetes YAML LSP configured
- [ ] Docker LSP/linting configured
- [ ] Terraform LSP configured
- [ ] YAML formatting configured
- [ ] HCL formatting configured
- [ ] Completion works for all cloud tools
- [ ] Error checking works for all formats

**Story Points:** 8  
**Dependencies:** Epic 4  
**Owner:** Cloud Tools  
**Sprint:** Sprint 5

**Tasks:**
1. Download/configure K8s YAML LSP
2. Download/configure Docker support
3. Download/configure Terraform LSP
4. Configure formatters (prettier, terraform fmt)
5. Test with sample files
6. Verify error checking
7. Document cloud workflows

---

## Story 4.5: IntelliJ-Compatible Keybindings

**As a** IntelliJ user  
**I want to** use familiar keybindings in nvim  
**So that** the transition from IDE to nvim is smooth  

**Acceptance Criteria:**
- [ ] Ctrl+Click → go to definition
- [ ] Ctrl+Alt+O → organize imports
- [ ] Ctrl+H → find/replace
- [ ] Ctrl+B → go to declaration
- [ ] Ctrl+F12 → document outline
- [ ] Alt+Enter → code actions/quick fixes
- [ ] Shift+F6 → rename
- [ ] Ctrl+Shift+U → find usages
- [ ] F5 → start debugging
- [ ] All keybindings documented

**Story Points:** 5  
**Dependencies:** Story 4.1  
**Owner:** Frontend/UX  
**Sprint:** Sprint 5

**Tasks:**
1. Create keymap.lua with IntelliJ bindings
2. Map to actual nvim commands/plugins
3. Test all keybindings
4. Document in keybindings guide
5. Allow user customization
6. Test with real IDE workflows

---

## Story 4.6: Code Navigation & Refactoring Tools

**As a** developer  
**I want to** navigate code (jump to definition, find usages, breadcrumbs) and refactor (rename, extract, inline)  
**So that** I have IDE-like code exploration and modification  

**Acceptance Criteria:**
- [ ] Jump to definition working (native LSP)
- [ ] Find all usages working (`:Telescope lsp_references`)
- [ ] Breadcrumb navigation working
- [ ] Symbol explorer working (`:Telescope symbols`)
- [ ] Rename refactoring working (LSP rename)
- [ ] Extract variable working (lsp-actions plugin)
- [ ] Extract method working
- [ ] Inline variable/function working
- [ ] All features tested with sample code

**Story Points:** 8  
**Dependencies:** Story 4.1, Story 4.2  
**Owner:** Navigation/Refactoring  
**Sprint:** Sprint 5

**Tasks:**
1. Configure Telescope for code navigation
2. Configure nvim-code-action-menu for refactoring
3. Setup breadcrumb display
4. Setup symbol explorer
5. Test all features
6. Document workflows
7. Create tutorials

---

# EPIC 5: Platform Support & Distribution

**Goal:** Prepare infrastructure for multi-platform support (macOS, Windows) and streamlined distribution.

**Value Statement:** Developers on different platforms get native experience; updates are easy and safe.

**Estimated Effort:** 30 story points  
**Timeline:** 2-3 sprints (after Epics 1-4)  
**Depends On:** Epics 1-4

---

## Story 5.1: macOS Platform Support (Preparation)

**As a** macOS developer  
**I want to** run the same bootstrap.sh and get a working IDE  
**So that** I can use cumulus on my Mac laptop  

**Acceptance Criteria:**
- [ ] bootstrap.sh detects macOS
- [ ] Skips Linux-only steps (Sway, etc)
- [ ] Builds cumulus-lsp as macOS binary
- [ ] Uses macOS-native tools (Homebrew, etc)
- [ ] Creates ~/.cumulus/ same structure
- [ ] Installs Neovim (or verifies present)
- [ ] Full IDE works on macOS

**Story Points:** 8  
**Dependencies:** Epic 2  
**Owner:** Platform Engineering  
**Sprint:** Sprint 6

**Tasks:**
1. Add macOS detection to bootstrap.sh
2. Add macOS-specific build flags to build.sbt
3. Update Homebrew dependency installation
4. Test on macOS (Intel + Apple Silicon if possible)
5. Document macOS setup
6. Create macOS troubleshooting guide

---

## Story 5.2: GitHub Releases & Distribution

**As a** end-user  
**I want to** download pre-built binaries instead of building from source  
**So that** installation is faster and doesn't require sbt/Java  

**Acceptance Criteria:**
- [ ] GitHub Actions workflow builds and releases binaries
- [ ] Releases include: cumulus-lsp (Linux x86_64, Linux ARM, macOS Intel, macOS ARM)
- [ ] Release includes full repo as source tarball
- [ ] Release notes auto-generated from commits
- [ ] Each release tagged with semantic version
- [ ] Bootstrap can download pre-built cumulus-lsp

**Story Points:** 8  
**Dependencies:** Epics 1-4  
**Owner:** DevOps/Release  
**Sprint:** Sprint 6

**Tasks:**
1. Create GitHub Actions workflow for builds
2. Configure matrix for multiple platforms
3. Add release creation step
4. Update bootstrap to detect and download binaries
5. Test download and installation
6. Document release process

---

## Story 5.3: Update Mechanism

**As a** user  
**I want to** update my IDE with `cumulus install` or `cumulus update`  
**So that** I can get new features and bugfixes  

**Acceptance Criteria:**
- [ ] `cumulus install` can run on existing installation
- [ ] Detects if cumulus-lsp needs rebuild
- [ ] Downloads latest binaries if available
- [ ] Re-syncs plugins
- [ ] Backs up old config if changes needed
- [ ] Health check runs after update
- [ ] Update is atomic (rollback on failure)

**Story Points:** 5  
**Dependencies:** Story 5.2  
**Owner:** Update/Distribution  
**Sprint:** Sprint 6

**Tasks:**
1. Add update detection logic
2. Add rollback mechanism
3. Add version checking
4. Test update path
5. Document update process
6. Create recovery guide

---

---

# Implementation Timeline

## Sprint 1: Foundation (Weeks 1-2)
- Story 1.1: Repository Consolidation
- Story 1.2: Unified Build Configuration
- Story 1.3: Shared Build Infrastructure
- Story 1.4: Test Infrastructure Unification
- Story 1.5: Documentation Structure Update
- **Output:** Monorepo ready for bootstrap development

## Sprint 2: Bootstrap & Installation (Weeks 3-4)
- Story 2.1: Enhanced Bootstrap Script Architecture
- Story 2.2: Engine Building & Installation
- Story 2.3: Frontend Installation & Linking
- Story 2.4: Plugin Synchronization
- Story 2.5: Health Check & Verification
- Story 2.6: Desktop Environment Setup
- **Output:** One-shot installer working on Linux

## Sprint 3: Configuration (Weeks 5-6)
- Story 3.1: Shared Configuration Root Structure
- Story 3.2: Neovim Configuration Linking
- Story 3.3: LSP Configuration & Defaults
- Story 3.4: Theme System Integration
- Story 3.5: Plugin Configuration Unification
- **Output:** Unified configuration system live

## Sprint 4: JVM Languages (Weeks 7-8)
- Story 4.1: Java Language Server Setup
- Story 4.2: Kotlin Language Server Setup
- Story 4.3: Scala & Groovy Support
- **Output:** Full JVM language support

## Sprint 5: IDE Features & Cloud Tools (Weeks 9-10)
- Story 4.4: Cloud Native Tool Support
- Story 4.5: IntelliJ-Compatible Keybindings
- Story 4.6: Code Navigation & Refactoring Tools
- **Output:** Complete IDE experience

## Sprint 6: Platform & Distribution (Weeks 11-12)
- Story 5.1: macOS Platform Support
- Story 5.2: GitHub Releases & Distribution
- Story 5.3: Update Mechanism
- **Output:** Multi-platform support, streamlined distribution

---

# Success Metrics

By end of implementation:

✅ **User Experience**
- Single `bash bootstrap.sh` → full IDE in <10 minutes
- Works on Linux (MVP), prepared for macOS
- Plain `nvim` launches complete IDE
- All JVM languages supported
- IntelliJ-familiar keybindings
- Cloud-native tools integrated

✅ **Developer Experience**
- Single `git clone` for entire system
- Single `sbt` build command
- All tests passing
- Clear architecture (backend/frontend/config)
- Easy to contribute and extend

✅ **Operational Experience**
- One GitHub repository
- One CI/CD pipeline
- One release cadence
- Streamlined updates
- Pre-built binaries available

✅ **Quality**
- Full LSP intelligence for JVM languages
- Code completion, navigation, refactoring
- Health checks verify installation
- Automated testing of all components
- Clear error messages and recovery paths

---

# Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Merging repos loses git history | Preserve git history during merge; document in MIGRATION_GUIDE |
| Build time increases | Incremental builds with caching; publish pre-built binaries |
| Complex bootstrap breaks easily | Extensive error handling; health check after each step |
| User config conflicts on merge | Backup existing configs; document manual migration |
| macOS differences break Linux build | Test matrix in CI for both platforms; OS detection in bootstrap |

---

**Document Status:** Ready for Sprint Planning  
**Next Action:** Assign stories to sprint, create Jira/Linear tickets, begin Sprint 1  
**Created:** 2026-08-24  
**Author:** Monorepo Architecture Party + Epic/Story Creation Workflow
