-- lua/tetravim/tests/web_lsp_spec.lua
-- Tests for HTML, CSS, JavaScript, and TypeScript LSP specs

describe("Web LSP Stack (HTML, CSS, JS, TS)", function()
  it("lsp-html spec declares treesitter and lspconfig with html server", function()
    local ok, spec = pcall(require, "tetravim.plugins.lsp-html")
    assert.is_true(ok, "lsp-html should load successfully")
    assert.is_table(spec)
    assert.equals(2, #spec)

    -- Treesitter spec
    local ts_spec = spec[1]
    assert.equals("nvim-treesitter/nvim-treesitter", ts_spec[1])
    local ts_opts = { ensure_installed = {} }
    ts_spec.opts(nil, ts_opts)
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "html"))

    -- LSP spec
    local lsp_spec = spec[2]
    assert.equals("neovim/nvim-lspconfig", lsp_spec[1])
    assert.is_table(lsp_spec.opts.servers)
    assert.is_table(lsp_spec.opts.servers.html)
  end)

  it("lsp-css spec declares treesitter and lspconfig with cssls server", function()
    local ok, spec = pcall(require, "tetravim.plugins.lsp-css")
    assert.is_true(ok, "lsp-css should load successfully")
    assert.is_table(spec)
    assert.equals(2, #spec)

    -- Treesitter spec
    local ts_spec = spec[1]
    assert.equals("nvim-treesitter/nvim-treesitter", ts_spec[1])
    local ts_opts = { ensure_installed = {} }
    ts_spec.opts(nil, ts_opts)
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "css"))

    -- LSP spec
    local lsp_spec = spec[2]
    assert.equals("neovim/nvim-lspconfig", lsp_spec[1])
    assert.is_table(lsp_spec.opts.servers)
    assert.is_table(lsp_spec.opts.servers.cssls)
  end)

  it("lsp-typescript spec declares treesitter and lspconfig with ts_ls server", function()
    local ok, spec = pcall(require, "tetravim.plugins.lsp-typescript")
    assert.is_true(ok, "lsp-typescript should load successfully")
    assert.is_table(spec)
    assert.equals(2, #spec)

    -- Treesitter spec
    local ts_spec = spec[1]
    assert.equals("nvim-treesitter/nvim-treesitter", ts_spec[1])
    local ts_opts = { ensure_installed = {} }
    ts_spec.opts(nil, ts_opts)
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "javascript"))
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "typescript"))
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "tsx"))

    -- LSP spec
    local lsp_spec = spec[2]
    assert.equals("neovim/nvim-lspconfig", lsp_spec[1])
    assert.is_table(lsp_spec.opts.servers)
    assert.is_table(lsp_spec.opts.servers.ts_ls)
  end)

  it("lsp-lua spec declares treesitter and lspconfig with lua_ls server", function()
    local ok, spec = pcall(require, "tetravim.plugins.lsp-lua")
    assert.is_true(ok, "lsp-lua should load successfully")
    assert.is_table(spec)
    assert.equals(2, #spec)

    -- Treesitter spec
    local ts_spec = spec[1]
    assert.equals("nvim-treesitter/nvim-treesitter", ts_spec[1])
    local ts_opts = { ensure_installed = {} }
    ts_spec.opts(nil, ts_opts)
    assert.is_truthy(vim.tbl_contains(ts_opts.ensure_installed, "lua"))

    -- LSP spec
    local lsp_spec = spec[2]
    assert.equals("neovim/nvim-lspconfig", lsp_spec[1])
    assert.is_table(lsp_spec.opts.servers)
    assert.is_table(lsp_spec.opts.servers.lua_ls)
  end)

  it("tools-mason includes html-lsp, css-lsp, typescript-language-server, and lua-language-server", function()
    local mason_spec = require("tetravim.plugins.tools-mason")
    local ensure_installed = nil
    for _, plugin in ipairs(mason_spec) do
      if plugin.opts and plugin.opts.ensure_installed then
        ensure_installed = plugin.opts.ensure_installed
        break
      end
    end
    assert.is_not_nil(ensure_installed)
    assert.is_truthy(vim.tbl_contains(ensure_installed, "html-lsp"))
    assert.is_truthy(vim.tbl_contains(ensure_installed, "css-lsp"))
    assert.is_truthy(vim.tbl_contains(ensure_installed, "typescript-language-server"))
    assert.is_truthy(vim.tbl_contains(ensure_installed, "lua-language-server"))
  end)

  it("tools-formatting configures formatters for html, css, javascript, and typescript", function()
    local formatting_spec = require("tetravim.plugins.tools-formatting")
    local formatters = formatting_spec[1].opts.formatters_by_ft
    assert.is_not_nil(formatters)
    assert.is_not_nil(formatters.html)
    assert.is_not_nil(formatters.css)
    assert.is_not_nil(formatters.javascript)
    assert.is_not_nil(formatters.typescript)
  end)
end)
