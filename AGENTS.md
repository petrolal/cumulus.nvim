<!-- bmad:context -->
## cumulus.nvim

Polyglot JVM intelligence engine and opinionated Neovim distribution for modern backend engineering (Java, Kotlin, Scala, Rust, Go, Python, and Cloud Native development). Built in **Lua + Scala 3 & GraalVM Native Image** to pair with `cumulus.dotfiles`.

## Where things are

- **Engine source**: `engine/` — Scala 3 GraalVM native image CLI (`cumulus-engine`)
  - `engine/build.sbt`: SBT build, GraalVM native-image config, dependencies
  - `engine/src/main/scala/cumulus/`: Core engine logic, subcommands (DAG, Spring, K8s, Logs, Flyway, Tests)
  - `engine/src/test/scala/cumulus/`: MUnit test suites
- **Neovim Lua config**: `lua/cumulus/`
  - `lua/cumulus/core/`: Options, keymaps, autocmds, commands
  - `lua/cumulus/plugins/`: Lazy.nvim plugin specs (LSP, Blink CMP, Snacks, Treesitter, DAP)
  - `lua/cumulus/theme/`: Multi-cloud theme provider (AWS, Azure, GCP, OCI)
  - `lua/cumulus/util/`: Engine bridge, diagnostic parser, test runner
- **Scripts**: `scripts/` — Installation (`install.sh`), verification/smoke test (`validate.sh`)
- **Developer guide**: `CLAUDE.md` — build commands, testing patterns, and workflows

## Conventions that differ from defaults

- **Scala Engine Build**: Scala 3.5.2 + GraalVM Native Image. Must compile with `--no-fallback` and `--initialize-at-build-time`.
- **JSON Serialization**: Use `uPickle` macros (compile-time, reflection-free for GraalVM compatibility).
- **Process & Path I/O**: Use `os-lib` (`os.Path`, `os.proc`) for filesystem and process handling.
- **Testing**: Run MUnit tests via `sbt test` in `engine/`. Use `bash scripts/validate.sh` for Neovim headless sanity validation.

## BMAD Integration & Skills

BMAD skills are installed globally at `~/.gemini/config/skills` and `~/.claude/skills`, and locally at `.agents/skills` / `.claude/skills`. Use standard BMAD workflows:
- Planning / Architecture: `bmad-architecture`, `bmad-prd`, `bmad-spec`
- Development: `bmad-dev-story`, `bmad-quick-dev`, `bmad-build`
- Quality & Testing: `bmad-teach-me-testing`, `bmad-code-review`, `bmad-qa-generate-e2e-tests`
<!-- /bmad:context -->
