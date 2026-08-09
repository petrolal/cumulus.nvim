-- Cumulus WhichKey Keybinding Helper Integration (Story 8.4, Story 10.1, Story 12.3, Story 20.1, Story 21.2, Story 23.2, Story 24.2 & Story 34.1)

return {
  {
    "folke/which-key.nvim",
    event = "VeryLazy",
    opts = function(_, opts)
      opts.preset = "helix"
      -- The "helix" preset caps the popup at height.max = 0.75 and
      -- width.max = 60. With this many top-level leader groups, that cap
      -- is too small: on an 80x24 terminal, the list overflows and later
      -- groups (e.g. "windows", "buffer") get silently cut off below the
      -- fold instead of scrolling into view, making them look like they
      -- vanished. Raise both caps so the whole group list fits on-screen
      -- while keeping the preset's bottom-right, rounded-border layout.
      opts.win = {
        height = { min = 4, max = 0.9 },
        width = { min = 30, max = 80 },
      }
      opts.spec = opts.spec or {}
      vim.list_extend(opts.spec, {
        { "<leader>f", group = "file/find", icon = "󰈞 " },
        { "<leader>s", group = "search", icon = "󰍉 " },
        { "<leader>t", group = "telescope search", icon = "󰈞 " },
        { "<leader>c", group = "code/build/lsp", icon = "󰅍 " },
        { "<leader>cj", group = "java/jvm build", icon = "󰬷 " },
        { "<leader>cjt", group = "test runner", icon = "󰙨 " },
        { "<leader>cx", group = "java refactor", icon = "󰨞 " },
        { "<leader>w", group = "windows", icon = "󰖲 " },
        { "<leader>l", group = "lsp/mason", icon = "󰒓 " },
        { "<leader>g", group = "git control", icon = "󰊢 " },
        { "<leader>q", group = "quit/session", icon = "󰗼 " },
        { "<leader>d", group = "debug/dap", icon = "󰃤 " },
        { "<leader>D", group = "database", icon = "󰆼 " },
        { "<leader>o", group = "devtools/workloads", icon = "󰒓 " },
        { "<leader>u", group = "ui/toggles", icon = "󰔡 " },
        { "<leader>b", group = "buffer", icon = "󰓩 " },
      })
      -- Per-language <leader>c* subgroups (Maven/Gradle, Terraform,
      -- Ansible, Docker (<leader>cD to avoid the global <leader>cd
      -- Line Diagnostics keymap), Helm...). These are registered with buffer = true,
      -- so which-key only surfaces them while the current buffer's
      -- filetype actually owns matching buffer-local keymaps -- see
      -- lua/cumulus/core/lang-keymaps.lua.
      vim.list_extend(opts.spec, require("cumulus.core.lang-keymaps").whichkey_spec())
      return opts
    end,
  },
}
