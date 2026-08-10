-- Cumulus Multi-Module Topology Helper (SPEC-008 & SPEC-017)
--
-- Discovers sub-modules in Maven (`pom.xml`) and Gradle (`settings.gradle`)
-- repositories using the compiled Rust `cumulus-core` helper (with Lua fallback).

local M = {}

--- Parse sub-modules from Maven root pom.xml
---@param pom_path? string
---@return table[] Array of { name = string, path = string }
function M.get_maven_modules(pom_path)
  pom_path = pom_path or (vim.fn.getcwd() .. "/pom.xml")
  if vim.fn.filereadable(pom_path) == 0 then
    return {}
  end

  local rust = require("cumulus.util.rust")
  if rust.is_available() then
    local rust_mods = rust.parse_modules("maven", pom_path)
    if rust_mods then
      return rust_mods
    end
  end

  -- Fallback Lua parser
  local content = table.concat(vim.fn.readfile(pom_path), "\n")
  local modules = {}
  local in_modules = false
  for line in content:gmatch("[^\r\n]+") do
    if line:match("<modules>") then
      in_modules = true
    elseif line:match("</modules>") then
      in_modules = false
    elseif in_modules then
      local mod = line:match("<module>%s*(.-)%s*</module>")
      if mod and mod ~= "" then
        local dir = vim.fn.fnamemodify(pom_path, ":p:h") .. "/" .. mod
        table.insert(modules, { name = mod, path = dir })
      end
    end
  end
  return modules
end

--- Parse sub-modules from Gradle root settings.gradle / settings.gradle.kts
---@param settings_path? string
---@return table[] Array of { name = string, path = string }
function M.get_gradle_modules(settings_path)
  settings_path = settings_path or (vim.fn.getcwd() .. "/settings.gradle")
  if vim.fn.filereadable(settings_path) == 0 then
    settings_path = vim.fn.getcwd() .. "/settings.gradle.kts"
  end
  if vim.fn.filereadable(settings_path) == 0 then
    return {}
  end

  local rust = require("cumulus.util.rust")
  if rust.is_available() then
    local rust_mods = rust.parse_modules("gradle", settings_path)
    if rust_mods then
      return rust_mods
    end
  end

  -- Fallback Lua parser
  local content = table.concat(vim.fn.readfile(settings_path), "\n")
  local modules = {}
  for line in content:gmatch("[^\r\n]+") do
    local mod = line:match("include%s*%(?['\"]([^'\"]+)['\"]%)?")
    if mod and mod ~= "" then
      local clean_mod = mod:gsub("^:", "")
      local rel_path = clean_mod:gsub(":", "/")
      local dir = vim.fn.fnamemodify(settings_path, ":p:h") .. "/" .. rel_path
      table.insert(modules, { name = clean_mod, path = dir })
    end
  end
  return modules
end

return M
