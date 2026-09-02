local function find_java21_home()
  local patterns = {
    "/usr/lib/jvm/java-21-openjdk*",
    "/usr/lib/jvm/java-21*",
    "/usr/lib/jvm/jdk-21*",
    vim.fn.expand("~/.sdkman/candidates/java/21*"),
    "/usr/lib/jvm/default-java",
  }
  for _, pat in ipairs(patterns) do
    local candidates = vim.fn.glob(pat, false, true)
    if #candidates > 0 and vim.fn.isdirectory(candidates[1]) == 1 then
      return candidates[1]
    end
  end
  return nil
end

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
            local java21_home = find_java21_home()
            if not java21_home then
              return nil
            end
            return { JAVA_HOME = java21_home }
          end)(),
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
            vim.keymap.set("n", "<leader>ce", function()
              require("tetravim.util.extract").extract_interface()
            end, { buffer = bufnr, desc = "Extract Interface (Kotlin)" })
            vim.keymap.set("v", "<leader>ce", function()
              require("tetravim.util.extract").extract_interface(true)
            end, { buffer = bufnr, desc = "Extract Interface (Kotlin)" })

            vim.keymap.set("n", "<leader>ci", function()
              require("tetravim.util.extract").inline()
            end, { buffer = bufnr, desc = "Inline (Kotlin)" })
            vim.keymap.set("v", "<leader>ci", function()
              require("tetravim.util.extract").inline(true)
            end, { buffer = bufnr, desc = "Inline (Kotlin)" })

            vim.keymap.set("n", "<leader>cm", function()
              require("tetravim.util.extract").extract_method()
            end, { buffer = bufnr, desc = "Extract Method (Kotlin)" })
            vim.keymap.set("v", "<leader>cm", function()
              require("tetravim.util.extract").extract_method(true)
            end, { buffer = bufnr, desc = "Extract Method (Kotlin)" })

            vim.keymap.set("n", "<leader>cv", function()
              require("tetravim.util.extract").extract_variable()
            end, { buffer = bufnr, desc = "Extract Variable (Kotlin)" })
            vim.keymap.set("v", "<leader>cv", function()
              require("tetravim.util.extract").extract_variable(true)
            end, { buffer = bufnr, desc = "Extract Variable (Kotlin)" })

            vim.keymap.set("n", "<leader>cc", function()
              require("tetravim.util.extract").extract_constant()
            end, { buffer = bufnr, desc = "Extract Constant (Kotlin)" })
            vim.keymap.set("v", "<leader>cc", function()
              require("tetravim.util.extract").extract_constant(true)
            end, { buffer = bufnr, desc = "Extract Constant (Kotlin)" })
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
