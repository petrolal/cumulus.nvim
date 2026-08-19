-- Cumulus Cloud Theme Switcher & Persistence (Story 31.2 & Story 5.4/6.1)
--
-- Theme precedence: delegates to `cumulus-engine manage-theme` which reads
-- ~/.config/cumulus/theme/state (shared dotfiles) or falls back to Neovim internal state.
-- Highlights are generated dynamically via `cumulus-engine generate-theme-highlights` and applied
-- directly to Neovim without manual color math.
local M = {}

local themes = {
  { name = "aws-theme", label = "🟧 AWS Cloud Theme", provider = "aws" },
  { name = "azure-theme", label = "🟦 Microsoft Azure Theme", provider = "azure" },
  { name = "gcp-theme", label = "🟩 Google Cloud Platform (GCP) Theme", provider = "gcp" },
  { name = "oci-theme", label = "🟥 Oracle Cloud Infrastructure (OCI) Theme", provider = "oci" },
}

--- Apply theme highlights from engine-generated palette via vim.api.nvim_set_hl()
---@param highlights table Map of highlight group name to HighlightGroup definition
local function apply_highlights(highlights)
  if not highlights then
    return
  end
  for group_name, group_def in pairs(highlights) do
    local hl_opts = {}
    if group_def.fg then
      hl_opts.fg = group_def.fg
    end
    if group_def.bg then
      hl_opts.bg = group_def.bg
    end
    if group_def.bold then
      hl_opts.bold = true
    end
    if group_def.italic then
      hl_opts.italic = true
    end
    if group_def.underline then
      hl_opts.underline = true
    end
    pcall(vim.api.nvim_set_hl, 0, group_name, hl_opts)
  end
end

--- Load highlights for a specific cloud provider
---@param provider string Cloud provider: "aws", "azure", "gcp", "oci"
local function load_provider_highlights(provider)
  local engine = require("cumulus.util.engine")
  if not engine.is_available() then
    return false
  end

  local result = engine.generate_theme_highlights(provider)
  if result and result.highlights then
    apply_highlights(result.highlights)
    return true
  end
  return false
end

function M.get_current_theme()
  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local res = engine.manage_theme("get")
    if res and res.theme then
      local t = res.theme
      if not t:match("%-theme$") then
        t = t .. "-theme"
      end
      return t
    end
  end

  local internal_state_file = vim.fn.stdpath("state") .. "/cumulus_theme"
  if vim.fn.filereadable(internal_state_file) == 1 then
    local lines = vim.fn.readfile(internal_state_file)
    if #lines > 0 and lines[1] ~= "" then
      return lines[1]
    end
  end
  return "aws-theme"
end

function M.set_theme(theme_name)
  local clean_theme = theme_name:gsub("%-theme$", "")

  -- Find the provider for this theme name
  local provider = nil
  for _, t in ipairs(themes) do
    if t.name == theme_name or t.name == clean_theme .. "-theme" then
      provider = t.provider
      break
    end
  end

  -- Load provider highlights from engine
  if provider then
    if not load_provider_highlights(provider) then
      vim.notify("Warning: Could not load highlights from engine for " .. provider, vim.log.levels.WARN)
    end
  end

  -- Set colorscheme as fallback (for base colors and additional customizations)
  local ok, _ = pcall(vim.cmd, "colorscheme " .. theme_name)
  if not ok then
    vim.notify("Failed to set colorscheme: " .. theme_name, vim.log.levels.ERROR)
    return
  end

  vim.notify("Cloud theme set to: " .. theme_name, vim.log.levels.INFO)

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    engine.manage_theme("set", { theme = clean_theme })
  else
    local state_file = vim.fn.stdpath("state") .. "/cumulus_theme"
    vim.fn.mkdir(vim.fn.fnamemodify(state_file, ":h"), "p")
    vim.fn.writefile({ theme_name }, state_file)
  end
end

function M.load_saved_theme()
  local theme = M.get_current_theme()

  -- Load provider highlights from engine
  local clean_theme = theme:gsub("%-theme$", "")
  for _, t in ipairs(themes) do
    if t.name == theme or t.provider == clean_theme then
      load_provider_highlights(t.provider)
      break
    end
  end

  local ok, _ = pcall(vim.cmd, "colorscheme " .. theme)
  if not ok then
    pcall(vim.cmd, "colorscheme aws-theme")
  end
end

function M.select_theme()
  local items = {}
  for _, t in ipairs(themes) do
    table.insert(items, t.label)
  end

  vim.ui.select(items, {
    prompt = "Select Cumulus Cloud Theme:",
    format_item = function(item)
      return item
    end,
  }, function(choice)
    if not choice then
      return
    end

    for _, t in ipairs(themes) do
      if t.label == choice then
        M.set_theme(t.name)
        break
      end
    end
  end)
end

function M.setup(opts)
  opts = opts or {}
  local theme = opts.theme or M.get_current_theme()

  -- Load provider highlights from engine
  local clean_theme = theme:gsub("%-theme$", "")
  for _, t in ipairs(themes) do
    if t.name == theme or t.provider == clean_theme then
      load_provider_highlights(t.provider)
      break
    end
  end

  local ok, _ = pcall(vim.cmd, "colorscheme " .. theme)
  if not ok then
    pcall(vim.cmd, "colorscheme aws-theme")
  end
end

return M
