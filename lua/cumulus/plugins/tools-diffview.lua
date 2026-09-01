-- Cumulus Advanced Git Conflict Resolution (JetBrains / VS Code merge-tool
-- parity) -- SPEC-4.1
--
-- diffview.nvim is the SOLE 3-way merge tool and file-history engine -- never
-- a hand-written conflict-marker parser or a custom diff/window UI (spec's
-- "Never" boundary). Story 4.2 reuses this same diff surface for PR review.
--
-- This file only wires the plugin up. The git work-tree guard that every
-- entry point runs first lives in cumulus.util.git. All new global keymaps
-- sit under <leader>g in a `gc` conflict/compare sub-group; diffview's own
-- in-view conflict picks are rebound off its default <leader>c* (code/lsp)
-- onto <leader>gx* so the git mnemonic hierarchy Epic 4 mandates stays
-- intact. Views render in diffview's own persistent tabpage -- never floats.

--- Run the shared git guard; returns true when it is safe to open a view.
local function guard()
  return require("cumulus.util.git").guard()
end

--- Bail (with a warning, never a raw diffview stacktrace -- frozen boundary)
--- when the current buffer is not a saved, readable, named file:
--- :DiffviewFileHistory needs a real on-disk path to operate on.
local function history_target_ok()
  local name = vim.fn.expand("%")
  if vim.bo.buftype ~= "" or name == "" or vim.fn.filereadable(name) ~= 1 then
    require("cumulus.util.ui").notify_warn(
      "File history needs a saved, readable file in the current buffer -- write it first, or open a tracked file."
    )
    return false
  end
  return true
end

--- Run `cmd` only when a diffview view is actually open; otherwise a silent
--- no-op. Close/toggle are not entry points -- a user reflexively hitting
--- close in a non-repo dir should get silence, not an error toast.
local function if_view_open(cmd)
  local ok, lib = pcall(require, "diffview.lib")
  if ok and lib.get_current_view() then
    vim.cmd(cmd)
  end
end

return {
  {
    "sindrets/diffview.nvim",
    dependencies = { "nvim-lua/plenary.nvim" },
    cmd = {
      "DiffviewOpen",
      "DiffviewClose",
      "DiffviewFileHistory",
      "DiffviewToggleFiles",
      "DiffviewFocusFiles",
    },
    -- Golden keymap set (SPEC-4.1 Design Notes). Every lhs is under <leader>g.
    keys = {
      {
        "<leader>gco",
        function()
          if not guard() then
            return
          end
          -- Mid-merge: diffview auto-opens the 3-way merge tool. Clean tree:
          -- the working-tree diff (diffview's own empty state, no crash).
          vim.cmd("DiffviewOpen")
        end,
        desc = "Conflict/compare: merge tool / working-tree diff",
      },
      {
        "<leader>gcq",
        function()
          if_view_open("DiffviewClose")
        end,
        desc = "Conflict/compare: close the diffview tabpage",
      },
      {
        "<leader>gch",
        function()
          if not guard() or not history_target_ok() then
            return
          end
          vim.cmd("DiffviewFileHistory %")
        end,
        desc = "Conflict/compare: history for the current file",
      },
      {
        "<leader>gcH",
        function()
          if not guard() or not history_target_ok() then
            return
          end
          -- Read the selection bounds directly (the callback fires while still
          -- in visual mode, so the `'<`/`'>` marks aren't committed yet) and
          -- pass diffview an explicit line range.
          local l1, l2 = vim.fn.line("v"), vim.fn.line(".")
          if l1 > l2 then
            l1, l2 = l2, l1
          end
          vim.cmd(("%d,%dDiffviewFileHistory"):format(l1, l2))
        end,
        mode = "x",
        desc = "Conflict/compare: history for the selected line range",
      },
      {
        "<leader>gcf",
        function()
          if_view_open("DiffviewToggleFiles")
        end,
        desc = "Conflict/compare: toggle the file panel",
      },
    },
    -- Function form: diffview.actions is only require()d after the plugin is
    -- loaded, so `require("cumulus.plugins.tools-diffview")` at spec-collection
    -- time (and the static spec test) never pulls in diffview itself.
    opts = function()
      local actions = require("diffview.actions")
      return {
        -- diff4_mixed: OURS | BASE | THEIRS with a distinct always-visible
        -- BASE pane -- matches AC 1 / the I/O matrix ("OURS / BASE / THEIRS")
        -- without any custom window code.
        view = {
          merge_tool = {
            layout = "diff4_mixed",
            disable_diagnostics = true,
          },
        },
        keymaps = {
          view = {
            -- Retire every diffview default in-view conflict pick that is not
            -- under the git mnemonic -- SPEC-4.1 frozen boundary. All in-view
            -- resolution must route through <leader>gx* / <leader>gX*.
            { "n", "<leader>co", false },
            { "n", "<leader>ct", false },
            { "n", "<leader>cb", false },
            { "n", "<leader>ca", false },
            { "n", "<leader>cO", false },
            { "n", "<leader>cT", false },
            { "n", "<leader>cB", false },
            { "n", "<leader>cA", false },
            { "n", "dx", false },
            { "n", "dX", false },
            -- Per-region picks, rebound under the git mnemonic (<leader>gx*).
            { "n", "<leader>gx1", actions.conflict_choose("ours"), { desc = "Conflict: choose OURS" } },
            { "n", "<leader>gx2", actions.conflict_choose("base"), { desc = "Conflict: choose BASE" } },
            { "n", "<leader>gx3", actions.conflict_choose("theirs"), { desc = "Conflict: choose THEIRS" } },
            { "n", "<leader>gxa", actions.conflict_choose("all"), { desc = "Conflict: choose ALL" } },
            { "n", "<leader>gx0", actions.conflict_choose("none"), { desc = "Conflict: choose NONE (delete region)" } },
            -- Whole-file variants, same mnemonic (capital X = all regions).
            {
              "n",
              "<leader>gX1",
              actions.conflict_choose_all("ours"),
              { desc = "Conflict: choose OURS for the whole file" },
            },
            {
              "n",
              "<leader>gX2",
              actions.conflict_choose_all("base"),
              { desc = "Conflict: choose BASE for the whole file" },
            },
            {
              "n",
              "<leader>gX3",
              actions.conflict_choose_all("theirs"),
              { desc = "Conflict: choose THEIRS for the whole file" },
            },
            {
              "n",
              "<leader>gXa",
              actions.conflict_choose_all("all"),
              { desc = "Conflict: choose ALL for the whole file" },
            },
            {
              "n",
              "<leader>gX0",
              actions.conflict_choose_all("none"),
              { desc = "Conflict: choose NONE for the whole file" },
            },
            -- diffview defaults -- keep next/prev conflict navigation.
            { "n", "]x", actions.next_conflict, { desc = "Next conflict" } },
            { "n", "[x", actions.prev_conflict, { desc = "Prev conflict" } },
          },
        },
      }
    end,
    config = function(_, opts)
      require("diffview").setup(opts)
    end,
  },
}
