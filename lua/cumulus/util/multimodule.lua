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

--- Select and jump to a sub-module build file via Rust backend + UI picker.
---@param tool? "maven"|"gradle"
function M.select_module(tool)
  tool = tool or "maven"
  local modules = (tool == "maven") and M.get_maven_modules() or M.get_gradle_modules()

  if not modules or #modules == 0 then
    vim.notify("No " .. tool:sub(1, 1):upper() .. tool:sub(2) .. " sub-modules found", vim.log.levels.WARN)
    return
  end

  vim.ui.select(modules, {
    prompt = "Select " .. tool:sub(1, 1):upper() .. tool:sub(2) .. " Sub-Module:",
    format_item = function(item)
      return string.format("%s (%s)", item.name, item.path)
    end,
  }, function(choice)
    if choice then
      local build_file = choice.path
      if vim.fn.isdirectory(build_file) == 1 then
        local pom = build_file .. "/pom.xml"
        local gradle = build_file .. "/build.gradle"
        local gradle_kts = build_file .. "/build.gradle.kts"

        if vim.fn.filereadable(pom) == 1 then
          build_file = pom
        elseif vim.fn.filereadable(gradle) == 1 then
          build_file = gradle
        elseif vim.fn.filereadable(gradle_kts) == 1 then
          build_file = gradle_kts
        end
      end

      if vim.fn.filereadable(build_file) == 1 then
        vim.cmd("edit " .. vim.fn.fnameescape(build_file))
      else
        vim.notify("Build file not found for module: " .. choice.name, vim.log.levels.WARN)
      end
    end
  end)
end

--- Get topological build order DAG for multi-module project via Rust backend (SPEC-028).
---@param root_dir? string
---@return table[]|nil
function M.get_build_order(root_dir)
  root_dir = root_dir or vim.fn.getcwd()
  local rust = require("cumulus.util.rust")
  if rust.is_available() then
    return rust.compute_build_order(root_dir)
  end
  return nil
end

return M
