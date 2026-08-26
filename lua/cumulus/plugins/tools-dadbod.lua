-- Cumulus SQL Database Client (DataGrip Parity) -- SPEC-3.1

local ui = require("cumulus.util.ui")
local SQL_FILETYPES = { "sql", "mysql", "plsql" }
local DADBOD_SOURCE_NAME = "vim-dadbod-completion"

--- Register the vim-dadbod-completion cmp source on `bufnr`, merged
--- alongside whatever sources are already configured (LSP, buffer, etc.)
--- instead of replacing them. Safe to call more than once for the same
--- buffer (e.g. a re-fired FileType event) -- a `vim.b` flag plus an
--- explicit duplicate check both guard against accumulating repeat entries.
---@param bufnr integer
local function register_dadbod_completion(bufnr)
  if not vim.api.nvim_buf_is_valid(bufnr) then
    return
  end
  if vim.b[bufnr].cumulus_dadbod_completion_registered then
    return
  end

  local ok, cmp = pcall(require, "cmp")
  if not ok or type(cmp) ~= "table" or type(cmp.get_config) ~= "function" then
    return
  end

  -- cmp.get_config() can return nil/incomplete before cmp.setup() has ever
  -- run (e.g. this buffer's FileType fires before nvim-cmp's own
  -- InsertEnter-triggered load/setup) -- guard both the call and the shape
  -- of what it returns before indexing into it.
  local ok_cfg, cfg = pcall(cmp.get_config)
  if not ok_cfg or type(cfg) ~= "table" then
    return
  end

  local existing = type(cfg.sources) == "table" and cfg.sources or {}
  local sources = vim.deepcopy(existing)

  local already_present = false
  for _, source in ipairs(sources) do
    if source.name == DADBOD_SOURCE_NAME then
      already_present = true
      break
    end
  end
  if not already_present then
    table.insert(sources, 1, { name = DADBOD_SOURCE_NAME })
  end

  local applied_ok = vim.api.nvim_buf_call(bufnr, function()
    return pcall(cmp.setup.buffer, { sources = sources })
  end)
  if applied_ok then
    vim.b[bufnr].cumulus_dadbod_completion_registered = true
  end
end

return {
  {
    "tpope/vim-dadbod",
    cmd = { "DB", "DBUI", "DBUIToggle", "DBUIAddConnection", "DBUIFindBuffer" },
    -- Also load on sql/mysql/plsql FileType (not just the DB* commands) so
    -- the vim-dadbod-completion cmp source below gets registered as soon as
    -- a SQL buffer is opened, not only after the user has already run a DB
    -- command in some other buffer first. lazy.nvim re-fires the triggering
    -- FileType event after loading, so config()'s own FileType autocmd
    -- still fires for this exact buffer.
    ft = SQL_FILETYPES,
    dependencies = {
      "kristijanhusak/vim-dadbod-ui",
      "kristijanhusak/vim-dadbod-completion",
    },
    -- vim-dadbod-ui reads these g: globals (including g:dbs) from its own
    -- plugin/ script, which only runs once lazy.nvim loads the plugin --
    -- they must be set in init() (runs before load), not config() (runs
    -- after). Discovery is stateless: config files are re-read from disk on
    -- every init, nothing about the credentials is cached or persisted.
    init = function()
      vim.g.db_ui_use_nerd_fonts = 1
      vim.g.db_ui_show_help = 0

      local ok, dbs = pcall(function()
        return require("cumulus.util.db").discover_datasources(vim.fn.getcwd())
      end)
      if ok then
        if type(dbs) == "table" and #dbs > 0 then
          vim.g.dbs = dbs
        end
      else
        ui.notify_warn("Spring datasource auto-discovery failed: " .. tostring(dbs))
      end
    end,
    config = function()
      vim.api.nvim_create_autocmd("FileType", {
        group = vim.api.nvim_create_augroup("cumulus_dadbod_completion", { clear = true }),
        pattern = SQL_FILETYPES,
        callback = function(event)
          register_dadbod_completion(event.buf)
        end,
      })

      -- Buffers whose filetype was already sql/mysql/plsql BEFORE this
      -- plugin finished loading (e.g. the plugin loaded via a `:DB*`
      -- command run from a different buffer, while a .sql buffer was
      -- already open) never fire FileType again on their own -- catch them
      -- up immediately instead of only covering buffers opened afterward.
      for _, bufnr in ipairs(vim.api.nvim_list_bufs()) do
        if vim.api.nvim_buf_is_loaded(bufnr) and vim.tbl_contains(SQL_FILETYPES, vim.bo[bufnr].filetype) then
          register_dadbod_completion(bufnr)
        end
      end
    end,
  },

  -- Extends nvim-treesitter's ensure_installed list (seeded in
  -- core-treesitter.lua) the same way lsp-toml.lua does for "toml" -- gets
  -- syntax highlighting for .sql buffers.
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "sql" })
      end
    end,
  },
}
