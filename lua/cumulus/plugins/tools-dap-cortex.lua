return {
  {
    "jedrzejboczar/nvim-dap-cortex-debug",
    ft = { "c", "cpp" },
    dependencies = {
      "mfussenegger/nvim-dap",
    },
    opts = {
      debug_server = "openocd",
    },
    config = function(_, opts)
      require("dap-cortex-debug").setup(opts)

      local dap = require("dap")

      dap.configurations.c = {
        {
          type = "cortex-debug",
          request = "launch",
          name = "ESP32 (OpenOCD)",
          servertype = "openocd",
          interface = "jtag",
          toolchainPrefix = "xtensa-esp32-elf",
        },
        {
          type = "cortex-debug",
          request = "launch",
          name = "ARM Cortex (OpenOCD)",
          servertype = "openocd",
        },
      }
      dap.configurations.cpp = dap.configurations.c
    end,
  },
}
