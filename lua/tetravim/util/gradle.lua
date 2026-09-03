-- TetraVim Gradle Project Utilities
local M = {}

--- Locate build.gradle or build.gradle.kts in directory
---@param dir string|nil Directory to check (defaults to cwd)
---@return boolean
function M.find_gradle(dir)
  dir = dir or vim.fn.getcwd()
  if type(dir) ~= "string" or dir == "" then
    return false
  end
  return vim.fn.glob(dir .. "/build.gradle") ~= "" or vim.fn.glob(dir .. "/build.gradle.kts") ~= ""
end

return M
