-- TetraVim Deno Language Stack -- IntelliJ IDEA Ultimate bundled "Deno" parity
--
-- Deno's language server is the Deno runtime itself (`deno lsp`) -- there is
-- no Mason package, so the server is only registered when `deno` is on $PATH.
--
-- denols and ts_ls both claim javascript/typescript. The well-known fix: let
-- denols own a buffer only when its project root has a deno.json/deno.jsonc,
-- and stop any ts_ls client that also attaches inside such a root.

local DENO_MARKERS = { "deno.json", "deno.jsonc", "deps.ts", "import_map.json" }

local function deno_root(bufnr)
  local name = vim.api.nvim_buf_get_name(bufnr or 0)
  if name == "" then
    return nil
  end
  return vim.fs.root(name, DENO_MARKERS)
end

vim.api.nvim_create_autocmd("LspAttach", {
  group = vim.api.nvim_create_augroup("tetravim_deno_vs_tsserver", { clear = true }),
  callback = function(args)
    local client = vim.lsp.get_client_by_id(args.data.client_id)
    if client and client.name == "ts_ls" and deno_root(args.buf) then
      client.stop(true)
    end
  end,
})

return {
  {
    "neovim/nvim-lspconfig",
    opts = function(_, opts)
      if vim.fn.executable("deno") ~= 1 then
        return
      end
      opts.servers = opts.servers or {}
      opts.servers.denols = {
        root_dir = function(bufnr, on_dir)
          local root = deno_root(bufnr)
          if root then
            on_dir(root)
          end
        end,
        settings = {
          deno = {
            enable = true,
            lint = true,
            unstable = true,
          },
        },
      }
    end,
  },
}
