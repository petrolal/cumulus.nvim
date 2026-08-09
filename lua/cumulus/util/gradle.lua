-- Cumulus Gradle Utility Helper (Story 4.1 & AR4)

local M = {}

function M.find_gradle()
  local cwd = vim.fn.getcwd()
  local build_gradle = vim.fn.findfile("build.gradle", cwd .. ";")
  local build_gradle_kts = vim.fn.findfile("build.gradle.kts", cwd .. ";")

  if build_gradle == "" and build_gradle_kts == "" then
    local current_file = vim.fn.expand("%:p:h")
    if current_file ~= "" then
      build_gradle = vim.fn.findfile("build.gradle", current_file .. ";")
      build_gradle_kts = vim.fn.findfile("build.gradle.kts", current_file .. ";")
    end
  end

  return build_gradle ~= "" or build_gradle_kts ~= ""
end

function M.get_gradle_cmd()
  local cwd = vim.fn.getcwd()
  local gradlew = cwd .. "/gradlew"
  if vim.fn.filereadable(gradlew) == 1 then
    if vim.fn.executable(gradlew) == 0 then
      vim.fn.system({ "chmod", "+x", gradlew })
    end
    return "./gradlew"
  end
  return "gradle"
end

function M.run_gradle_cmd(cmd)
  if not M.find_gradle() then
    vim.notify("No build.gradle or build.gradle.kts found in project", vim.log.levels.WARN)
    return
  end

  local base_cmd = M.get_gradle_cmd()
  if cmd:sub(1, 10) == "./gradlew " then
    cmd = base_cmd .. cmd:sub(10)
  elseif cmd:sub(1, 7) == "gradle " then
    cmd = base_cmd .. cmd:sub(7)
  end

  vim.cmd("botright 15split")
  local win = vim.api.nvim_get_current_win()
  local buf = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(win, buf)

  vim.fn.termopen(cmd, {
    on_exit = function(_, code)
      local level = (code == 0) and vim.log.levels.INFO or vim.log.levels.ERROR
      vim.notify("Gradle command exited with code " .. code, level)
    end,
  })

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

-- Cold local-repo/offline-mirror resolution can legitimately take a while,
-- but a stuck gradle process (hung proxy auth, dead network) must not hide
-- the gated java/kotlin/maven keymaps (lang-keymaps.lua) forever.
local SYNC_TIMEOUT_MS = 120000

-- How often the heartbeat timer refreshes the "syncing..." toast so a
-- healthy-but-slow sync is visibly distinguishable from a hung one.
local HEARTBEAT_INTERVAL_MS = 5000

-- Stable id so every notification for a given sync attempt (start,
-- heartbeat, success, failure, timeout) replaces the previous one instead of
-- stacking as separate toasts (Snacks' `notify()` honors `opts.id` as
-- replace-in-place -- see lua/cumulus/plugins/editor-snacks.lua:154-155).
local NOTIFY_ID = "cumulus_gradle_sync"

function M.sync_dependencies()
  local sync_state = require("cumulus.util.build-sync-state")

  if not M.find_gradle() then
    return
  end

  local base_cmd = M.get_gradle_cmd()
  vim.notify("Gradle: syncing dependencies...", vim.log.levels.INFO, { id = NOTIFY_ID })
  local timed_out = false
  local timer = (vim.uv or vim.loop).new_timer()
  -- Independent from `timer` (the timeout-kill timer) above -- this one only
  -- ever updates the notification toast and never touches the process.
  local heartbeat = (vim.uv or vim.loop).new_timer()
  local started = (vim.uv or vim.loop).now()

  -- The timeout branch below and the process exit callback can both want to
  -- close `heartbeat` (timeout closes it immediately so it stops updating
  -- the toast after a "timed out" notification; the exit callback closes it
  -- via stop_timers() when the killed process's exit eventually shows up).
  -- Guard against double-closing the same handle, which libuv raises as an
  -- uncaught Lua error ("handle already closing").
  local function close_heartbeat()
    if heartbeat:is_closing() then
      return
    end
    heartbeat:stop()
    heartbeat:close()
  end

  local function stop_timers()
    timer:stop()
    timer:close()
    close_heartbeat()
  end

  -- vim.system() throws synchronously (not via the callback) when the
  -- binary doesn't exist at all (ENOENT) -- e.g. no gradlew wrapper and
  -- gradle isn't on $PATH. pcall it so that shows up as a notification
  -- instead of an uncaught error breaking whatever autocmd triggered this.
  local ok, handle_or_err = pcall(vim.system, { base_cmd, "-q", "dependencies" }, { text = true }, function(result)
    stop_timers()
    if timed_out then
      -- The timeout timer below already called mark_ready() and notified
      -- the user -- don't fire a second, possibly-contradictory
      -- notification if the killed process's exit callback eventually
      -- shows up late (e.g. a forked, non-exec'd grandchild kept the
      -- stdout/stderr pipes open after the direct child was killed).
      return
    end
    vim.schedule(function()
      -- The java/kotlin/maven-related keymaps (lang-keymaps.lua) stay
      -- hidden until sync finishes -- mark ready on both success and
      -- failure so a broken/offline sync doesn't hide them forever.
      sync_state.mark_ready()
      if result.code == 0 then
        vim.notify("Gradle: dependencies synced", vim.log.levels.INFO, { id = NOTIFY_ID })
      else
        local detail = (result.stderr ~= "" and result.stderr)
          or (result.stdout ~= "" and result.stdout)
          or ("exit code " .. result.code)
        vim.notify("Gradle: dependency sync failed\n" .. detail, vim.log.levels.ERROR, { id = NOTIFY_ID })
      end
    end)
  end)
  if ok then
    local handle = handle_or_err
    heartbeat:start(
      HEARTBEAT_INTERVAL_MS,
      HEARTBEAT_INTERVAL_MS,
      vim.schedule_wrap(function()
        local elapsed_seconds = math.floor(((vim.uv or vim.loop).now() - started) / 1000)
        vim.notify(
          "Gradle: syncing dependencies... (" .. elapsed_seconds .. "s)",
          vim.log.levels.INFO,
          { id = NOTIFY_ID }
        )
      end)
    )
    timer:start(SYNC_TIMEOUT_MS, 0, function()
      -- Set the flag and kill the process synchronously, right here in the
      -- raw (unscheduled) timer callback -- both are libuv-level operations
      -- safe outside the main loop. Deferring `timed_out = true` itself via
      -- vim.schedule_wrap would leave a race window where the process's own
      -- (also unscheduled) exit callback could see `timed_out == false` and
      -- report success/failure normally right before this timeout branch
      -- also runs, producing two contradictory notifications.
      timed_out = true
      close_heartbeat()
      pcall(handle.kill, handle, "sigterm")
      vim.schedule(function()
        -- Don't wait for the exit callback to fire before unhiding the
        -- gated keymaps: a killed process is not guaranteed to actually
        -- close its stdout/stderr pipes promptly (e.g. an orphaned
        -- grandchild process can keep them open indefinitely), and that
        -- callback is what the exit-callback branch above depends on. Mark
        -- ready and notify here, unconditionally, so a timeout always
        -- resolves on schedule regardless of whether the kill signal is
        -- ever actually observed to take effect.
        sync_state.mark_ready()
        vim.notify(
          "Gradle: dependency sync timed out after " .. (SYNC_TIMEOUT_MS / 1000) .. "s",
          vim.log.levels.ERROR,
          { id = NOTIFY_ID }
        )
      end)
    end)
  else
    stop_timers()
    sync_state.mark_ready()
    vim.notify(
      "Gradle: dependency sync failed to start (" .. base_cmd .. " not found)\n" .. tostring(handle_or_err),
      vim.log.levels.ERROR,
      { id = NOTIFY_ID }
    )
  end
end

function M.get_gradle_tasks()
  local base_cmd = M.get_gradle_cmd()
  local cmd = base_cmd .. " tasks --all"
  local output = vim.fn.system(cmd)

  if vim.v.shell_error ~= 0 then
    vim.notify("Failed to fetch Gradle tasks", vim.log.levels.ERROR)
    return {}
  end

  local tasks = {}
  local in_tasks_section = false
  for line in output:gmatch("[^\r\n]+") do
    if
      line:match("^%-%-%-%-")
      or line:match("^All tasks")
      or line:match("^Build tasks")
      or line:match("^[A-Z][a-z]+ tasks")
    then
      in_tasks_section = true
    elseif line:match("^$") or line:match("^To see all tasks") then
      in_tasks_section = false
    elseif in_tasks_section then
      local task = line:match("^(%S+)%s*%-")
      if task and not task:match("^%-") and not task:match("^To") and not task:match("^Pattern") then
        table.insert(tasks, task)
      end
    end
  end

  local unique_tasks = {}
  local seen = {}
  for _, task in ipairs(tasks) do
    if not seen[task] then
      table.insert(unique_tasks, task)
      seen[task] = true
    end
  end
  if #unique_tasks > 0 then
    table.insert(unique_tasks, 1, "clean build")
    table.insert(unique_tasks, 1, "clean test")
  end
  return unique_tasks
end

function M.run_gradle_task()
  if not M.find_gradle() then
    vim.notify("No build.gradle or build.gradle.kts found in project", vim.log.levels.WARN)
    return
  end

  vim.notify("Loading Gradle tasks...", vim.log.levels.INFO)
  local tasks = M.get_gradle_tasks()
  if #tasks == 0 then
    vim.notify("No Gradle tasks found", vim.log.levels.WARN)
    return
  end

  local base_cmd = M.get_gradle_cmd()
  vim.ui.select(tasks, {
    prompt = "Select Gradle Task:",
    format_item = function(item)
      return base_cmd .. " " .. item
    end,
  }, function(choice)
    if choice then
      M.run_gradle_cmd(base_cmd .. " " .. choice)
    end
  end)
end

return M
