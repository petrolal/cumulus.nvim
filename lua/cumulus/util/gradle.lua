local M = {}

M.offline_mode = false

function M.toggle_offline_mode()
  M.offline_mode = not M.offline_mode
  vim.notify("Gradle Offline Mode: " .. (M.offline_mode and "ENABLED (--offline)" or "DISABLED"), vim.log.levels.INFO)
end

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
  local base = "gradle"
  if vim.fn.filereadable(gradlew) == 1 then
    if vim.fn.executable(gradlew) == 0 then
      vim.fn.system({ "chmod", "+x", gradlew })
    end
    base = "./gradlew"
  end

  local rust = require("cumulus.util.rust")
  if M.offline_mode or (rust.is_available() and rust.check_network() == false) then
    return base .. " --offline"
  end

  return base
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

function M.sync_dependencies()
  if not M.find_gradle() then
    return
  end

  local base_cmd = M.get_gradle_cmd()
  require("cumulus.util.sync-runner").run({
    cmd = { base_cmd, "-q", "dependencies" },
    notify_id = "cumulus_gradle_sync",
    tool_label = "Gradle",
    base_cmd = base_cmd,
  })
end

function M.get_gradle_tasks()
  local base_cmd = M.get_gradle_cmd()
  local cmd = base_cmd .. " tasks --all"
  local output = vim.fn.system(cmd)

  if vim.v.shell_error ~= 0 then
    vim.notify("Failed to fetch Gradle tasks", vim.log.levels.ERROR)
    return {}
  end

  local rust = require("cumulus.util.rust")
  if rust.is_available() then
    local rust_tasks = rust.parse_gradle_tasks(output)
    if rust_tasks then
      return rust_tasks
    end
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
