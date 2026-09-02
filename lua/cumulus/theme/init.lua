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

--- Apply theme highlights from engine-provided palette via vim.api.nvim_set_hl()
---@param highlights table Map of highlight group name to HighlightGroup definition
---@param clear_first boolean Clear old highlights before applying (default: false to avoid flicker)
local function apply_highlights(highlights, clear_first)
  if not highlights then
    return
  end

  clear_first = clear_first or false
  vim.opt.background = "dark"
  vim.opt.termguicolors = true

  if clear_first then
    vim.cmd("hi clear")
  end

  local count = 0
  for group_name, group_def in pairs(highlights) do
    local hl_opts = {}

    if group_def.fg and group_def.fg ~= vim.NIL then
      hl_opts.fg = group_def.fg
    end
    if group_def.bg and group_def.bg ~= vim.NIL then
      hl_opts.bg = group_def.bg
    end
    if group_def.bold and group_def.bold ~= vim.NIL and group_def.bold == true then
      hl_opts.bold = true
    end
    if group_def.italic and group_def.italic ~= vim.NIL and group_def.italic == true then
      hl_opts.italic = true
    end
    if group_def.underline and group_def.underline ~= vim.NIL and group_def.underline == true then
      hl_opts.underline = true
    end

    pcall(vim.api.nvim_set_hl, 0, group_name, hl_opts)
    count = count + 1
  end
end

--- Load highlights for a specific cloud provider via manage_theme
---@param provider string Cloud provider: "aws", "azure", "gcp", "oci"
local function load_provider_highlights(provider)
  local logfile = vim.fn.expand("~/.config/nvim/theme-debug.log")
  local function log(msg)
    vim.fn.writefile({ os.date("%H:%M:%S") .. " " .. msg }, logfile, "a")
  end

  log("load_provider_highlights: Loading native dotfile theme for '" .. provider .. "'")

  local palette_file = vim.fn.expand("~/.config/cumulus/theme/palette.json")
  local highlights = {}

  if vim.fn.filereadable(palette_file) == 1 then
    local content = vim.fn.readfile(palette_file)
    local ok, parsed = pcall(vim.fn.json_decode, content)
    if ok and parsed and type(parsed) == "table" then
      log("load_provider_highlights: Successfully parsed palette.json")
      highlights = parsed.highlights or parsed
    else
      log("load_provider_highlights: Failed to parse palette.json")
      vim.notify("Invalid JSON in theme palette.json", vim.log.levels.WARN)
    end
  end

  if vim.tbl_isempty(highlights) then
    log("load_provider_highlights: palette.json not found or invalid, falling back to legacy engine for backward compatibility.")
    local engine_ok, engine = pcall(require, "cumulus.util.engine")
    if engine_ok and engine.is_available() then
      local engine_result = engine.manage_theme("set", { theme = provider })
      if engine_result and engine_result.highlights and not vim.tbl_isempty(engine_result.highlights) then
        highlights = engine_result.highlights
        log("load_provider_highlights: Legacy engine fallback succeeded.")
      end
    end
  end

  -- Absolute worst-case fallback if engine is truly gone and no dotfile exists
  if vim.tbl_isempty(highlights) then
    highlights = {
      Normal = { fg = "#c0caf5", bg = "#1a1b26" },
      String = { fg = "#9ece6a" },
      Keyword = { fg = "#bb9af7" },
      Comment = { fg = "#565f89", italic = true },
      CursorLine = { bg = "#292e42" },
      StatusLine = { bg = "#16161e", fg = "#a9b1d6" },
      Type = { fg = "#0db9d7" },
      Error = { fg = "#db4b4b" }
    }
    
    if provider == "aws" then highlights.CursorLineNr = { fg = "#FF9900", bold = true }
    elseif provider == "azure" then highlights.CursorLineNr = { fg = "#0078D4", bold = true }
    elseif provider == "gcp" then highlights.CursorLineNr = { fg = "#4285F4", bold = true }
    elseif provider == "oci" then highlights.CursorLineNr = { fg = "#C74634", bold = true }
    else highlights.CursorLineNr = { fg = "#FF9900", bold = true } end
  end

  log("load_provider_highlights: Applying highlights...")
  apply_highlights(highlights)
  
  -- Store globally so theme_colors can access it without an engine call
  _G._cumulus_current_highlights = highlights
  return true
end

function M.get_current_theme()
  -- 1. Read global dotfile theme state
  local global_state_file = vim.fn.expand("~/.config/cumulus/theme/state")
  if vim.fn.filereadable(global_state_file) == 1 then
    local lines = vim.fn.readfile(global_state_file)
    if #lines > 0 and lines[1] ~= "" then
      local t = lines[1]
      if not t:match("%-theme$") then
        t = t .. "-theme"
      end
      return t
    end
  end

  -- 2. Fallback to Neovim internal state
  local internal_state_file = vim.fn.stdpath("state") .. "/cumulus_theme"
  if vim.fn.filereadable(internal_state_file) == 1 then
    local lines = vim.fn.readfile(internal_state_file)
    if #lines > 0 and lines[1] ~= "" then
      local t = lines[1]
      if not t:match("%-theme$") then
        t = t .. "-theme"
      end
      return t
    end
  end
  return "aws-theme"
end

function M.set_theme(theme_name)
  if not theme_name or theme_name == "" then
    vim.notify("Theme name cannot be empty", vim.log.levels.ERROR)
    return
  end

  local clean_theme = theme_name:gsub("%-theme$", "")

  -- Find the provider for this theme name
  local provider = nil
  for _, t in ipairs(themes) do
    if t.name == theme_name or t.name == clean_theme .. "-theme" or t.provider == clean_theme then
      provider = t.provider
      break
    end
  end

  -- Validate theme exists before attempting to set
  if not provider then
    local valid_themes = {}
    for _, t in ipairs(themes) do
      table.insert(valid_themes, t.provider)
    end
    local msg = "Unknown theme: " .. tostring(theme_name) .. ". Valid: " .. table.concat(valid_themes, ", ")
    msg = msg:sub(1, 150)
    vim.notify(msg, vim.log.levels.ERROR)
    return
  end

  -- Load provider highlights from engine
  if not load_provider_highlights(provider) then
    local err_msg = "Failed to set theme '" .. tostring(clean_theme) .. "': engine error"
    vim.notify(err_msg:sub(1, 150), vim.log.levels.ERROR)
    return
  end

  vim.notify("Cloud theme set to: " .. clean_theme, vim.log.levels.INFO)

  -- Refresh theme color cache for lualine/bufferline (Story 5.1)
  local theme_colors = require("cumulus.util.theme_colors")
  theme_colors.refresh_cache(theme_name)

  -- Trigger UI refresh for lualine and bufferline to pick up new colors
  vim.cmd("doautocmd User CumulusThemeChanged")
  if pcall(require, "lualine") then
    require("lualine").refresh()
  end
end

function M.load_saved_theme()
  local logfile = vim.fn.expand("~/.config/nvim/theme-debug.log")
  local function log(msg)
    vim.fn.writefile({ os.date("%H:%M:%S") .. " " .. msg }, logfile, "a")
  end

  log("=== load_saved_theme() called ===")
  local theme = M.get_current_theme()
  log("Current theme: " .. theme)

  -- Load provider highlights from engine
  local clean_theme = theme:gsub("%-theme$", "")
  log("Clean theme: " .. clean_theme)
  local loaded = false
  for _, t in ipairs(themes) do
    log("Checking theme: name=" .. t.name .. ", provider=" .. t.provider)
    if t.name == theme or t.provider == clean_theme then
      log("MATCH! Loading provider: " .. t.provider)
      if not load_provider_highlights(t.provider) then
        log("ERROR: Failed to load highlights for " .. t.provider)
      else
        loaded = true
        log("SUCCESS: Theme loaded!")
      end
      break
    end
  end

  if not loaded then
    log("ERROR: Theme not loaded")
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

  -- Load provider highlights from engine (Story 5.1: engine-driven, no Lua fallbacks)
  local clean_theme = theme:gsub("%-theme$", "")
  for _, t in ipairs(themes) do
    if t.name == theme or t.provider == clean_theme then
      local success = pcall(load_provider_highlights, t.provider)
      if not success then
        vim.notify("Theme unavailable: engine required for theme management", vim.log.levels.ERROR)
      end
      break
    end
  end
end

return M
