-- TetraVim Maven Project Utilities
local M = {}

--- Locate pom.xml in directory
---@param dir string|nil Directory to check (defaults to cwd)
---@return boolean
function M.find_pom(dir)
  dir = dir or vim.fn.getcwd()
  if type(dir) ~= "string" or dir == "" then
    return false
  end
  return vim.fn.glob(dir .. "/pom.xml") ~= ""
end

return M
