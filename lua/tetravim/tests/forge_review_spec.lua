local assert = require("luassert")

describe("Code Reviews Plugin Configuration", function()
  it("registers <leader>gr keymaps in tools-review spec", function()
    local spec = require("tetravim.plugins.tools-review")
    assert.is_table(spec)
    assert.is_table(spec[1])
    assert.are.equal("sindrets/diffview.nvim", spec[1][1])

    local keys = spec[1].keys
    assert.is_table(keys)

    local found_p = false
    local found_c = false
    local found_C = false

    for _, keymap in ipairs(keys) do
      if keymap[1] == "<leader>grp" then
        found_p = true
      end
      if keymap[1] == "<leader>grc" then
        found_c = true
      end
      if keymap[1] == "<leader>grC" then
        found_C = true
      end
    end

    assert.is_true(found_p, "<leader>grp not found")
    assert.is_true(found_c, "<leader>grc not found")
    assert.is_true(found_C, "<leader>grC not found")
  end)

  it("registers <leader>gr group in ui-whichkey spec", function()
    local wk_spec = require("tetravim.plugins.ui-whichkey")
    assert.is_table(wk_spec)

    local opts_func = wk_spec[1].opts
    assert.is_function(opts_func)

    package.loaded["tetravim.core.lang-keymaps"] = {
      whichkey_spec = function()
        return {}
      end,
    }
    package.loaded["tetravim.util.jvm"] = {
      whichkey_spec = function()
        return {}
      end,
    }

    local mock_opts = { spec = {} }
    local result = opts_func(nil, mock_opts)

    local found = false
    for _, item in ipairs(result.spec) do
      if item[1] == "<leader>gr" and item.group == "git review" then
        found = true
        break
      end
    end
    assert.is_true(found, "<leader>gr group not found in which-key spec")
  end)
end)
