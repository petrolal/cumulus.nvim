-- TetraVim JVM build-tool detection
--
-- Walks up from a starting path looking for the marker file that identifies a
-- Maven or Gradle project root. Pure Neovim (vim.fs); no external process.

local M = {}

local GRADLE_MARKERS = { "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts" }
local MAVEN_MARKERS = { "pom.xml" }

--- Detect the JVM build tool governing `path`.
---@param path? string Starting directory or file (default: cwd)
---@return "maven"|"gradle"|nil tool
---@return string|nil root Absolute project root directory when a tool is found
function M.detect(path)
  path = path or vim.fn.getcwd()

  local gradle = vim.fs.find(GRADLE_MARKERS, { upward = true, path = path, type = "file" })[1]
  local maven = vim.fs.find(MAVEN_MARKERS, { upward = true, path = path, type = "file" })[1]

  -- Prefer the marker closest to `path`; on a tie Gradle wins (wrapper-driven repos).
  if gradle and maven then
    if #vim.fs.dirname(gradle) >= #vim.fs.dirname(maven) then
      return "gradle", vim.fs.dirname(gradle)
    end
    return "maven", vim.fs.dirname(maven)
  elseif gradle then
    return "gradle", vim.fs.dirname(gradle)
  elseif maven then
    return "maven", vim.fs.dirname(maven)
  end

  return nil, nil
end

return M
