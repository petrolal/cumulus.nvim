-- TetraVim Central DAP Keymap Suite (Epic 12 - Story 12.3, Epic 39 - Story 39.1)

return {
  {
    "mfussenegger/nvim-dap",
    keys = {
      {
        "<leader>db",
        function()
          require("dap").toggle_breakpoint()
        end,
        desc = "Toggle Breakpoint",
      },
      {
        "<leader>dc",
        function()
          require("dap").continue()
        end,
        desc = "Continue / Start Debugging",
      },
      {
        "<leader>di",
        function()
          require("dap").step_into()
        end,
        desc = "Step Into",
      },
      {
        "<leader>do",
        function()
          require("dap").step_over()
        end,
        desc = "Step Over",
      },
      {
        "<leader>dO",
        function()
          require("dap").step_out()
        end,
        desc = "Step Out",
      },
      {
        "<leader>dr",
        function()
          require("dap").repl.open()
        end,
        desc = "Open REPL",
      },
      {
        "<leader>du",
        function()
          require("dapui").toggle()
        end,
        desc = "Toggle DAP UI",
      },
      {
        "<leader>dt",
        function()
          require("dap").terminate()
        end,
        desc = "Terminate Debugging",
      },
      {
        "<leader>dC",
        function()
          local condition = vim.fn.input("Condition: ")
          if condition:match("^%s*$") then
            return
          end
          require("dap").set_breakpoint(condition)
        end,
        desc = "Conditional Breakpoint",
      },
      {
        "<leader>dL",
        function()
          local log_message = vim.fn.input("Log message: ")
          if log_message:match("^%s*$") then
            return
          end
          require("dap").set_breakpoint(nil, nil, log_message)
        end,
        desc = "Logpoint",
      },
      {
        "<leader>dE",
        function()
          -- nvim-dap's Session:set_exception_breakpoints already handles
          -- the "adapter doesn't support exceptionBreakpointFilters" case
          -- by notifying and no-op'ing, and prompts for filters from the
          -- adapter's own advertised capabilities when none are passed.
          require("dap").set_exception_breakpoints()
        end,
        desc = "Set Exception Breakpoints",
      },
      {
        "<leader>dv",
        function()
          require("dapui").eval()
        end,
        desc = "Evaluate Variable Under Cursor",
      },
    },
  },
}
