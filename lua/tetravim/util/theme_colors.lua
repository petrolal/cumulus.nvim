-- Theme Colour Cache & Initialisation
--
-- Derives the handful of UI accent colours that lualine / bufferline need
-- from the currently-applied highlight groups, so those plugins don't
-- duplicate palette data. Colours come from the canonical TetraVim
-- "Tetris" highlight map (`tetravim.theme.tetris`); `M.DEFAULT_COLORS`
-- mirrors that palette as a literal fallback if the module fails to load.

local M = {}

-- ============================================================================
-- Fallback Colors
-- ============================================================================

-- Mirrors the canonical TetraVim "Tetris" palette
-- (see lua/tetravim/theme/tetris.lua). Kept as literals so UI plugins have
-- correct colours even if that module fails to load.
M.DEFAULT_COLORS = {
  bg = "#111216", -- Background
  fg = "#BCBEC4", -- Foreground
  fg_dim = "#5C6370", -- Muted gray (comments / inactive statusline text)
  bg_cursorline = "#1E1F26", -- Surface
  statusline_bg = "#1E1F26", -- Surface
  primary_color = "#00F0F0", -- I-piece cyan — default accent (CursorLineNr)
  secondary = "#00F000", -- S-piece green — insert-mode statusline accent
  purple = "#A000F0", -- T-piece — keywords
  cyan = "#00F0F0", -- I-piece
  yellow = "#F0F000", -- O-piece — functions
  green = "#00F000", -- S-piece — annotations
  orange = "#FF7F00", -- L-piece — strings
  blue = "#5B8CFF", -- J-piece (AA-readable tint) — constants
  error = "#F00000", -- Z-piece — diagnostics
}

-- Cache stores the current theme colors
M.cache = vim.deepcopy(M.DEFAULT_COLORS)

-- ============================================================================
-- Color Initialization & Refresh
-- ============================================================================

--- Recompute the cached UI colours from the active highlight groups.
--- Falls back to the Tetris highlight map, then to `M.DEFAULT_COLORS`.
---@return nil
function M.init_theme_colors()
  local highlights = _G._tetravim_current_highlights or {}

  if vim.tbl_isempty(highlights) then
    -- Fall back to the canonical Tetris highlight map before giving up.
    local ok, tetris = pcall(require, "tetravim.theme.tetris")
    if ok then
      highlights = tetris.highlights()
    end
  end

  if vim.tbl_isempty(highlights) then
    M.cache = vim.deepcopy(M.DEFAULT_COLORS)
    return
  end

  -- Extract colors from highlights with safe fallback logic
  M.cache.bg = (highlights.Normal and highlights.Normal.bg) or M.DEFAULT_COLORS.bg
  M.cache.fg = (highlights.Normal and highlights.Normal.fg) or M.DEFAULT_COLORS.fg
  M.cache.fg_dim = (highlights.Comment and highlights.Comment.fg) or M.DEFAULT_COLORS.fg_dim
  M.cache.bg_cursorline = (highlights.CursorLine and highlights.CursorLine.bg) or M.DEFAULT_COLORS.bg_cursorline
  M.cache.statusline_bg = (highlights.StatusLine and highlights.StatusLine.bg) or M.DEFAULT_COLORS.statusline_bg

  -- Primary accent (active line number, statusline mode badge, bufferline indicator)
  local cursor_line_nr_fg = highlights.CursorLineNr and highlights.CursorLineNr.fg
  M.cache.primary_color = cursor_line_nr_fg or M.DEFAULT_COLORS.primary_color

  -- Extract secondary, purple, error with safe access
  M.cache.secondary = (highlights.DiagnosticHint and highlights.DiagnosticHint.fg) or M.DEFAULT_COLORS.secondary
  M.cache.purple = (highlights.Keyword and highlights.Keyword.fg) or M.DEFAULT_COLORS.purple
  M.cache.error = (highlights.Error and highlights.Error.fg) or M.DEFAULT_COLORS.error

  -- Piece colours for UI plugins (bufferline / lualine accents).
  M.cache.cyan = (highlights.Type and highlights.Type.fg) or M.DEFAULT_COLORS.cyan
  M.cache.yellow = (highlights["@function"] and highlights["@function"].fg)
    or (highlights.Function and highlights.Function.fg)
    or M.DEFAULT_COLORS.yellow
  M.cache.green = (highlights["@attribute"] and highlights["@attribute"].fg) or M.DEFAULT_COLORS.green
  M.cache.orange = (highlights.String and highlights.String.fg) or M.DEFAULT_COLORS.orange
  M.cache.blue = (highlights.Constant and highlights.Constant.fg) or M.DEFAULT_COLORS.blue
end

--- Refresh the cached UI colours from the active highlights.
---@param _ any Ignored; retained for call-site compatibility.
---@return nil
function M.refresh_cache(_)
  M.init_theme_colors()
end

return M
