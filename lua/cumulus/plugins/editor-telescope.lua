-- Cumulus Telescope & Ripgrep Integration (Story 16.1, 16.2 & Story 22.1)

return {
  {
    "nvim-telescope/telescope.nvim",
    dependencies = {
      "nvim-lua/plenary.nvim",
      {
        "nvim-telescope/telescope-fzf-native.nvim",
        build = "make",
        config = function()
          pcall(function()
            require("telescope").load_extension("fzf")
          end)
        end,
      },
      "nvim-telescope/telescope-ui-select.nvim",
    },
    keys = {
      {
        "<leader>/",
        function()
          require("telescope.builtin").live_grep()
        end,
        desc = "Live Grep (Telescope)",
      },
      {
        "<leader><space>",
        function()
          require("telescope.builtin").find_files({ hidden = true })
        end,
        desc = "Find Files (Telescope)",
      },
      {
        "<leader>ff",
        function()
          require("telescope.builtin").find_files({ hidden = true })
        end,
        desc = "Find Files (Telescope)",
      },
      {
        "<leader>tf",
        function()
          require("telescope.builtin").find_files({ hidden = true })
        end,
        desc = "Find Files (Telescope)",
      },
      {
        "<leader>sg",
        function()
          require("telescope.builtin").live_grep()
        end,
        desc = "Live Grep (Telescope)",
      },
      {
        "<leader>tg",
        function()
          require("telescope.builtin").live_grep()
        end,
        desc = "Live Grep (Telescope)",
      },
      {
        "<leader>tp",
        function()
          require("telescope.builtin").find_files({
            cwd = require("lazy.core.config").options.root,
          })
        end,
        desc = "Find Plugin File (Telescope)",
      },
      {
        "<leader>sw",
        function()
          require("telescope.builtin").grep_string()
        end,
        desc = "Word Under Cursor (Telescope)",
      },
      {
        "<leader>sb",
        function()
          require("telescope.builtin").current_buffer_fuzzy_find()
        end,
        desc = "Fuzzy Search Current Buffer",
      },
      {
        "<leader>sh",
        function()
          require("telescope.builtin").help_tags()
        end,
        desc = "Help Tags",
      },
      {
        "<leader>sk",
        function()
          require("telescope.builtin").keymaps()
        end,
        desc = "Telescope Keymaps",
      },
    },
    opts = function(_, opts)
      opts.defaults = opts.defaults or {}
      opts.defaults.layout_strategy = "horizontal"
      opts.defaults.layout_config = vim.tbl_deep_extend("force", opts.defaults.layout_config or {}, {
        prompt_position = "top",
      })
      opts.defaults.sorting_strategy = "ascending"
      opts.defaults.winblend = 0
      opts.defaults.vimgrep_arguments = {
        "rg",
        "--color=never",
        "--no-heading",
        "--with-filename",
        "--line-number",
        "--column",
        "--smart-case",
        "--hidden",
        "--glob",
        "!**/.git/*",
      }
      opts.extensions = vim.tbl_deep_extend("force", opts.extensions or {}, {
        ["ui-select"] = {
          require("telescope.themes").get_dropdown({}),
        },
      })
      return opts
    end,
    config = function(_, opts)
      local telescope = require("telescope")
      telescope.setup(opts)
      pcall(telescope.load_extension, "fzf")
      pcall(telescope.load_extension, "ui-select")

      vim.api.nvim_create_user_command("TelescopeMavenModules", function()
        require("cumulus.util.multimodule").select_module("maven")
      end, { desc = "List and open Maven sub-modules (SPEC-008)" })

      vim.api.nvim_create_user_command("TelescopeGradleModules", function()
        require("cumulus.util.multimodule").select_module("gradle")
      end, { desc = "List and open Gradle sub-modules (SPEC-008)" })
    end,
  },
}
