-- TetraVim Core LSP Engine (Epic 3)

-- Every server configured across the lsp-*.lua/cloud-*.lua specs attaches
-- automatically, per-buffer, whenever nvim-lspconfig's `.setup()` sees a
-- matching filetype in the project -- that's already "load based on the
-- project/files present", no extra wiring needed. What's missing is
-- visibility: attaching happens silently, so there's no way to tell a
-- language server is genuinely running vs. just configured. This notifies
-- once per server *process* (deduped by client id, not by buffer) the first
-- time each one attaches.
local attach_messages = {
  jdtls = "JDTLS attached -- test runner & refactor keymaps are ready",
}

return {
  {
    "neovim/nvim-lspconfig",
    event = { "BufReadPre", "BufNewFile" },
    dependencies = {
      "williamboman/mason.nvim",
    },
    opts = {
      servers = {},
    },
    config = function(_, opts)
      local configs = require("lspconfig.configs")
      if opts.servers then
        for server, server_opts in pairs(opts.servers) do
          if configs[server] then
            configs[server].setup(server_opts or {})
          end
        end
      end

      local notified_clients = {}
      vim.api.nvim_create_autocmd("LspAttach", {
        group = vim.api.nvim_create_augroup("tetravim_lsp_attach_notify", { clear = true }),
        callback = function(args)
          local client = vim.lsp.get_client_by_id(args.data.client_id)
          if not client or notified_clients[client.id] then
            return
          end
          notified_clients[client.id] = true
          vim.notify(attach_messages[client.name] or (client.name .. " attached"), vim.log.levels.INFO)
        end,
      })
    end,
  },
}
