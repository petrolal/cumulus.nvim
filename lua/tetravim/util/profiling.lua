local M = {}

M.active_pid = nil
M.last_flamegraph = nil

local function get_profiler_cmd()
  if vim.fn.executable("async-profiler") == 1 then
    return "async-profiler"
  elseif vim.fn.executable("asprof") == 1 then
    return "asprof"
  elseif vim.fn.executable("profiler.sh") == 1 then
    return "profiler.sh"
  end
  return nil
end

function M.start()
  if M.active_pid then
    vim.notify(
      "Profiling already active for PID: " .. M.active_pid,
      vim.log.levels.WARN,
      { title = "TetraVim Profiler" }
    )
    return
  end

  local cmd = get_profiler_cmd()
  if not cmd then
    vim.notify("async-profiler binary not found in $PATH", vim.log.levels.ERROR, { title = "TetraVim Profiler" })
    return
  end

  vim.ui.input({ prompt = "Enter JVM PID to profile: " }, function(pid)
    if not pid or pid == "" then
      return
    end
    pid = pid:match("^%s*(.-)%s*$") -- Trim whitespace
    if not pid:match("^%d+$") then
      vim.notify("Invalid PID", vim.log.levels.ERROR, { title = "TetraVim Profiler" })
      return
    end

    local command = { cmd, "start", pid }
    local ok, snacks = pcall(require, "snacks")
    if ok and snacks and snacks.terminal then
      snacks.terminal(command, { interactive = false })
      M.active_pid = pid
      vim.notify("Started profiling PID: " .. pid, vim.log.levels.INFO, { title = "TetraVim Profiler" })
    else
      -- Fallback to vim.system (async)
      vim.system(command, { text = true }, function(out)
        vim.schedule(function()
          if out.code == 0 then
            M.active_pid = pid
            vim.notify("Started profiling PID: " .. pid, vim.log.levels.INFO, { title = "TetraVim Profiler" })
          else
            vim.notify(
              "Failed to start profiler: " .. (out.stderr or out.stdout or ""),
              vim.log.levels.ERROR,
              { title = "TetraVim Profiler" }
            )
          end
        end)
      end)
    end
  end)
end

function M.stop()
  if not M.active_pid then
    vim.notify("No active profiling session found", vim.log.levels.WARN, { title = "TetraVim Profiler" })
    return
  end

  local cmd = get_profiler_cmd()
  if not cmd then
    vim.notify("async-profiler binary not found in $PATH", vim.log.levels.ERROR, { title = "TetraVim Profiler" })
    return
  end

  local tmp_dir = vim.fn.stdpath("cache") .. "/tetravim-profiler"
  vim.fn.mkdir(tmp_dir, "p")

  if vim.fn.filewritable(tmp_dir) ~= 2 then
    vim.notify("Cannot write to directory: " .. tmp_dir, vim.log.levels.ERROR, { title = "TetraVim Profiler" })
    return
  end

  local out_file = tmp_dir .. "/flamegraph_" .. M.active_pid .. "_" .. os.time() .. ".html"
  local command = { cmd, "stop", "-f", out_file, M.active_pid }

  local ok, snacks = pcall(require, "snacks")
  if ok and snacks and snacks.terminal then
    snacks.terminal(command, {
      interactive = false,
      on_close = function()
        -- Wait briefly to ensure file is written, although on_close means process exited
        if vim.fn.filereadable(out_file) == 1 then
          M.last_flamegraph = out_file
          M.active_pid = nil
          vim.notify(
            "Stopped profiling. Flamegraph generated at:\n" .. out_file,
            vim.log.levels.INFO,
            { title = "TetraVim Profiler" }
          )
        else
          M.active_pid = nil
          vim.notify("Failed to generate flamegraph", vim.log.levels.ERROR, { title = "TetraVim Profiler" })
        end
      end,
    })
  else
    vim.system(command, { text = true }, function(out)
      vim.schedule(function()
        if out.code == 0 and vim.fn.filereadable(out_file) == 1 then
          M.last_flamegraph = out_file
          M.active_pid = nil
          vim.notify(
            "Stopped profiling. Flamegraph generated at:\n" .. out_file,
            vim.log.levels.INFO,
            { title = "TetraVim Profiler" }
          )
        else
          M.active_pid = nil
          vim.notify(
            "Failed to stop profiler or missing flamegraph: " .. (out.stderr or out.stdout or ""),
            vim.log.levels.ERROR,
            { title = "TetraVim Profiler" }
          )
        end
      end)
    end)
  end
end

function M.view()
  if not M.last_flamegraph or vim.fn.filereadable(M.last_flamegraph) == 0 then
    vim.notify("No generated flamegraph available", vim.log.levels.ERROR, { title = "TetraVim Profiler" })
    return
  end

  local opener
  if vim.fn.has("mac") == 1 then
    opener = "open"
  elseif vim.fn.has("unix") == 1 then
    opener = "xdg-open"
  elseif vim.fn.has("win32") == 1 then
    opener = "start"
  else
    vim.notify(
      "Cannot determine browser open command for this OS",
      vim.log.levels.ERROR,
      { title = "TetraVim Profiler" }
    )
    return
  end

  if vim.fn.executable(opener) == 0 then
    vim.notify(
      "Browser open command '" .. opener .. "' not found",
      vim.log.levels.ERROR,
      { title = "TetraVim Profiler" }
    )
    return
  end

  vim.system({ opener, M.last_flamegraph }, { detach = true })
  vim.notify("Opening flamegraph...", vim.log.levels.INFO, { title = "TetraVim Profiler" })
end

return M
