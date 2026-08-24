-- Cumulus JUnit 5 Test Runner Integration (SPEC-007 & Story 4.4/6.1)
--
-- Architecture: Lua is a bridge only. All Java/Kotlin source parsing, command assembly,
-- and log parsing is done by the cumulus-engine Scala binary.
-- This file handles Neovim terminal wiring and diagnostic display only.

local M = {}

--- Detect current Java/Kotlin test class and nearest test method using Scala engine.
---@param bufnr? number
---@return { class_name: string|nil, method_name: string|nil }
function M.detect_test_info(bufnr)
  bufnr = (bufnr == nil or bufnr == 0) and vim.api.nvim_get_current_buf() or bufnr
  local file_path = vim.api.nvim_buf_get_name(bufnr)
  local cursor_line = vim.api.nvim_win_get_cursor(0)[1]

  local engine = require("cumulus.util.engine")
  local ctx = engine.detect_test_context(file_path, cursor_line)
  if ctx then
    return ctx
  end

  return { class_name = nil, method_name = nil }
end

--- Parse test log output and display results via Neovim notify.
---@param log_content string
function M.process_results(log_content)
  local engine = require("cumulus.util.engine")
  local entries = engine.parse_test_output(log_content)

  if not entries or #entries == 0 then
    return
  end

  local failures, passed = 0, 0
  for _, entry in ipairs(entries) do
    if entry.status == "FAILED" then
      failures = failures + 1
    elseif entry.status == "PASSED" then
      passed = passed + 1
    end
  end

  if failures > 0 then
    vim.notify(
      string.format("Test Suite: %d FAILED, %d PASSED", failures, passed),
      vim.log.levels.ERROR,
      { id = "cumulus_test_run" }
    )
  else
    vim.notify(
      string.format("Test Suite: All %d tests PASSED", passed),
      vim.log.levels.INFO,
      { id = "cumulus_test_run" }
    )
  end
end

--- Run test suite in terminal split.
---@param mode "nearest"|"class"|"all"
function M.run_test(mode)
  local engine = require("cumulus.util.engine")
  local cwd = vim.fn.getcwd()
  local build_result = engine.discover_build_tool(cwd)

  if not build_result then
    vim.notify("No Maven or Gradle project found in current directory", vim.log.levels.WARN)
    return
  end

  local tool = build_result.tool
  if tool ~= "maven" and tool ~= "gradle" then
    vim.notify("Unsupported build tool: " .. tostring(tool), vim.log.levels.WARN)
    return
  end

  local info = M.detect_test_info()
  local cmd = ""

  if engine.is_available() then
    local class_arg = (mode == "nearest" or mode == "class") and info.class_name or nil
    local method_arg = (mode == "nearest") and info.method_name or nil
    local assembled = engine.assemble_test_command({
      tool = tool,
      ["class"] = class_arg,
      method = method_arg,
      dir = cwd,
    })
    if assembled and assembled.command then
      cmd = assembled.command
    end
  end

  if cmd == "" then
    -- Fallback if engine is not available or command assembly failed
    local base_cmd = ""
    local jvm = require("cumulus.util.jvm")
    local offline_flags = jvm.offline_mode

    if tool == "maven" then
      local mvnw = cwd .. "/mvnw"
      base_cmd = "mvn"
      if vim.fn.filereadable(mvnw) == 1 then
        if vim.fn.executable(mvnw) == 0 then
          vim.fn.system({ "chmod", "+x", mvnw })
        end
        base_cmd = "./mvnw"
      end

      -- Apply offline flag if needed
      if offline_flags then
        base_cmd = base_cmd .. " -o"
      end

      if mode == "nearest" and info.class_name and info.method_name then
        local escaped_class = vim.fn.shellescape(info.class_name)
        local escaped_method = vim.fn.shellescape(info.method_name)
        cmd = base_cmd .. " test -Dtest=" .. escaped_class .. "#" .. escaped_method
      elseif (mode == "class" or mode == "nearest") and info.class_name then
        local escaped_class = vim.fn.shellescape(info.class_name)
        cmd = base_cmd .. " test -Dtest=" .. escaped_class
      else
        cmd = base_cmd .. " test"
      end
    else -- gradle
      local gradlew = cwd .. "/gradlew"
      base_cmd = "gradle"
      if vim.fn.filereadable(gradlew) == 1 then
        if vim.fn.executable(gradlew) == 0 then
          vim.fn.system({ "chmod", "+x", gradlew })
        end
        base_cmd = "./gradlew"
      end

      -- Apply offline flag if needed
      if offline_flags then
        base_cmd = base_cmd .. " --offline"
      end

      if mode == "nearest" and info.class_name and info.method_name then
        local escaped_class = vim.fn.shellescape(info.class_name)
        local escaped_method = vim.fn.shellescape(info.method_name)
        cmd = base_cmd .. " test --tests " .. escaped_class .. "." .. escaped_method
      elseif (mode == "class" or mode == "nearest") and info.class_name then
        local escaped_class = vim.fn.shellescape(info.class_name)
        cmd = base_cmd .. " test --tests " .. escaped_class
      else
        cmd = base_cmd .. " test"
      end
    end
  end

  engine.notify_info("Running tests: " .. cmd, "Cumulus Test")

  local output_lines = {}
  engine.run_term(cmd, {
    title = "Cumulus Test",
    on_stdout = function(data)
      for _, line in ipairs(data) do
        if line ~= "" then
          table.insert(output_lines, line)
        end
      end
    end,
    on_stderr = function(data)
      for _, line in ipairs(data) do
        if line ~= "" then
          table.insert(output_lines, line)
        end
      end
    end,
    on_exit = function()
      vim.schedule(function()
        local log = table.concat(output_lines, "\n")
        M.process_results(log)
      end)
    end,
  })
end

return M
