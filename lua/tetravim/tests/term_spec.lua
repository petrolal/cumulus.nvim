-- Smoke tests for tetravim.util.term

describe("tetravim.util.term", function()
  local term = require("tetravim.util.term")

  it("exposes run_term", function()
    assert.is_function(term.run_term)
  end)

  it("opens a terminal buffer for a short command and runs on_exit", function()
    local start_wins = #vim.api.nvim_list_wins()
    local exit_code

    term.run_term({ "true" }, {
      on_exit = function(code)
        exit_code = code
      end,
    })

    assert.is_true(#vim.api.nvim_list_wins() > start_wins)
    assert.are.equal("terminal", vim.bo.buftype)

    vim.wait(5000, function()
      return exit_code ~= nil
    end, 20)
    assert.are.equal(0, exit_code)

    -- Leave terminal mode and drop the split so the next test starts clean.
    vim.cmd("stopinsert")
    vim.cmd("bwipeout!")
  end)

  it("forwards stdout to on_stdout", function()
    local saw_output = false

    term.run_term({ "sh", "-c", "echo tetravim-term-probe" }, {
      on_stdout = function(data)
        for _, line in ipairs(data) do
          if line:find("tetravim%-term%-probe") then
            saw_output = true
          end
        end
      end,
      on_exit = function() end,
    })

    vim.wait(5000, function()
      return saw_output
    end, 20)
    assert.is_true(saw_output)

    vim.cmd("stopinsert")
    vim.cmd("bwipeout!")
  end)
end)
