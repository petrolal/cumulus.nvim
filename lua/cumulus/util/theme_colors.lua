-- Theme Color Cache & Initialization (Story 5.1)
--
-- Provides centralized theme color extraction from engine-generated highlights.
-- Used by lualine, bufferline, and other UI plugins to maintain consistent colors
-- across theme switches without duplicating palette data.

local M = {}

-- ============================================================================
-- Fallback Colors
-- ============================================================================

M.DEFAULT_COLORS = {
  bg = "#071521",
  fg = "#E0E6ED",
  fg_dim = "#94A3B8",
  bg_cursorline = "#122232",
  statusline_bg = "#020A12",
  primary_color = "#FF9900", -- AWS orange default
  secondary = "#38BDF8",
  purple = "#A855F7",
  error = "#EF4444",
}

-- Cache stores the current theme colors
M.cache = vim.deepcopy(M.DEFAULT_COLORS)

-- ============================================================================
-- Color Initialization & Refresh
-- ============================================================================

--- Extract theme colors from engine-provided highlights (via manage_theme response).
--- Uses pcall for safe engine calls with nil guards and type validation.
---@param provider string Cloud provider: "aws", "azure", "gcp", "oci"
---@return nil
function M.init_theme_colors(provider)
  -- Nil guard: check engine module is available
  local engine = require("cumulus.util.engine")
  if not engine or not engine.is_available() then
    M.cache = vim.deepcopy(M.DEFAULT_COLORS)
    return
  end

  -- Safe call to manage_theme("set") which returns highlights in response
  local ok, result = pcall(engine.manage_theme, "set", { theme = provider })
  if not ok or not result then
    M.cache = vim.deepcopy(M.DEFAULT_COLORS)
    return
  end

  -- Type validation: ensure result.highlights is a table before indexing
  if not result.highlights or type(result.highlights) ~= "table" then
    M.cache = vim.deepcopy(M.DEFAULT_COLORS)
    return
  end

  -- Extract colors from highlights with safe fallback logic
  M.cache.bg = (result.highlights.Normal and result.highlights.Normal.bg) or M.DEFAULT_COLORS.bg
  M.cache.fg = (result.highlights.Normal and result.highlights.Normal.fg) or M.DEFAULT_COLORS.fg
  M.cache.fg_dim = (result.highlights.Comment and result.highlights.Comment.fg) or M.DEFAULT_COLORS.fg_dim
  M.cache.bg_cursorline = (result.highlights.CursorLine and result.highlights.CursorLine.bg)
    or M.DEFAULT_COLORS.bg_cursorline
  M.cache.statusline_bg = (result.highlights.StatusLine and result.highlights.StatusLine.bg)
    or M.DEFAULT_COLORS.statusline_bg

  -- Extract provider-specific primary color from CursorLineNr (each provider has distinct color)
  local cursor_line_nr_fg = result.highlights.CursorLineNr and result.highlights.CursorLineNr.fg
  M.cache.primary_color = cursor_line_nr_fg or M.DEFAULT_COLORS.primary_color

  -- Extract secondary, purple, error with safe access
  M.cache.secondary = (result.highlights.Type and result.highlights.Type.fg) or M.DEFAULT_COLORS.secondary
  M.cache.purple = (result.highlights.Keyword and result.highlights.Keyword.fg) or M.DEFAULT_COLORS.purple
  M.cache.error = (result.highlights.Error and result.highlights.Error.fg) or M.DEFAULT_COLORS.error
end

--- Refresh theme cache when theme is changed. Detects provider from theme name.
---@param theme_name string Theme name (e.g., "aws-theme", "azure-theme")
---@return nil
function M.refresh_cache(theme_name)
  if not theme_name then
    M.cache = vim.deepcopy(M.DEFAULT_COLORS)
    return
  end
  local clean_theme = theme_name:gsub("%-theme$", "")
  M.init_theme_colors(clean_theme)
end

return M
