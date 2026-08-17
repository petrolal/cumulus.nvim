-- Cumulus Git Signs & Version Control Integration (Story 6.2 & Story 10.2)

return {
  {
    "lewis6991/gitsigns.nvim",
    event = { "BufReadPre", "BufNewFile" },
    opts = {
      signs = {
        add = { text = "▎" },
        change = { text = "▎" },
        delete = { text = "" },
        topdelete = { text = "" },
        changedelete = { text = "▎" },
        untracked = { text = "▎" },
      },
      on_attach = function(bufnr)
        local gs = package.loaded.gitsigns

        local function map(mode, l, r, opts)
          opts = opts or {}
          opts.buffer = bufnr
          vim.keymap.set(mode, l, r, opts)
        end

        -- Navigation
        map("n", "]c", function()
          if vim.wo.diff then return "]c" end
          vim.schedule(function() gs.next_hunk() end)
          return "<Ignore>"
        end, { expr = true, desc = "Next Hunk" })

        map("n", "[c", function()
          if vim.wo.diff then return "[c" end
          vim.schedule(function() gs.prev_hunk() end)
          return "<Ignore>"
        end, { expr = true, desc = "Prev Hunk" })

        map("n", "<leader>gb", function() gs.blame_line({ full = true }) end, { desc = "Blame Line" })
        map("n", "<leader>gB", gs.blame, { desc = "Blame Buffer Toggle" })
        map("n", "<leader>gd", gs.diffthis, { desc = "Git Diff This" })
        map("n", "<leader>ghs", gs.stage_hunk, { desc = "Stage Hunk" })
        map("n", "<leader>ghr", gs.reset_hunk, { desc = "Reset Hunk" })
        map("n", "<leader>ghu", gs.undo_stage_hunk, { desc = "Undo Stage Hunk" })
        map("n", "<leader>ghp", gs.preview_hunk, { desc = "Preview Hunk" })
      end,
    },
  },
}
