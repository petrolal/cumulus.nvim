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
        { "<leader>c", group = "code/lsp", icon = "󰅍 " },
        { "<leader>f", group = "file/find", icon = "󰈞 " },
        { "<leader>s", group = "search", icon = "󰍉 " },
        { "<leader>b", group = "buffer", icon = "󰓩 " },
        { "<leader>w", group = "windows", icon = "󰖲 " },
        { "<leader>l", group = "lazy/mason/lsp", icon = "󰒓 " },
        { "<leader>g", group = "git control", icon = "󰊢 " },
        { "<leader>o", group = "devops/infra", icon = "󱁢 " },
        { "<leader>d", group = "debug/dap", icon = "󰃤 " },
        { "<leader>D", group = "database", icon = "󰆼 " },
        { "<leader>u", group = "ui/toggles", icon = "󰔡 " },
        { "<leader>q", group = "quit/session", icon = "󰗼 " },
      })
      -- Per-language <leader>c* subgroups (Maven/Gradle, Terraform,
      -- Ansible, Docker (<leader>cD to avoid the global <leader>cd
      -- Line Diagnostics keymap), Helm...). These are registered with buffer = true,
      -- so which-key only surfaces them while the current buffer's
      -- filetype actually owns matching buffer-local keymaps -- see
      -- lua/cumulus/core/lang-keymaps.lua.
      vim.list_extend(opts.spec, require("cumulus.core.lang-keymaps").whichkey_spec())

      -- DevOps & Infrastructure Tooling Suite (<leader>o)
      vim.list_extend(opts.spec, {
        { "<leader>o", group = "devops/infra", icon = "󱁢 " },
        { "<leader>ot", group = "terraform/opentofu", icon = "󱁢 " },
        { "<leader>oc", group = "cloudformation/sam", icon = "󰅟 " },
        { "<leader>oy", group = "ansible", icon = "󰚰 " },
        { "<leader>od", group = "docker", icon = "󰡨 " },
        { "<leader>ok", group = "helm/k8s", icon = "󱃾 " },
        -- DevOps Validation Keymaps (Story 3.1: avoid collisions with existing groups)
        { "<leader>cf", group = "cloudformation validation", icon = "󰅟 " },
        { "<leader>ay", group = "ansible validation", icon = "󰚰 " },
        { "<leader>dk", group = "docker validation", icon = "󰡨 " },
        { "<leader>hm", group = "helm validation", icon = "󱃾 " },
      })

      local jvm = require("cumulus.util.jvm")
      vim.list_extend(opts.spec, jvm.whichkey_spec())
      return opts
    end,
  },
}
