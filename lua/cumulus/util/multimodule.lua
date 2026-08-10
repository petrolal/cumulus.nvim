-- Cumulus Multi-Module Topology Helper (SPEC-008 & SPEC-017)
--
-- Architecture: Lua is a bridge only. All Maven pom.xml and Gradle settings.gradle
-- parsing is done by the cumulus-core Rust binary (parse-modules subcommand).
-- No Lua fallbacks. If the binary is missing, the function errors explicitly.

local M = {}

--- Parse sub-modules from Maven root pom.xml via Rust backend.
---@param pom_path? string
---@return table[] Array of { name = string, path = string }
function M.get_maven_modules(pom_path)
  pom_path = pom_path or (vim.fn.getcwd() .. "/pom.xml")
  if vim.fn.filereadable(pom_path) == 0 then
    return {}
  end

  local rust = require("cumulus.util.rust")
  local modules = rust.parse_modules("maven", pom_path)
  if modules then
    return modules
  end

  error("cumulus-core: failed to parse Maven modules from " .. pom_path)
end

--- Parse sub-modules from Gradle root settings.gradle via Rust backend.
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
  local modules = rust.parse_modules("gradle", settings_path)
  if modules then
    return modules
  end

  error("cumulus-core: failed to parse Gradle modules from " .. settings_path)
end

return M
