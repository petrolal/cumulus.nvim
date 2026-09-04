-- lua/tetravim/tests/lsp_resilience_spec.lua
--
-- Epic 5, Story 5.1 -- JDTLS heap limits + bounded LSP auto-restart.

local resilience = require("tetravim.util.lsp_resilience")

describe("apply_memory_limit", function()
  it("adds --jvm-arg heap flags to a JDTLS-style launcher", function()
    local cmd = resilience.apply_memory_limit({ "jdtls", "-data", "/tmp/ws" }, { xmx = "2g", xms = "512m" })
    assert.is_truthy(vim.tbl_contains(cmd, "--jvm-arg=-Xmx2g"))
    assert.is_truthy(vim.tbl_contains(cmd, "--jvm-arg=-Xms512m"))
    -- original args are preserved and untouched
    assert.equals("jdtls", cmd[1])
    assert.is_truthy(vim.tbl_contains(cmd, "-data"))
  end)

  it("adds raw -Xmx/-Xms flags to a bare java -jar invocation", function()
    local cmd = resilience.apply_memory_limit({ "java", "-jar", "server.jar" }, { xmx = "1g", xms = "256m" })
    assert.is_truthy(vim.tbl_contains(cmd, "-Xmx1g"))
    assert.is_truthy(vim.tbl_contains(cmd, "-Xms256m"))
  end)

  it("never duplicates a heap flag that is already present", function()
    local cmd = resilience.apply_memory_limit({ "jdtls", "--jvm-arg=-Xmx4g" }, { xmx = "2g", xms = "512m" })
    local xmx = vim.tbl_filter(function(a)
      return tostring(a):find("-Xmx", 1, true) ~= nil
    end, cmd)
    assert.equals(1, #xmx)
    assert.equals("--jvm-arg=-Xmx4g", xmx[1])
  end)

  it("defaults to the module heap constants when no opts are given", function()
    local cmd = resilience.apply_memory_limit({ "jdtls" })
    assert.is_truthy(vim.tbl_contains(cmd, "--jvm-arg=-Xmx" .. resilience.JDTLS_MAX_HEAP))
    assert.is_truthy(vim.tbl_contains(cmd, "--jvm-arg=-Xms" .. resilience.JDTLS_MIN_HEAP))
  end)

  it("returns non-table input unchanged", function()
    assert.equals("jdtls", resilience.apply_memory_limit("jdtls"))
    assert.is_nil(resilience.apply_memory_limit(nil))
  end)

  it("does not mutate the caller's table", function()
    local original = { "jdtls" }
    resilience.apply_memory_limit(original)
    assert.equals(1, #original)
  end)
end)

describe("note_exit -- bounded restart budget", function()
  before_each(function()
    resilience.reset()
  end)

  it("permits up to MAX_RESTARTS inside the window, then gives up", function()
    local now = 1000
    for i = 1, resilience.MAX_RESTARTS do
      local decision, count = resilience.note_exit("jdtls", now + i)
      assert.equals("restart", decision)
      assert.equals(i, count)
    end
    local decision, count = resilience.note_exit("jdtls", now + resilience.MAX_RESTARTS + 1)
    assert.equals("give-up", decision)
    assert.equals(resilience.MAX_RESTARTS + 1, count)
  end)

  it("resets the counter once the rolling window has elapsed", function()
    local now = 5000
    for i = 1, resilience.MAX_RESTARTS do
      resilience.note_exit("jdtls", now + i)
    end
    -- well past WINDOW_S -> fresh window, budget restored
    local decision, count = resilience.note_exit("jdtls", now + resilience.WINDOW_S + 100)
    assert.equals("restart", decision)
    assert.equals(1, count)
  end)

  it("tracks each server independently", function()
    for _ = 1, resilience.MAX_RESTARTS + 1 do
      resilience.note_exit("jdtls", 1)
    end
    local decision = resilience.note_exit("kotlin_language_server", 1)
    assert.equals("restart", decision)
  end)

  it("reset(name) clears only that server", function()
    for _ = 1, resilience.MAX_RESTARTS + 1 do
      resilience.note_exit("jdtls", 1)
    end
    resilience.note_exit("metals", 1)
    resilience.reset("jdtls")
    assert.equals("restart", (resilience.note_exit("jdtls", 1)))
    -- metals history survived
    assert.equals(2, select(2, resilience.note_exit("metals", 1)))
  end)
end)

describe("make_on_exit", function()
  local scheduled, notes
  local saved_schedule, saved_notify, saved_defer

  before_each(function()
    resilience.reset()
    scheduled = {}
    notes = {}
    saved_schedule = vim.schedule
    saved_notify = vim.notify
    saved_defer = vim.defer_fn
    vim.schedule = function(fn)
      table.insert(scheduled, fn)
      fn()
    end
    -- run the backoff-delayed restart inline so the tests stay synchronous
    vim.defer_fn = function(fn)
      fn()
    end
    vim.notify = function(msg)
      table.insert(notes, msg)
    end
  end)

  after_each(function()
    vim.schedule = saved_schedule
    vim.notify = saved_notify
    vim.defer_fn = saved_defer
  end)

  it("ignores an ordinary shutdown (code 0, no signal)", function()
    local restarts = 0
    local handler = resilience.make_on_exit("jdtls", function()
      restarts = restarts + 1
    end)
    handler(0, 0)
    assert.equals(0, restarts)
    assert.equals(0, #scheduled)
  end)

  it("restarts on a crash while inside the budget", function()
    local restarts = 0
    local handler = resilience.make_on_exit("jdtls", function()
      restarts = restarts + 1
    end)
    handler(139, 11)
    assert.equals(1, restarts)
  end)

  it("stops restarting once the budget is spent", function()
    local restarts = 0
    local handler = resilience.make_on_exit("jdtls", function()
      restarts = restarts + 1
    end)
    for _ = 1, resilience.MAX_RESTARTS + 3 do
      handler(1, 6)
    end
    assert.equals(resilience.MAX_RESTARTS, restarts)
  end)

  it("treats a nil exit code as an unexpected exit and restarts (bounded)", function()
    local restarts = 0
    local handler = resilience.make_on_exit("jdtls", function()
      restarts = restarts + 1
    end)
    handler(nil, nil)
    assert.equals(1, restarts)
  end)

  it("does not error and says 'no restart handler wired' when restart_fn is nil", function()
    local handler = resilience.make_on_exit("jdtls", nil)
    assert.has_no.errors(function()
      handler(139, 11)
    end)
    assert.is_truthy(table.concat(notes, "\n"):match("no restart handler wired"))
  end)
end)
