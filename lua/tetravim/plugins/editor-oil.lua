return {
  "stevearc/oil.nvim",
  cmd = "Oil",
  keys = {
    { "<leader>e", "<cmd>Oil<cr>", desc = "File Explorer" },
  },
  init = function()
    local opened_dir = false
    for _, arg in
      ipairs(vim.fn.argv() --[[@as string[] ]])
    do
      if vim.fn.isdirectory(arg) == 1 then
        opened_dir = true
        break
      end
    end
    if opened_dir then
      require("oil")
    end
  end,
  opts = {
    default_file_explorer = true,
    delete_to_trash = true,
    view_options = {
      show_hidden = false,
    },
  },
}
