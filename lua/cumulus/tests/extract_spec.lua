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

    it("should reject a second action while one is already in flight (shared action-lock.lua)", function()
      local extract = require("cumulus.util.extract")
      local action_lock = require("cumulus.util.action-lock")
      local notified = {}
      local orig_notify = vim.notify
      vim.notify = function(msg, level)
        table.insert(notified, { msg = msg, level = level })
      end

      action_lock.acquire()
      extract.extract_interface()
      action_lock.release()

      vim.notify = orig_notify
      assert.are.equal(1, #notified)
      assert.are.equal(vim.log.levels.WARN, notified[1].level)
      assert.is_truthy(notified[1].msg:match("already in progress"))
    end)
  end)

  describe("Buffer-local <leader>ce and <leader>ci override wiring", function()
    it(
      "ftplugin/java.lua should install REAL buffer-local extraction mappings (ce/ci/cm/cv/cc) that dispatch into cumulus.util.extract",
      function()
        -- Dynamic, mirroring refactor_spec.lua's <leader>cr wiring test:
        -- load the REAL ftplugin/java.lua, capture the on_attach it hands
        -- to jdtls.start_or_attach (mocked so no real jdtls process is
        -- needed), invoke it against a scratch buffer, and assert the
        -- resulting keymaps are REAL, callable mappings -- not just source
        -- text matching a string pattern, which would pass even if the
        -- keymap were dead code never actually reached at runtime.
        local old_require = _G.require
        local captured_on_attach = nil
        _G.require = function(mod)
          if mod == "jdtls" then
            return {
              -- ftplugin/java.lua calls jdtls.setup.find_root(...) at
              -- module-load time (as a root_dir fallback) BEFORE
              -- start_or_attach is ever invoked -- the mock needs this
              -- too, or loading the file errors before on_attach is even
              -- captured.
              setup = {
                find_root = function()
                  return vim.fn.getcwd()
                end,
              },
              -- on_attach calls jdtls.setup_dap(...) unconditionally (not
              -- pcall-guarded) -- stub it as a no-op so invoking the
              -- captured on_attach below doesn't crash before reaching the
              -- extraction keymaps this test actually cares about.
              setup_dap = function() end,
              start_or_attach = function(config)
                captured_on_attach = config.on_attach
              end,
            }
          end
          return old_require(mod)
        end

        vim.cmd("enew")
        local bufnr = vim.api.nvim_get_current_buf()
        vim.bo[bufnr].filetype = "java"

        local f = loadfile("ftplugin/java.lua")
        if f then
          f()
        end
        _G.require = old_require

        if not captured_on_attach then
          pending("Could not capture on_attach")
          return
        end

        captured_on_attach({ name = "jdtls" }, bufnr)

        local extract = require("cumulus.util.extract")
        local calls = {}
        local function stub(name)
          return function(...)
            calls[name] = { ... }
          end
        end
        local orig = {
          extract_interface = extract.extract_interface,
          inline = extract.inline,
          extract_method = extract.extract_method,
          extract_variable = extract.extract_variable,
          extract_constant = extract.extract_constant,
        }
        extract.extract_interface = stub("extract_interface")
        extract.inline = stub("inline")
        extract.extract_method = stub("extract_method")
        extract.extract_variable = stub("extract_variable")
        extract.extract_constant = stub("extract_constant")

        local function assert_mapping_calls(lhs, mode, key, expect_visual_arg)
          local mapping = vim.fn.maparg(lhs, mode, false, true)
          assert.is_not_nil(mapping.buffer, "Mapping " .. lhs .. " not found in mode " .. mode)
          -- maparg(...).buffer is a 0/1 "is buffer-local" flag, not a bufnr.
          assert.are.equal(1, mapping.buffer)
          assert.is_function(mapping.callback)
          calls[key] = nil
          mapping.callback()
          assert.is_not_nil(calls[key], lhs .. " (" .. mode .. ") did not call cumulus.util.extract." .. key)
          if expect_visual_arg then
            assert.is_true(calls[key][1], lhs .. " (" .. mode .. ") must pass is_visual=true")
          end
        end

        assert_mapping_calls("<leader>ce", "n", "extract_interface")
        assert_mapping_calls("<leader>ce", "v", "extract_interface", true)
        assert_mapping_calls("<leader>ci", "n", "inline")
        assert_mapping_calls("<leader>ci", "v", "inline", true)
        assert_mapping_calls("<leader>cm", "n", "extract_method")
        assert_mapping_calls("<leader>cm", "v", "extract_method", true)
        assert_mapping_calls("<leader>cv", "n", "extract_variable")
        assert_mapping_calls("<leader>cv", "v", "extract_variable", true)
        assert_mapping_calls("<leader>cc", "n", "extract_constant")
        assert_mapping_calls("<leader>cc", "v", "extract_constant", true)

        extract.extract_interface = orig.extract_interface
        extract.inline = orig.inline
        extract.extract_method = orig.extract_method
        extract.extract_variable = orig.extract_variable
        extract.extract_constant = orig.extract_constant
      end
    )

    it(
      "lsp-kotlin.lua's on_attach should install REAL buffer-local mappings that dispatch into cumulus.util.extract",
      function()
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

        -- Same dispatch/visual-arg coverage the Java test runs -- a mis-wired
        -- Kotlin mapping (wrong action, or normal-mode behavior on a visual
        -- selection) must not slip through as "some buffer-local mapping exists".
        local extract = require("cumulus.util.extract")
        local calls = {}
        local orig = {}
        for _, name in ipairs({
          "extract_interface",
          "inline",
          "extract_method",
          "extract_variable",
          "extract_constant",
        }) do
          orig[name] = extract[name]
          extract[name] = function(...)
            calls[name] = { ... }
          end
        end

        local function assert_mapping_calls(lhs, mode, key, expect_visual_arg)
          local mapping = vim.fn.maparg(lhs, mode, false, true)
          assert.is_not_nil(mapping.buffer, "Mapping " .. lhs .. " not found in mode " .. mode)
          assert.are.equal(1, mapping.buffer)
          assert.is_function(mapping.callback)
          calls[key] = nil
          mapping.callback()
          assert.is_not_nil(calls[key], lhs .. " (" .. mode .. ") did not call cumulus.util.extract." .. key)
          if expect_visual_arg then
            assert.is_true(calls[key][1], lhs .. " (" .. mode .. ") must pass is_visual=true")
          end
        end

        local ok, err = pcall(function()
          assert_mapping_calls("<leader>ce", "n", "extract_interface")
          assert_mapping_calls("<leader>ce", "v", "extract_interface", true)
          assert_mapping_calls("<leader>ci", "n", "inline")
          assert_mapping_calls("<leader>ci", "v", "inline", true)
          assert_mapping_calls("<leader>cm", "n", "extract_method")
          assert_mapping_calls("<leader>cm", "v", "extract_method", true)
          assert_mapping_calls("<leader>cv", "n", "extract_variable")
          assert_mapping_calls("<leader>cv", "v", "extract_variable", true)
          assert_mapping_calls("<leader>cc", "n", "extract_constant")
          assert_mapping_calls("<leader>cc", "v", "extract_constant", true)
        end)

        for name, fn in pairs(orig) do
          extract[name] = fn
        end
        require("cumulus.util.action-lock").release()
        if not ok then
          error(err, 0)
        end
      end
    )
  end)
end)
