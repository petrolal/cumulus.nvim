return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "java" })
      end
    end,
  },

  {
    "mfussenegger/nvim-jdtls",
    ft = { "java" },
    config = function(_, opts)
      -- JDTLS lifecycle managed via ftplugin/java.lua
    end,
    opts = function(_, opts)
      opts = opts or {}
      local lombok_path = vim.fn.expand("~/.local/share/nvim/mason/share/jdtls/lombok.jar")
      if vim.fn.filereadable(lombok_path) == 0 then
        lombok_path = vim.fn.expand("~/.local/share/nvim/mason/packages/jdtls/lombok.jar")
      end

      if vim.fn.filereadable(lombok_path) == 1 then
        local orig_full_cmd = opts.full_cmd
        opts.full_cmd = function(options)
          local cmd = orig_full_cmd and orig_full_cmd(options) or { "jdtls" }
          local has_lombok = false
          for _, arg in ipairs(cmd) do
            if type(arg) == "string" and arg:find("lombok.jar") then
              has_lombok = true
              break
            end
          end
          if not has_lombok then
            table.insert(cmd, "--jvm-arg=-javaagent:" .. lombok_path)
          end
          return cmd
        end
      end

      local orig_root_dir = opts.root_dir
      opts.root_dir = function(fname)
        local root = orig_root_dir and orig_root_dir(fname)
        return root or vim.fs.dirname(fname) or vim.fn.getcwd()
      end

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

      local java21_path = find_java21_home()
      local runtimes = {}
      if java21_path then
        table.insert(runtimes, {
          name = "JavaSE-21",
          path = java21_path,
          default = true,
        })
      end

      opts.settings = vim.tbl_deep_extend("force", opts.settings or {}, {
        java = {
          configuration = {
            runtimes = runtimes,
          },
          signatureHelp = { enabled = true },
          contentProvider = { preferred = "fernflower" },
          completion = {
            favoriteStaticMembers = {
              "org.hamcrest.MatcherAssert.assertThat",
              "org.hamcrest.Matchers.*",
              "org.hamcrest.CoreMatchers.*",
              "org.junit.jupiter.api.Assertions.*",
              "java.util.Objects.requireNonNull",
              "java.util.Objects.requireNonNullElse",
              "org.mockito.Mockito.*",
            },
            filteredTypes = {
              "com.sun.*",
              "io.micrometer.shaded.*",
              "java.awt.*",
              "jdk.*",
              "sun.*",
            },
          },
          sources = {
            organizeImports = {
              starThreshold = 9999,
              staticStarThreshold = 9999,
            },
          },
          codeGeneration = {
            toString = {
              template = "${object.className}{${member.name()}=${member.value}, ${otherMembers}}",
            },
            useBlocks = true,
          },
        },
      })
      return opts
    end,
  },
}
