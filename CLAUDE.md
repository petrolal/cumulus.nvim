# CLAUDE.md

## Project Overview
`cumulus.nvim` is a polyglot JVM intelligence engine and opinionated Neovim distribution for modern backend engineering (Java, Kotlin, Scala, Rust, Go, Python, Cloud Native).

## Common Build & Test Commands
- **Compile Scala engine**: `cd engine && sbt compile`
- **Run engine tests**: `cd engine && sbt test`
- **Build engine native image**: `cd engine && sbt nativeImage`
- **Validate Neovim config**: `bash scripts/validate.sh`
- **Format Lua**: `stylua lua/`

## Architecture & Code Guidelines
- **Engine**: Scala 3 + GraalVM Native Image. Must remain reflection-free (`uPickle`, `os-lib`).
- **Neovim Configuration**: Lua plugins managed by Lazy.nvim in `lua/cumulus/plugins/`.
- **Bridge**: `lua/cumulus/util/engine.lua` handles asynchronous IPC with the compiled `cumulus-engine` binary.

## BMAD Skills & Agent Integration
BMAD skills are available locally in `.claude/skills` and globally in `~/.claude/skills`.
Refer to [AGENTS.md](file:///home/petrolal/cumulus.nvim/AGENTS.md) for contextual guidelines.
