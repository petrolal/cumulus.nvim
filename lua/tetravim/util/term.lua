-- TetraVim interactive terminal runner
--
-- Runs a command in a non-blocking terminal. Prefers Snacks.terminal when the
-- distribution loads it; otherwise opens a bottom split with termopen and the
-- usual terminal keymaps.

local notify = require("tetravim.util.notify")

local M = {}

--- Run a command in an interactive, non-blocking terminal session.
---@param cmd string|string[] Command string or command argv list to execute
---@param opts? { cwd?: string, timeout?: number, title?: string, on_exit?: fun(code: number), on_stdout?: fun(data: string[]), on_stderr?: fun(data: string[]) }
function M.run_term(cmd, opts)
  opts = opts or {}
  local term_cwd = opts.cwd or vim.fn.getcwd()
  local timeout = opts.timeout or 3600000 -- default 1 hour
  local title = opts.title or "TetraVim"
  local snacks = _G.Snacks or package.loaded["snacks"]

  if snacks and snacks.terminal then
    snacks.terminal(cmd, { cwd = term_cwd })
    return
  end

  vim.cmd("botright 15split")
  local win = vim.api.nvim_get_current_win()
  local buf = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(win, buf)

  local job_id = vim.fn.termopen(cmd, {
    cwd = term_cwd,
    on_stdout = function(_, data)
      if opts.on_stdout then
        opts.on_stdout(data)
      end
    end,
    on_stderr = function(_, data)
      if opts.on_stderr then
        opts.on_stderr(data)
      end
    end,
    on_exit = function(_, code)
      if opts.on_exit then
        opts.on_exit(code)
      else
        if code == 0 or code == 130 then
          notify.notify_info("Process finished (exit code " .. code .. ")", title)
        else
          notify.notify_err("Process exited with code " .. code, title)
        end
      end
    end,
  })

  -- Set timeout timer to prevent indefinite hangs
  if timeout > 0 then
    vim.fn.timer_start(timeout, function()
      if vim.fn.jobwait({ job_id }, 0)[1] == -1 then
        vim.fn.jobstop(job_id)
        notify.notify_warn("Process timeout (" .. (timeout / 1000) .. "s), job terminated", title)
      end
    end)
  end

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

return M
