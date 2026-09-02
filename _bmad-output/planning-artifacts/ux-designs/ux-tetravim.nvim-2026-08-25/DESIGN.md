---
status: final
updated: 2026-08-25
colors:
  base: Catppuccin Macchiato / Tokyonight Storm (User preference)
  aws: '#FF9900'
  azure: '#0078D4'
  gcp: '#4285F4'
  oci: '#C74634'
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

**TetraVim IDE** is a purposeful, distraction-free environment for enterprise JVM and cloud engineers. The aesthetic is "professional cyberpunk"—dark, high-contrast, and deeply functional. It leverages dynamic theming to provide immediate, subconscious context about the active cloud environment.

# Colors

The base theme relies on established dark modes (Catppuccin or Tokyonight) for excellent readability during long sessions. 

**Dynamic Cloud Overlays [ASSUMPTION]:**
When a user switches contexts via `<leader>ct`, specific UI elements (statusline accents, active window borders, selected items) adapt to match the cloud provider:
- **AWS**: Amazon Orange (`#FF9900`) accents.
- **Azure**: Microsoft Blue (`#0078D4`) accents.
- **GCP**: Google Blue (`#4285F4`) accents, with complementary red/yellow/green for specific states.
- **OCI**: Oracle Red (`#C74634`) accents.

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
Minimalist. Left: Mode, Git branch, active Cloud Theme indicator. Center: Filepath. Right: LSP status, Diagnostics count, Location (Row/Col).

# Do's and Don'ts

- **Do** use floating windows for temporary context (hover, pickers).
- **Don't** use floating windows for long-running output (build logs, test runners); use bottom splits.
- **Do** ensure the active cloud theme is visible at a glance (via statusline or borders).
- **Don't** clutter the gutter with too many conflicting signs; prioritize errors over warnings.
