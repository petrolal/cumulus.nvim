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
    cmd = { "Telescope", "TelescopeMavenModules", "TelescopeGradleModules" },
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
        local engine = require("cumulus.util.engine")
        if not engine.is_available() then
          vim.notify("cumulus-engine not available for module resolution", vim.log.levels.ERROR)
          return
        end

        local modules = engine.resolve_modules(vim.fn.getcwd())
        if not modules or type(modules) ~= "table" or #modules == 0 then
          vim.notify("No Maven modules found", vim.log.levels.WARN)
          return
        end

        vim.ui.select(modules, {
          prompt = "Select Maven Module:",
          format_item = function(item)
            return string.format("%s (%s)", item.name, item.path)
          end,
        }, function(choice)
          if choice then
            local build_file = choice.path
            if vim.fn.isdirectory(build_file) == 1 then
              local pom = build_file .. "/pom.xml"
              if vim.fn.filereadable(pom) == 1 then
                build_file = pom
              end
            end
            if vim.fn.filereadable(build_file) == 1 then
              vim.cmd("edit " .. vim.fn.fnameescape(build_file))
            else
              vim.notify("Build file not found for module: " .. choice.name, vim.log.levels.WARN)
            end
          end
        end)
      end, { desc = "List and open Maven sub-modules (SPEC-008)" })

      vim.api.nvim_create_user_command("TelescopeGradleModules", function()
        local engine = require("cumulus.util.engine")
        if not engine.is_available() then
          vim.notify("cumulus-engine not available for module resolution", vim.log.levels.ERROR)
          return
        end

        local modules = engine.resolve_modules(vim.fn.getcwd())
        if not modules or type(modules) ~= "table" or #modules == 0 then
          vim.notify("No Gradle modules found", vim.log.levels.WARN)
          return
        end

        vim.ui.select(modules, {
          prompt = "Select Gradle Module:",
          format_item = function(item)
            return string.format("%s (%s)", item.name, item.path)
          end,
        }, function(choice)
          if choice then
            local build_file = choice.path
            if vim.fn.isdirectory(build_file) == 1 then
              local gradle = build_file .. "/build.gradle"
              local gradle_kts = build_file .. "/build.gradle.kts"
              if vim.fn.filereadable(gradle) == 1 then
                build_file = gradle
              elseif vim.fn.filereadable(gradle_kts) == 1 then
                build_file = gradle_kts
              end
            end
            if vim.fn.filereadable(build_file) == 1 then
              vim.cmd("edit " .. vim.fn.fnameescape(build_file))
            else
              vim.notify("Build file not found for module: " .. choice.name, vim.log.levels.WARN)
            end
          end
        end)
      end, { desc = "List and open Gradle sub-modules (SPEC-008)" })
    end,
  },
}
