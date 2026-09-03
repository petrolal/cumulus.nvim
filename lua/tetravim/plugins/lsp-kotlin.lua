local jvm = require("tetravim.util.jvm")

local storage_path = vim.fn.stdpath("cache") .. "/kotlin-language-server"
vim.fn.mkdir(storage_path, "p")

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "kotlin" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        kotlin_language_server = {
          init_options = {
            storagePath = storage_path,
          },
          cmd_env = (function()
            local java21_home = jvm.find_java21_home()
            if not java21_home then
              return nil
            end
            return { JAVA_HOME = java21_home }
          end)(),
          root_dir = function(fname_or_buf, on_dir)
            local fname = type(fname_or_buf) == "number" and vim.api.nvim_buf_get_name(fname_or_buf) or fname_or_buf
            local util = require("lspconfig.util")
            local root = util.root_pattern(
              "settings.gradle",
              "settings.gradle.kts",
              "build.gradle",
              "build.gradle.kts",
              "pom.xml",
              ".git"
            )(fname)
            local resolved = root or (fname and fname ~= "" and vim.fs.dirname(fname)) or vim.fn.getcwd()
            if on_dir then
              on_dir(resolved)
            end
            return resolved
          end,
          on_attach = function(client, bufnr)
            client.server_capabilities.documentHighlightProvider = false

            local root = client.config.root_dir or vim.fn.getcwd()
            if root and root ~= "" then
              local kls_files = vim.fn.glob(root .. "/kls_database*", false, true)
              for _, f in ipairs(kls_files) do
                vim.fn.delete(f)
              end
            end

            -- SPEC-2.1: <leader>cr is installed unconditionally in
            -- ftplugin/kotlin.lua (so the no-client case still notifies) --
            -- not re-bound here.

            -- SPEC-2.2: Intelligent Extraction
            -- Wires up: extract_interface, inline, extract_method, extract_variable, extract_constant
            require("tetravim.util.extract").setup_keymaps(bufnr, "Kotlin")
          end,
          settings = {
            kotlin = {
              storagePath = storage_path,
              compiler = {
                jvm = {
                  target = "21",
                },
              },
              hints = {
                typeHints = true,
                parameterHints = true,
                chainedMemberFunctionHints = true,
              },
            },
          },
        },
      },
    },
  },
}
