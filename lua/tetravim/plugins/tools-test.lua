-- TetraVim Test Runner Specs (SPEC-1.3: Visual Test Runner & Coverage)
--
-- Integrates neotest with neotest-java for visual test tree discovery,
-- nearest test execution, and DAP debugging across JVM projects.

local function run_nearest()
  local ok, neotest = pcall(require, "neotest")
  if not ok then
    vim.notify("neotest is not available", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local file = vim.api.nvim_buf_get_name(0)
  if not file or file == "" or vim.bo.buftype ~= "" then
    vim.notify("Current buffer is not a valid test file", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local call_ok, err = pcall(function()
    neotest.run.run()
  end)
  if not call_ok then
    vim.notify("Failed to run nearest test: " .. tostring(err), vim.log.levels.WARN, { title = "TetraVim Test" })
  end
end

local function run_file()
  local ok, neotest = pcall(require, "neotest")
  if not ok then
    vim.notify("neotest is not available", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local file = vim.api.nvim_buf_get_name(0)
  if not file or file == "" or vim.bo.buftype ~= "" then
    vim.notify("Current buffer is not a runnable test file", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local call_ok, err = pcall(function()
    neotest.run.run(file)
  end)
  if not call_ok then
    vim.notify("Failed to run test file: " .. tostring(err), vim.log.levels.WARN, { title = "TetraVim Test" })
  end
end

local function toggle_summary()
  local ok, neotest = pcall(require, "neotest")
  if not ok then
    vim.notify("neotest is not available", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  pcall(function()
    neotest.summary.toggle()
  end)
end

local function toggle_output()
  local ok, neotest = pcall(require, "neotest")
  if not ok then
    vim.notify("neotest is not available", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  pcall(function()
    neotest.output_panel.toggle()
  end)
end

local function debug_nearest()
  local ok, neotest = pcall(require, "neotest")
  if not ok then
    vim.notify("neotest is not available", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local dap_ok, _ = pcall(require, "dap")
  if not dap_ok then
    vim.notify("DAP debugger is not configured", vim.log.levels.WARN, { title = "TetraVim Test" })
    return
  end
  local call_ok, err = pcall(function()
    neotest.run.run({ strategy = "dap" })
  end)
  if not call_ok then
    vim.notify("Failed to debug nearest test: " .. tostring(err), vim.log.levels.WARN, { title = "TetraVim Test" })
  end
end

return {
  {
    "nvim-neotest/neotest",
    dependencies = {
      "nvim-lua/plenary.nvim",
      "nvim-neotest/nvim-nio",
      "antoinemadec/FixCursorHold.nvim",
      "nvim-treesitter/nvim-treesitter",
      "rcasia/neotest-java",
    },
    -- Only `neotest-java` is registered as an adapter below, so there is no
    -- runnable coverage for kotlin/scala buffers -- gate the plugin load on
    -- java alone to avoid loading neotest where it can do nothing.
    ft = { "java" },
    keys = {
      {
        "<leader>tr",
        run_nearest,
        desc = "Run Nearest Test",
      },
      {
        "<leader>tf",
        run_file,
        desc = "Run Test File",
      },
      {
        "<leader>ts",
        toggle_summary,
        desc = "Toggle Test Summary",
      },
      {
        "<leader>to",
        toggle_output,
        desc = "Toggle Test Output Panel",
      },
      {
        "<leader>td",
        debug_nearest,
        desc = "Debug Nearest Test (DAP)",
      },
    },
    opts = function()
      local adapters = {}
      local ok_java, neotest_java = pcall(require, "neotest-java")
      if ok_java then
        table.insert(adapters, neotest_java({}))
      end
      return {
        adapters = adapters,
        status = { virtual_text = true },
        output = { open_on_run = true },
      }
    end,
    config = function(_, opts)
      local neotest = require("neotest")
      neotest.setup(opts)
    end,
  },
}
