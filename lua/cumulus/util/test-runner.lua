-- Cumulus JUnit 5 Test Runner Integration (SPEC-007)
--
-- Detects nearest test method or test class, constructs Maven/Gradle test execution
-- commands, and parses test results for inline diagnostic reporting.

local M = {}

local test_ns = vim.api.nvim_create_namespace("cumulus_test")

--- Detects current Java/Kotlin test class and nearest test method based on cursor position
---@param bufnr? number
---@return { class_name: string|nil, method_name: string|nil }
function M.detect_test_info(bufnr)
  bufnr = (bufnr == nil or bufnr == 0) and vim.api.nvim_get_current_buf() or bufnr
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local cursor_line = vim.api.nvim_win_get_cursor(0)[1]

  local class_name = nil
  for _, line in ipairs(lines) do
    local c = line:match("public%s+class%s+([A-Za-z0-9_]+)")
      or line:match("class%s+([A-Za-z0-9_]+)")
    if c then
      class_name = c
      break
    end
  end

  local method_name = nil
  -- Scan upwards from cursor line to find nearest @Test or test method
  for l = cursor_line, 1, -1 do
    local line = lines[l] or ""
    local m = line:match("void%s+([A-Za-z0-9_]+)%s*%(")
      or line:match("fun%s+([A-Za-z0-9_]+)%s*%(")
    if m then
      method_name = m
      break
    end
  end

  return { class_name = class_name, method_name = method_name }
end

--- Parse test log output and populate diagnostics for failed tests
---@param log_content string
function M.process_results(log_content)
  local rust = require("cumulus.util.rust")
  local entries = rust.is_available() and rust.parse_test_output(log_content) or nil

  if not entries or #entries == 0 then
    return
  end

  local failures = 0
  local passed = 0
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

--- Run test suite in terminal split
---@param mode "nearest"|"class"|"all"
function M.run_test(mode)
  local maven = require("cumulus.util.maven")
  local gradle = require("cumulus.util.gradle")

  local is_maven = maven.find_pom()
  local is_gradle = gradle.find_gradle()

  if not is_maven and not is_gradle then
    vim.notify("No pom.xml or build.gradle found in project", vim.log.levels.WARN)
    return
  end

  local info = M.detect_test_info()
  local cmd = ""

  if is_maven then
    local base = maven.get_mvn_cmd()
    if mode == "nearest" and info.class_name and info.method_name then
      cmd = base .. " test -Dtest=" .. info.class_name .. "#" .. info.method_name
    elseif (mode == "class" or mode == "nearest") and info.class_name then
      cmd = base .. " test -Dtest=" .. info.class_name
    else
      cmd = base .. " test"
    end
  else
    local base = gradle.get_gradle_cmd()
    if mode == "nearest" and info.class_name and info.method_name then
      cmd = base .. " test --tests " .. info.class_name .. "." .. info.method_name
    elseif (mode == "class" or mode == "nearest") and info.class_name then
      cmd = base .. " test --tests " .. info.class_name
    else
      cmd = base .. " test"
    end
  end

  vim.notify("Running tests: " .. cmd, vim.log.levels.INFO)

  vim.cmd("botright 15split")
  local win = vim.api.nvim_get_current_win()
  local buf = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(win, buf)

  local output_lines = {}
  vim.fn.termopen(cmd, {
    on_stdout = function(_, data)
      for _, line in ipairs(data) do
        if line ~= "" then
          table.insert(output_lines, line)
        end
      end
    end,
    on_exit = function(_, code)
      local log = table.concat(output_lines, "\n")
      M.process_results(log)
    end,
  })

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

return M
