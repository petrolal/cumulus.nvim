-- TetraVim Background Transparency Toggle
--
-- Blanks the background on the editor + floating-window chrome groups so a
-- translucent terminal shows through, and restores the "Tetris" surfaces
-- on the way back. Syntax / accent foregrounds are never touched.
--
-- Toggled from `<leader>ut` (see core/keymaps.lua).

local M = {}

M.enabled = false

-- Groups whose background is cleared. Foreground-only groups (syntax,
-- diagnostics) are deliberately absent -- only surfaces go transparent.
local GROUPS = {
  "Normal",
  "NormalNC",
  "NormalFloat",
  "FloatBorder",
  "FloatTitle",
  "SignColumn",
  "LineNr",
  "FoldColumn",
  "EndOfBuffer",
  "MsgArea",
  "TelescopeNormal",
  "TelescopeBorder",
  "TelescopePromptNormal",
  "SnacksNormal",
  "SnacksBackdrop",
  "SnacksDashboardNormal",
  "WhichKeyFloat",
  "NoiceCmdlinePopup",
  "WinBar",
  "WinBarNC",
}

--- Clear the background on every managed group (only when enabled).
--- Safe to call repeatedly -- it re-reads each group's current spec so it
--- composes with a freshly re-applied theme.
function M.apply()
  if not M.enabled then
    return
  end
  for _, group in ipairs(GROUPS) do
    local ok, current = pcall(vim.api.nvim_get_hl, 0, { name = group, link = false })
    if ok then
      current.bg = "NONE"
      current.ctermbg = "NONE"
      pcall(vim.api.nvim_set_hl, 0, group, current)
    end
  end
end

--- Flip transparency on/off. Re-applies the base Tetris palette first so
--- switching *off* restores the opaque surfaces, then re-blanks if the new
--- state is "on".
function M.toggle()
  M.enabled = not M.enabled

  local ok, theme = pcall(require, "tetravim.theme")
  if ok then
    theme.apply()
  end
  M.apply()

  vim.notify("Transparency " .. (M.enabled and "ON" or "OFF"), vim.log.levels.INFO)
end

return M
