-- Cumulus Git Work-Tree Guard (SPEC-4.1)
--
-- Shared entry-point guard for the git conflict/compare feature cluster.
-- Every <leader>gc* keymap in tools-diffview.lua runs M.guard() first so a
-- missing `git` binary or a non-repo cwd produces one clear notification
-- (never a raw stacktrace). Story 4.2 reuses this same guard for its forge
-- commands.
--
-- Modeled on cumulus.util.http's guard style: cheap vim.fn.executable check
-- plus a single synchronous git probe. The probe is a fast local
-- `git rev-parse` with an explicit timeout -- acceptable to :wait() on from a
-- keymap callback, and it can never hang the editor.

local ui = require("cumulus.util.ui")

local M = {}

-- Upper bound on the synchronous `git rev-parse` probe (mirrors
-- cumulus.util.http's JQ_TIMEOUT_MS pattern). A hung/pathologically slow git
-- must not freeze a keymap press.
local PROBE_TIMEOUT_MS = 2000

--- Whether the current working directory sits inside a git work tree.
--- Returns false (never errors) when `git` is not on $PATH, the probe times
--- out, or git reports anything other than a clean `true`.
---@return boolean
function M.in_worktree()
  if vim.fn.executable("git") ~= 1 then
    return false
  end
  local ok, res = pcall(function()
    return vim
      .system({ "git", "rev-parse", "--is-inside-work-tree" }, { text = true, timeout = PROBE_TIMEOUT_MS })
      :wait()
  end)
  if not ok or type(res) ~= "table" or type(res.code) ~= "number" then
    return false
  end
  return res.code == 0 and vim.trim(res.stdout or "") == "true"
end

--- Guard a git conflict/compare entry point. Returns true when it is safe to
--- proceed; otherwise emits a clear ui.notify_err and returns false.
---@return boolean
function M.guard()
  if vim.fn.executable("git") ~= 1 then
    ui.notify_err(
      "`git` is not installed or not on $PATH. Install git (e.g. `apt install git`, `brew install git`, "
        .. "`pacman -S git`) to use the conflict/compare commands."
    )
    return false
  end

  if not M.in_worktree() then
    ui.notify_err("Not a git repository -- open a file inside a git work tree first (`git init` or `cd` into one).")
    return false
  end

  return true
end

return M
