# Specification: SPEC-006 - SpringBoot Debug Configuration & Hotswap

## Metadata
- **Spec ID**: SPEC-006
- **Title**: SpringBoot Debug Configuration & Hotswap
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `ftplugin/java.lua` (extends)
  - `lua/cumulus/core/keymaps.lua` (extends)

- **Implementation**: Rust (Lua bridge only — minimal Lua)
---

---

## Architecture

**Lua is a bridge to the Rust backend. That is it.**

```
Neovim  →  Lua (bridge)  →  cumulus-core (Rust binary)
```

- **Rust** (`crates/cumulus-core`): all logic — parsing, file I/O, network, validation, analysis
- **Lua**: one job only — call the Rust binary and pass results to Neovim APIs
- No Lua fallbacks. No Lua parsing. No Lua analysis. If the binary is missing, fail explicitly.
---

## Goal & Intent

SpringBoot development requires starting the app with JPDA (Java Debug Wire Protocol) enabled to support breakpoint debugging and hotswap. Currently, developers must manually add JVM args or set MAVEN_OPTS. This creates inconsistency across team members and adds friction to the debug workflow.

This spec adds a standardized `<leader>ds` keymap (debug springboot) that:
1. Auto-detects Maven vs. Gradle project
2. Configures JPDA on port 5005 with suspend=n (so app starts immediately)
3. Enables hotcode reload for Spring classes
4. Launches DAP debugger and opens DAP UI
5. Shows status: "SpringBoot running with debugger on port 5005"

Developers press one key and the entire debug environment is ready.

---

## Scope Boundaries

**In scope:**
- Detect project type (Maven pom.xml / Gradle build.gradle)
- Launch SpringBoot app via Maven or Gradle with JPDA args
- Pre-select DAP config for Kotlin debug adapter
- Show statusline indicator while app is running
- Add `<leader>ds` keymap

**Out of scope:**
- Quarkus, Micronaut, or other non-Spring frameworks
- Custom JVM arg presets; use standard JPDA defaults
- Profiling or performance monitoring

---

## Prerequisite Analysis

- DAP is already configured in `tools-dap-ui.lua` and `ftplugin/java.lua`
- `dap.configurations.java` already exists for JDTLS
- Maven/Gradle runners are in `maven.lua` and `gradle.lua`
- `<leader>c*` keymaps for Java are in `lang_keymaps` stack

---

## Execution Checklist

- [ ] Extend `ftplugin/java.lua`: Add SpringBoot DAP config to `dap.configurations.java`
- [ ] Add to `dap.configurations.java`:
  ```lua
  {
    type = "java",
    name = "SpringBoot (Maven)",
    request = "launch",
    mainClass = "...", -- detect @SpringBootApplication class
    projectName = "...", -- detect Maven artifactId
    preLaunchTask = "mvn spring-boot:run -Dspring-boot.run.jvmArguments=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005\"",
  }
  ```
- [ ] Extend `lua/cumulus/core/keymaps.lua`: Add `<leader>ds` keymap to launch Spring DAP config

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p ftplugin/java.lua lua/cumulus/core/keymaps.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] `<leader>ds` launches SpringBoot app with JPDA enabled
- [ ] DAP UI opens and shows running app
- [ ] Breakpoints work
- [ ] Hotswap works for Spring classes
- [ ] Status indicator shows debugger is active

---

## Summary

One-keypress SpringBoot debugging with automatic hotswap configuration. Eliminates manual JVM arg setup and ensures consistency across team.
