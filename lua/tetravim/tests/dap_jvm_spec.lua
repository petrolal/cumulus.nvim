-- SPEC-1.1: Advanced JVM Debugger (nvim-dap integration) -- Scala/Metals + shared keymaps
--
-- Scoped to what plenary's busted harness can reliably verify: static spec
-- shape and keymap registration, no third-party plugin requires. Like the
-- repo's other *_spec.lua files, this avoids require("dap")/require("dapui")
-- -- plenary's harness doesn't run lazy.nvim's normal ft/key load events, and
-- forcing a load via require("lazy").load(...) here corrupts lazy's internal
-- state instead. The dap/dapui-dependent behavioral coverage (breakpoint and
-- logpoint state, the whitespace-input guard, exception-breakpoints/eval
-- no-op'ing safely) lives in scripts/validate-dap-jvm.sh, which runs each
-- check in its own fresh `nvim --headless` process where lazy loads normally.

describe("JVM Debugger (SPEC-1.1)", function()
  describe("Mason + lsp-scala registration", function()
    it("should register JVM tools in Mason's ensure_installed", function()
      local mason = require("tetravim.plugins.tools-mason")
      local ensure = nil
      for _, spec in ipairs(mason) do
        if spec.opts and spec.opts.ensure_installed then
          ensure = spec.opts.ensure_installed
        end
      end
      assert.is_not_nil(ensure)
      local found_jdtls, found_dap = false, false
      for _, pkg in ipairs(ensure) do
        if pkg == "jdtls" then
          found_jdtls = true
        elseif pkg == "java-debug-adapter" then
          found_dap = true
        end
      end
      assert.is_true(found_jdtls)
      assert.is_true(found_dap)
    end)

    it("should declare a valid scalameta/nvim-metals lazy spec ft-gated on scala and sbt", function()
      local lsp_scala = require("tetravim.plugins.lsp-scala")
      assert.are.equal("table", type(lsp_scala[1]))
      assert.are.equal("scalameta/nvim-metals", lsp_scala[1][1])
      local ft_set = {}
      for _, f in ipairs(lsp_scala[1].ft) do
        ft_set[f] = true
      end
      assert.is_true(ft_set.scala)
      assert.is_true(ft_set.sbt)
    end)
  end)

  describe("Shared DAP keymaps (tools-dap-devops.lua)", function()
    it("should bind <leader>dC, <leader>dL, <leader>dE, <leader>dv to functions", function()
      local dap_devops = require("tetravim.plugins.tools-dap-devops")
      local key_fns = {}
      for _, k in ipairs(dap_devops[1].keys) do
        key_fns[k[1]] = k[2]
      end
      for _, lhs in ipairs({ "<leader>dC", "<leader>dL", "<leader>dE", "<leader>dv" }) do
        assert.are.equal("function", type(key_fns[lhs]), lhs .. " must be bound")
      end
    end)
  end)

  describe("Java hotswap regression (ftplugin/java.lua)", function()
    it("should still enable hotcodereplace = auto", function()
      local f = io.open("ftplugin/java.lua", "r")
      assert.is_not_nil(f)
      local content = f:read("*a")
      f:close()
      assert.is_truthy(content:match("hotcodereplace%s*=%s*['\"]auto['\"]"))
    end)
  end)
end)
