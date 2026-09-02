return {
  {
    "mfussenegger/nvim-dap",
    config = function()
      local dap = require("dap")
      dap.adapters.kotlin = {
        type = "executable",
        command = "kotlin-debug-adapter",
        args = {},
      }

      dap.configurations.kotlin = {
        {
          type = "kotlin",
          request = "launch",
          name = "Launch Kotlin Main Class",
          projectRoot = "${workspaceFolder}",
          mainClass = function()
            return vim.fn.input("Main class (e.g. com.example.MainKt): ", "", "file")
          end,
        },
        {
          type = "kotlin",
          request = "attach",
          name = "Attach to Remote JVM (port 5005)",
          hostName = "localhost",
          port = 5005,
        },
      }
    end,
  },
}
