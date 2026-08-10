-- Cumulus JUnit 5 Test Runner Integration (SPEC-007)
--
-- Architecture: Lua is a bridge only. All Java/Kotlin source parsing is done
-- by the cumulus-core Rust binary. This file handles Neovim terminal wiring
-- and diagnostic display only.

local M = {}

--- Detect current Java/Kotlin test class and nearest test method using Rust backend.
---@param bufnr? number
---@return { class_name: string|nil, method_name: string|nil }
function M.detect_test_info(bufnr)
  bufnr = (bufnr == nil or bufnr == 0) and vim.api.nvim_get_current_buf() or bufnr
  local file_path = vim.api.nvim_buf_get_name(bufnr)
  local cursor_line = vim.api.nvim_win_get_cursor(0)[1]

  local rust = require("cumulus.util.rust")
  local ctx = rust.detect_test_context(file_path, cursor_line)
  if ctx then
    return ctx
  end

  return { class_name = nil, method_name = nil }
end

--- Parse test log output and display results via Neovim notify.
--- All log parsing is done by the Rust backend (parse-test-output subcommand).
---@param log_content string
function M.process_results(log_content)
  local rust = require("cumulus.util.rust")
  local entries = rust.parse_test_output(log_content)

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
    on_exit = function()
      local log = table.concat(output_lines, "\n")
      M.process_results(log)
    end,
  })

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

return M
