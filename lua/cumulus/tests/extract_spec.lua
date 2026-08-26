-- SPEC-2.2: Intelligent Extraction -- static shape tests

describe("Extract (SPEC-2.2)", function()
  describe("cumulus.util.extract", function()
    it("should expose extract_interface and inline", function()
      local extract = require("cumulus.util.extract")
      assert.is_table(extract)
      assert.is_function(extract.extract_interface)
      assert.is_function(extract.inline)
      assert.is_function(extract.extract_method)
      assert.is_function(extract.extract_variable)
      assert.is_function(extract.extract_constant)
      assert.is_number(extract.ACTION_TIMEOUT_MS)
    end)

    it("should reject a second action while one is already in flight (M._busy)", function()
      local extract = require("cumulus.util.extract")
      local notified = {}
      local orig_notify = vim.notify
      vim.notify = function(msg, level)
        table.insert(notified, { msg = msg, level = level })
      end

      extract._busy = true
      extract.extract_interface()
      extract._busy = false

      vim.notify = orig_notify
      assert.are.equal(1, #notified)
      assert.are.equal(vim.log.levels.WARN, notified[1].level)
      assert.is_truthy(notified[1].msg:match("already in progress"))
    end)
  end)

  describe("Buffer-local <leader>ce and <leader>ci override wiring", function()
    it("ftplugin/java.lua should bind extraction mappings to refactor.extract inside on_attach", function()
      local f = io.open("ftplugin/java.lua", "r")
      assert.is_not_nil(f)
      local content = f:read("*a")
      f:close()
      assert.is_truthy(content:match('"<leader>ce"'))
      assert.is_truthy(content:match('require%("cumulus%.util%.extract"%)%.extract_interface'))
      assert.is_truthy(content:match('"<leader>ci"'))
      assert.is_truthy(content:match('require%("cumulus%.util%.extract"%)%.inline'))
      assert.is_truthy(content:match('"<leader>cm"'))
      assert.is_truthy(content:match('require%("cumulus%.util%.extract"%)%.extract_method'))
      assert.is_truthy(content:match('"<leader>cv"'))
      assert.is_truthy(content:match('require%("cumulus%.util%.extract"%)%.extract_variable'))
      assert.is_truthy(content:match('"<leader>cc"'))
      assert.is_truthy(content:match('require%("cumulus%.util%.extract"%)%.extract_constant'))
    end)

    it("lsp-kotlin.lua's on_attach should install REAL buffer-local mappings", function()
      local lsp_kotlin = require("cumulus.plugins.lsp-kotlin")
      local on_attach = lsp_kotlin[2].opts.servers.kotlin_language_server.on_attach
      assert.is_function(on_attach)

      vim.cmd("enew")
      local bufnr = vim.api.nvim_get_current_buf()
      local stub_client = {
        server_capabilities = {},
        config = { root_dir = vim.fn.getcwd() },
      }
      on_attach(stub_client, bufnr)

      local function assert_mapping(lhs, mode)
        local mapping = vim.fn.maparg(lhs, mode, false, true)
        assert.is_table(mapping, "Mapping " .. lhs .. " not found in mode " .. mode)
        assert.are.equal(1, mapping.buffer)
        assert.is_function(mapping.callback)
      end

      assert_mapping("<leader>ce", "n")
      assert_mapping("<leader>ci", "n")
      assert_mapping("<leader>ci", "v")
      assert_mapping("<leader>cm", "n")
      assert_mapping("<leader>cm", "v")
      assert_mapping("<leader>cv", "n")
      assert_mapping("<leader>cv", "v")
      assert_mapping("<leader>cc", "n")
      assert_mapping("<leader>cc", "v")
    end)
  end)
end)
