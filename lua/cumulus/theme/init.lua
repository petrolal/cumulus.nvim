-- Cumulus Cloud Theme Switcher & Persistence (Story 31.2 & Story 5.4/6.1)
--
-- Theme precedence: delegates to `cumulus-engine manage-theme` which reads
-- ~/.config/cumulus/theme/state (shared dotfiles) or falls back to Neovim internal state.
local M = {}

local themes = {
  { name = "aws-theme", label = "🟧 AWS Cloud Theme" },
  { name = "azure-theme", label = "🟦 Microsoft Azure Theme" },
  { name = "gcp-theme", label = "🟩 Google Cloud Platform (GCP) Theme" },
  { name = "oci-theme", label = "🟥 Oracle Cloud Infrastructure (OCI) Theme" },
}

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
  local ok, _ = pcall(vim.cmd, "colorscheme " .. theme_name)
  if not ok then
    vim.notify("Failed to set colorscheme: " .. theme_name, vim.log.levels.ERROR)
    return
  end

  vim.notify("Cloud theme set to: " .. theme_name, vim.log.levels.INFO)

  local clean_theme = theme_name:gsub("%-theme$", "")
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
  local ok, _ = pcall(vim.cmd, "colorscheme " .. theme)
  if not ok then
    pcall(vim.cmd, "colorscheme aws-theme")
  end
end

return M
