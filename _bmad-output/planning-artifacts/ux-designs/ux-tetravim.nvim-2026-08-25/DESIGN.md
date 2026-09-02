---
status: final
updated: 2026-09-02
colors:
  scheme: TetraVim "Tetris" (canonical, single palette)
  background: '#111216'
  surface: '#1E1F26'
  foreground: '#BCBEC4'
  comment: '#5C6370'
  cyan: '#00F0F0'
  purple: '#A000F0'
  yellow: '#F0F000'
  green: '#00F000'
  red: '#F00000'
  orange: '#FF7F00'
  blue: '#0000F0'
  blue_text: '#5B8CFF'
typography:
  family: JetBrains Mono, Fira Code (with Nerd Fonts)
  size: Controlled by terminal emulator
rounded: 'rounded'
spacing: 'compact'
components:
  pickers: Telescope / Snacks.picker
  notifications: Snacks.notifier
  statusline: Lualine
---

# Brand & Style

**TetraVim IDE** is a purposeful, distraction-free environment for enterprise JVM and cloud engineers. The aesthetic is "professional cyberpunk"—dark, high-contrast, and deeply functional. Its visual identity is the fixed **"Tetris"** palette: seven saturated tetromino colours, each permanently bound to one semantic code role so token colour is always predictable.

# Colors

TetraVim ships a single canonical colour scheme — **"Tetris"** — inspired by the seven Tetris tetromino colours. It is a self-contained dark theme (no Catppuccin / Tokyonight dependency at runtime) tuned for long enterprise JVM sessions and IntelliJ-level token parity. There are no per-project, per-context, or per-cloud-provider palette variants.

## Structural palette

| Role | Hex |
| --- | --- |
| Background | `#111216` |
| Surface / floating windows / statusline | `#1E1F26` |
| Foreground (default text) | `#BCBEC4` |
| Comments | `#5C6370` |

## Tetromino accents

Each tetromino colour owns exactly one semantic token role. The mapping is fixed — colours never shift based on cloud provider, project, or mode.

| Piece | Hex | Token role |
| --- | --- | --- |
| I — Cyan | `#00F0F0` | Interfaces, class / type names, primitive types, active cursor-focus |
| T — Purple | `#A000F0` | Keywords, visibility modifiers, control flow |
| O — Yellow | `#F0F000` | Method / function declarations and calls |
| S — Green | `#00F000` | Annotations & decorators (`@Override`, `@Service`), success states |
| Z — Red | `#F00000` | Errors, breakpoints, deletions |
| L — Orange | `#FF7F00` | Strings, warnings |
| J — Blue | `#0000F0` | Constants, enum members, informational hints |

## Blue contrast deviation

Pure J-piece blue `#0000F0` scores only **2.01:1** against the `#111216` background — below WCAG AA (4.5:1) and AA-large (3:1), so it is illegible as body text. The theme therefore renders on-background text roles (constants, enum members, hint diagnostics) in a lightened tint, **`#5B8CFF`** (**5.92:1**), and keeps `#0000F0` (`blue_pure`) only for non-text accents and terminal ANSI slots 4 / 12.

# Typography

- **Font Family**: A monospace font patched with Nerd Fonts is strictly required (e.g., JetBrains Mono Nerd Font). This ensures all diagnostic signs, Git status icons, and file type glyphs render correctly.
- **Ligatures**: Highly recommended for modern JVM languages (Scala, Kotlin, Java > 14).

# Layout & Spacing

Screen real estate is paramount in a terminal.
- **Splits**: Preferred for persistent, referenceable context (e.g., sidebars for file trees, bottom panes for test output or terminals).
- **Floating Windows**: Used strictly for ephemeral, task-focused workflows (e.g., Spring Bean pickers, code actions, renaming).
- **Padding**: Floating windows should have at least 1 column/row of padding to prevent text from crowding the borders.

# Elevation & Depth

In a TUI, depth is simulated through borders and background blending (`winblend`).
- **Base Layer**: Regular buffers, sidebars. (No border)
- **Mid Layer**: Floating windows for references (e.g., hover documentation, LSP signatures). (Single border)
- **Top Layer**: Interactive modal pickers (Telescope) and notifications (Snacks). (Rounded borders, optional drop shadow if supported by the terminal).

# Shapes

- **Borders**: Standardized to `rounded` across all primary UI plugins (Telescope, Mason, Lazy, Snacks).
- **Diagnostic Signs**: 
  - Error: `` 
  - Warn: `` 
  - Hint: `` 
  - Info: ``

# Components

## Pickers (Telescope / Snacks)
Centered floating window. The input prompt sits at the bottom or top, clearly delineated from the results list. Syntax highlighting is maintained in the preview pane.

## Notifications (Snacks.notifier)
Toast notifications appear in the bottom right corner (or top right, depending on standard config). They auto-dismiss after a brief timeout. Errors persist longer than infos.

## Statusline (Lualine)
Minimalist. Left: Mode badge, Git branch, Git diff. Center: Filepath. Right: LSP status, Diagnostics count, Location (Row/Col). The mode badge is the only element that changes colour (per Vim mode), drawn from the Tetris accents.

# Do's and Don'ts

- **Do** use floating windows for temporary context (hover, pickers).
- **Don't** use floating windows for long-running output (build logs, test runners); use bottom splits.
- **Do** keep every token colour bound to its fixed Tetris role; never repurpose a piece colour for an unrelated UI element.
- **Don't** clutter the gutter with too many conflicting signs; prioritize errors over warnings.
