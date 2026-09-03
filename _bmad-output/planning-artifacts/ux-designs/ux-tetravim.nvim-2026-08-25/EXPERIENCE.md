---
status: final
updated: 2026-08-25
---

# Foundation

The experience is built natively on **Neovim** (TUI), heavily leveraging `folke/snacks.nvim` for UI primitives, `Telescope.nvim` for fuzzy finding, and native LSP/DAP for intelligence. 
Visual identity and theme rules live in `{DESIGN.md}`.

# Information Architecture

The interface is divided into functional zones:
- **Editor Canvas**: The central area for code buffers.
- **Left Sidebar**: Project navigation via `oil.nvim`, toggled via keymap.
- **Right Sidebar**: Secondary structure (e.g., Outline/Symbols, DAP UI variables).
- **Bottom Drawer**: Output consoles, build logs, terminal, and test runners (`neotest`).
- **Floating Modals**: Ephemeral pickers (Spring Beans, REST Endpoints, Code Actions).

# Voice and Tone

- **Concise & Actionable**: Notifications and error messages should explain *what* happened and *how* to fix it, without filler words.
- **Developer-Centric**: Use standard JVM and DevOps terminology. Don't dumb down stack traces or infrastructure concepts.
- **Unobtrusive**: Success states should be quiet (a brief statusline flash or tick). Errors must be visible and explicit.

# Component Patterns

## Keymap Hierarchy
TetraVim strictly adheres to a mnemonic `<leader>`-based hierarchy to organize enterprise workflows:
- `<leader>c`: **Code** operations (Rename `<leader>cr`, Extraction `<leader>cm`/`<leader>cv`/`<leader>ci`, Code Action, Format)
- `<leader>j`: **Java/JVM** specific operations (`<leader>jsb` Spring Beans, `<leader>jse` Spring Endpoints, `<leader>jsd` Detect Boot App, `<leader>jt` tests)
- `<leader>o`: **DevOps / Operations** (Terraform `<leader>ot`, Ansible `<leader>oy`, Docker `<leader>od`, Kubernetes `<leader>ok`, CloudFormation `<leader>oc`)
- `<leader>d`: **Debug** (DAP interactions)
- `<leader>g`: **Git / Collaboration** (Conflict resolution `<leader>gc*`, Forge reviews `<leader>gr*`)
- `<leader>db`: **Database** exploration (Dadbod UI)
- `<leader>H`: **HTTP / REST** testing (Kulala, OpenAPI)

## Pickers & Navigation
When a user requests a list (Spring Beans, Endpoints, Files), the interface responds with a fuzzy-searchable Telescope or Snacks picker. 
- **[ASSUMPTION]** Preview panes are always enabled by default for context.

# State Patterns

## Editor Modes
Standard Neovim modes (Normal, Insert, Visual, Command) dictate interaction. The statusline explicitly colors based on the mode to prevent user error.

## Asynchronous Operations
Enterprise JVM builds and cloud deployments take time.
- **Loading**: Represented by a spinner in the statusline or a dedicated snacks notification.
- **Completion**: A toast notification informs the user of success/failure, and diagnostics are immediately populated in the quickfix list or inline.

# Interaction Primitives

- **Jump to Definition**: `gd` (Native LSP behavior).
- **Hover/Documentation**: `K`. Floating window with single borders.
- **Code Action**: `<leader>ca`. Floating menu showing available refactorings or fixes.

# Accessibility Floor

- **Keyboard First**: Every single action must be accessible without a mouse.
- **Contrast**: Rely on the canonical, high-contrast "Tetris" palette that meets WCAG AA for text.
- **Screen Reader [ASSUMPTION]**: TUI environments are inherently challenging for screen readers, but keeping UI elements standard (quickfix lists instead of complex floating UIs for errors) aids compatibility.

# Key Flows

## Flow: Debugging a Failing JVM Service (Winston's Journey)
1. **Trigger**: Winston opens `OrderService.java` and places a breakpoint (`<leader>db`).
2. **Launch**: He initiates the debug session (`<leader>dc`).
3. **Transition**: The UI transitions to the DAP perspective. The right sidebar opens showing Local Variables, and the bottom drawer opens the REPL/Console.
4. **Inspect**: Execution pauses. Winston uses `<leader>di` to step into a method. He hovers over `orderId` to see its value in a floating tooltip.
5. **Climax**: He identifies the null reference, stops the debugger (`<leader>dx`), makes the fix, and uses `<leader>jb` to recompile the Maven module directly within the editor.

## Flow: Exploring Spring Endpoints
1. **Trigger**: A developer needs to find the route for updating user profiles.
2. **Launch**: They press `<leader>jse` (Java Spring Endpoints).
3. **Transition**: A floating Telescope picker appears, parsing the AST of all controllers in the workspace.
4. **Action**: The developer types "profile put".
5. **Climax**: The list filters instantly. Pressing `<enter>` jumps the cursor directly to the `@PutMapping("/profile")` annotation in `UserController.java`.
