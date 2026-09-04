-- TetraVim SonarQube / SonarLint helpers (Epic 6, Story 6.1)
--
-- Resolves everything the SonarLint language server needs to attach for
-- Java / Kotlin / Scala buffers: the Mason-installed `sonarlint-language-server`
-- binary, its bundled analyzer jars, and any project-specific quality
-- profile declared in a `sonar-project.properties` at the project root.
--
-- `settings_from_properties` / `project_key` are pure text parsers (the
-- same read-and-transform discipline as util/grpc.lua's output walkers) --
-- no rule logic, no analysis, and no SonarLint protocol is reimplemented
-- here; that all lives in the language server.

local M = {}

-- The filetypes the SonarLint LS is wired for. Scala rules only work in
-- SonarQube connected mode (no free standalone analyzer is bundled) -- it
-- is still listed so a connected-mode project gets diagnostics.
M.FILETYPES = { "java", "kotlin", "scala" }

--- The Mason package directory for the SonarLint language server.
---@return string
function M.mason_pkg_root()
  return vim.fn.stdpath("data") .. "/mason/packages/sonarlint-language-server"
end

--- Whether the `sonarlint-language-server` binary is callable.
---@return boolean
function M.has_language_server()
  return vim.fn.executable("sonarlint-language-server") == 1
end

--- Sorted list of the analyzer jars bundled with the Mason package
--- (`extension/analyzers/*.jar` -- sonarjava, sonarkotlin, ...). Empty when
--- the package is not installed; the LS then falls back to connected-mode
--- analyzers or its own defaults.
---@return string[]
function M.analyzer_paths()
  local dir = M.mason_pkg_root() .. "/extension/analyzers"
  local jars = vim.fn.glob(dir .. "/*.jar", true, true)
  if type(jars) ~= "table" then
    jars = {}
  end
  table.sort(jars)
  return jars
end

-- Whether to pass the Mason-bundled analyzer jars explicitly via `-analyzers`.
-- The SonarLint LS discovers its bundled analyzers on its own; we only pass
-- them when this is set so a future Mason package layout change (jars moved,
-- renamed, or an incompatible `-analyzers` contract) degrades to the LS's own
-- discovery instead of a hard cmd error. Flip to false to opt out.
M.USE_BUNDLED_ANALYZERS = true

--- The full `sonarlint-language-server` command array (`-stdio`, plus
--- `-analyzers <jar>...` when any are bundled and `USE_BUNDLED_ANALYZERS` is
--- set), or nil when the binary is absent.
---@return string[]|nil
function M.language_server_cmd()
  if not M.has_language_server() then
    return nil
  end
  local cmd = { "sonarlint-language-server", "-stdio" }
  local jars = M.USE_BUNDLED_ANALYZERS and M.analyzer_paths() or {}
  if #jars > 0 then
    table.insert(cmd, "-analyzers")
    for _, jar in ipairs(jars) do
      table.insert(cmd, jar)
    end
  end
  return cmd
end

--- Parse a `sonar-project.properties` text blob into a flat key/value map.
--- `#` / `!` comment lines and blank lines are skipped; keys and values are
--- trimmed; both `key=value` and `key:value` separators are accepted. Pure
--- -- no file IO.
---@param text string
---@return table<string, string>
function M.settings_from_properties(text)
  local out = {}
  for _, raw in ipairs(vim.split(text or "", "\n", { plain = true })) do
    local line = vim.trim(raw)
    if line ~= "" and not line:match("^[#!]") then
      local k, v = line:match("^([^=:]+)[=:](.*)$")
      if k and vim.trim(k) ~= "" then
        out[vim.trim(k)] = vim.trim(v)
      end
    end
  end
  return out
end

--- The `sonar.projectKey` declared in a properties blob, or nil.
---@param text string
---@return string|nil
function M.project_key(text)
  return M.settings_from_properties(text)["sonar.projectKey"]
end

--- Locate + parse the nearest `sonar-project.properties`, searching `root`
--- (default: cwd) and then every parent directory up to the filesystem root
--- -- a monorepo commonly keeps it a level or two above the buffer's module.
--- Returns the settings map, or nil when no file is found / it is unreadable.
---@param root string|nil
---@return table<string, string>|nil
function M.find_project_settings(root)
  root = root or vim.fn.getcwd()
  local found = vim.fs.find("sonar-project.properties", {
    path = root,
    upward = true,
    type = "file",
    limit = 1,
  })
  local path = found[1]
  if not path then
    return nil
  end
  local ok, lines = pcall(vim.fn.readfile, path)
  if not ok then
    return nil
  end
  return M.settings_from_properties(table.concat(lines, "\n"))
end

return M
