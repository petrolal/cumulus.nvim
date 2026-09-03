# Epic 1 Context: Enterprise Debugging & Profiling

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Deliver a best-in-class debugging and profiling experience for JVM applications (Java, Kotlin, Scala), including Spring Boot and microservices, that rivals IntelliJ IDEA's debugger. This is the "execution parity" pillar of the broader effort to migrate `tetravim.nvim` from a Neovim distribution into a full enterprise-grade IDE replacement for IntelliJ, so debugging and profiling must be robust enough that developers never need to fall back to another tool for execution-time troubleshooting.

## Stories

- Story 1.1: Advanced JVM Debugger (nvim-dap integration)
- Story 1.2: Continuous Profiling & Flamegraphs

## Requirements & Constraints

- Must serve JVM developers across Java, Kotlin, and Scala uniformly, not just one language.
- Must handle real-world enterprise workloads: Spring Boot applications and microservices architectures, not just single-file/simple programs.
- Any new capability must preserve the lightweight, keyboard-driven Neovim philosophy — features should feel native to the editor rather than bolted-on external tooling.
- PRD, Architecture Spine, and UX Design specifications exist in `_bmad-output/planning-artifacts/` and define the overarching invariants (event-driven UI, zero global caching, headless execution).

## Technical Decisions

- The project is actively migrating away from a custom Scala-based backend engine toward a pure Neovim Lua-plugin architecture. New debugging/profiling functionality is implemented as standard Lua plugins and LSP/DAP integrations rather than reintroducing a custom backend engine or CLI process.
- Underlying tooling (language servers such as JDTLS, Kotlin LS, and debug adapters) is installed and managed via `mason.nvim` and `mason-lspconfig.nvim`. Scala Metals is integrated directly via `scalameta/nvim-metals`.
- Terminal and UI surfaces across the IDE (including output panels and pickers) are built on Snacks (`folke/snacks.nvim`) and Telescope as the standard UI layer, for consistency with the rest of the editor rather than introducing custom UI frameworks.
