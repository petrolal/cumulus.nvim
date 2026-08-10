local M = {}

M.offline_mode = false

function M.toggle_offline_mode()
  M.offline_mode = not M.offline_mode
  vim.notify("Maven Offline Mode: " .. (M.offline_mode and "ENABLED (-o)" or "DISABLED"), vim.log.levels.INFO)
end

function M.find_pom()
  local cwd = vim.fn.getcwd()
  local pom = vim.fn.findfile("pom.xml", cwd .. ";")

  if pom == "" then
    local current_file = vim.fn.expand("%:p:h")
    if current_file ~= "" then
      pom = vim.fn.findfile("pom.xml", current_file .. ";")
    end
  end

  return pom ~= ""
end

function M.get_mvn_cmd()
  local cwd = vim.fn.getcwd()
  local mvnw = cwd .. "/mvnw"
  local base = "mvn"
  if vim.fn.filereadable(mvnw) == 1 then
    if vim.fn.executable(mvnw) == 0 then
      vim.fn.system({ "chmod", "+x", mvnw })
    end
    base = "./mvnw"
  end

  local rust = require("cumulus.util.rust")
  if M.offline_mode or (rust.is_available() and rust.check_network() == false) then
    return base .. " -o"
  end

  return base
end

function M.run_maven_cmd(cmd)
  if not M.find_pom() then
    vim.notify("No pom.xml found in project", vim.log.levels.WARN)
    return
  end

  local base_cmd = M.get_mvn_cmd()
  if cmd:sub(1, 4) == "mvn " then
    cmd = base_cmd .. cmd:sub(4)
  end

  vim.cmd("botright 15split")
  local win = vim.api.nvim_get_current_win()
  local buf = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(win, buf)

  vim.fn.termopen(cmd, {
    on_exit = function(_, code)
      local level = (code == 0) and vim.log.levels.INFO or vim.log.levels.ERROR
      vim.notify("Maven command exited with code " .. code, level)
    end,
  })

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

function M.sync_dependencies()
  if not M.find_pom() then
    return
  end

  local base_cmd = M.get_mvn_cmd()
  require("cumulus.util.sync-runner").run({
    cmd = { base_cmd, "-q", "dependency:resolve" },
    notify_id = "cumulus_maven_sync",
    tool_label = "Maven",
    base_cmd = base_cmd,
  })
end

function M.get_maven_goals()
  local cwd = vim.fn.getcwd()
  local pom_path = vim.fn.findfile("pom.xml", cwd .. ";")

  if pom_path == "" then
    local current_file = vim.fn.expand("%:p:h")
    if current_file ~= "" then
      pom_path = vim.fn.findfile("pom.xml", current_file .. ";")
    end
  end

  if pom_path ~= "" then
    local rust = require("cumulus.util.rust")
    if rust.is_available() then
      local rust_goals = rust.parse_pom_goals(pom_path)
      if rust_goals then
        return rust_goals
      end
    end
  end

  local goals = {
    "clean",
    "validate",
    "compile",
    "test",
    "package",
    "verify",
    "install",
    "deploy",
    "clean compile",
    "clean test",
    "clean package",
    "clean install",
    "clean verify",
  }

  if pom_path ~= "" then
    local pom_content = table.concat(vim.fn.readfile(pom_path), "\n")
    local plugin_goals = {}

    if pom_content:match("spring%-boot%-maven%-plugin") then
      table.insert(plugin_goals, "spring-boot:run")
      table.insert(plugin_goals, "spring-boot:build-image")
      table.insert(plugin_goals, "spring-boot:repackage")
    end
    if pom_content:match("quarkus%-maven%-plugin") then
      table.insert(plugin_goals, "quarkus:dev")
      table.insert(plugin_goals, "quarkus:build")
      table.insert(plugin_goals, "quarkus:test")
    end
    if pom_content:match("maven%-surefire%-plugin") or pom_content:match("<test") then
      table.insert(plugin_goals, "test-compile")
      table.insert(plugin_goals, "surefire:test")
    end
    if pom_content:match("maven%-failsafe%-plugin") then
      table.insert(plugin_goals, "failsafe:integration-test")
      table.insert(plugin_goals, "verify")
    end
    if pom_content:match("exec%-maven%-plugin") then
      table.insert(plugin_goals, "exec:java")
      table.insert(plugin_goals, "exec:exec")
    end

    table.insert(plugin_goals, "dependency:tree")
    table.insert(plugin_goals, "dependency:analyze")
    table.insert(plugin_goals, "dependency:resolve")
    table.insert(plugin_goals, "help:effective-pom")
    table.insert(plugin_goals, "help:active-profiles")

    for _, goal in ipairs(plugin_goals) do
      table.insert(goals, goal)
    end
  end

  return goals
end

function M.run_maven_goal()
  if not M.find_pom() then
    vim.notify("No pom.xml found in project", vim.log.levels.WARN)
    return
  end

  vim.notify("Loading Maven goals...", vim.log.levels.INFO)
  local goals = M.get_maven_goals()
  local base_cmd = M.get_mvn_cmd()

  vim.ui.select(goals, {
    prompt = "Select Maven Goal:",
    format_item = function(item)
      return base_cmd .. " " .. item
    end,
  }, function(choice)
    if choice then
      M.run_maven_cmd(base_cmd .. " " .. choice)
    end
  end)
end

return M
