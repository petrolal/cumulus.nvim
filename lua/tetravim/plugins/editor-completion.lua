return {
  {
    "hrsh7th/nvim-cmp",
    event = "InsertEnter",
    opts = function(_, opts)
      opts.sources = opts.sources or {}
      for i = #opts.sources, 1, -1 do
        if opts.sources[i].name == "emoji" then
          table.remove(opts.sources, i)
        end
      end
    end,
  },
}
