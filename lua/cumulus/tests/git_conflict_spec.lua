-- SPEC-4.1: Advanced Git Conflict Resolution -- static shape spec.
--
-- Per AGENTS.md: static registration/shape checks live here; real
-- diffview.nvim runtime behavior is covered by scripts/validate-4-1.sh
-- (its own fresh `nvim --headless` process). This spec MUST NOT
-- require("diffview") -- plenary's harness never fires the lazy load events.

local assert = require("luassert")

-- Resolve the repo root from this file's own path (not CWD) so io.open()
-- source-text checks work regardless of where the suite is launched from.
local THIS = debug.getinfo(1, "S").source:sub(2)
local REPO_ROOT = vim.fn.fnamemodify(THIS, ":p:h:h:h:h")

local function read_file(rel)
  local fh = assert(io.open(REPO_ROOT .. "/" .. rel, "r"), "could not open " .. rel .. " from repo root " .. REPO_ROOT)
  local src = fh:read("*a")
  fh:close()
  return src
end

describe("SPEC-4.1 Advanced Git Conflict Resolution", function()
  it("tools-diffview.lua declares diffview.nvim lazy on cmd + keys", function()
    local snapshot = package.loaded["diffview"]
    package.loaded["diffview"] = nil

    local spec = require("cumulus.plugins.tools-diffview")
    assert.is_table(spec)
    assert.is_table(spec[1])
    assert.equals("sindrets/diffview.nvim", spec[1][1])

    assert.is_table(spec[1].cmd)
    assert.is_true(vim.tbl_contains(spec[1].cmd, "DiffviewOpen"))
    assert.is_true(vim.tbl_contains(spec[1].cmd, "DiffviewFileHistory"))

    -- opts stays a function so requiring the spec never pulls in diffview.
    assert.is_function(spec[1].opts)
    assert.is_table(spec[1].dependencies)
    assert.is_true(
      vim.tbl_contains(spec[1].dependencies, "nvim-lua/plenary.nvim"),
      "diffview spec must depend on nvim-lua/plenary.nvim"
    )

    assert.is_table(spec[1].keys)
    local by_lhs = {}
    for _, k in ipairs(spec[1].keys) do
      by_lhs[k[1]] = k
    end
    for _, want in ipairs({ "<leader>gco", "<leader>gcq", "<leader>gch", "<leader>gcH", "<leader>gcf" }) do
      assert.is_table(by_lhs[want], "missing golden keymap " .. want)
    end
    assert.equals("x", by_lhs["<leader>gcH"].mode, "<leader>gcH must be a visual-mode (x) keymap")

    -- Every new global keymap sits under <leader>g (frozen boundary).
    for _, k in ipairs(spec[1].keys) do
      assert.truthy(tostring(k[1]):match("^<leader>g"), tostring(k[1]) .. " must live under <leader>g")
    end

    -- Requiring the spec must not eagerly pull in diffview itself.
    assert.is_nil(package.loaded["diffview"])

    package.loaded["diffview"] = snapshot
  end)

  it("configures a 3-way merge_tool layout without calling opts()", function()
    -- opts() require()s diffview.actions, so assert on source text instead.
    local src = read_file("lua/cumulus/plugins/tools-diffview.lua")
    assert.truthy(src:find("merge_tool", 1, true), "tools-diffview.lua must configure view.merge_tool")
    assert.truthy(src:find("layout", 1, true), "tools-diffview.lua must set merge_tool.layout")
    assert.truthy(src:find("diff4_mixed", 1, true), "merge_tool.layout must be diff4_mixed (distinct BASE pane)")
  end)

  it("cumulus.util.git exposes in_worktree and guard", function()
    local git = require("cumulus.util.git")
    assert.is_table(git)
    assert.is_function(git.in_worktree)
    assert.is_function(git.guard)
  end)

  it("which-key registers the <leader>gc and <leader>gx groups", function()
    local src = read_file("lua/cumulus/plugins/ui-whichkey.lua")
    assert.truthy(src:find('"<leader>gc"', 1, true), "ui-whichkey.lua must register the <leader>gc group")
    assert.truthy(src:find("conflict/compare", 1, true), "the <leader>gc group must be named conflict/compare")
    assert.truthy(src:find('"<leader>gx"', 1, true), "ui-whichkey.lua must register the <leader>gx group")
  end)

  it("health.lua registers the Advanced Git Conflict Resolution section", function()
    local src = read_file("lua/cumulus/health.lua")
    assert.truthy(
      src:find("Advanced Git Conflict Resolution", 1, true),
      "health.lua must start an 'Advanced Git Conflict Resolution' section"
    )
    assert.truthy(src:find("diffview", 1, true), "the health section must cover diffview.nvim")
    assert.truthy(src:find('executable("git")', 1, true), "the health section must check for the git binary")
  end)
end)
