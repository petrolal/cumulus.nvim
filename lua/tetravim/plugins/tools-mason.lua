-- TetraVim Mason Package Management (AR2, Epic 3, Epic 11, Epic 12, Epic 39, Epic 40)

-- NOTE: plain mason.nvim's setup() has no "ensure_installed" option -- that
-- field is only understood by the separate mason-tool-installer.nvim
-- plugin. Without it, this list was silently ignored and none of these
-- tools (including cfn-lint / ansible-lint) were ever actually installed,
-- causing nvim-lint to fail when it tried to run them.
local ensure_installed = {
  "terraform-ls",
  "tflint",
  "ansible-language-server",
  "ansible-lint",
  "cfn-lint",
  "yaml-language-server",
  "dockerfile-language-server",
  "hadolint",
  "helm-ls",
  "json-lsp",
  "lemminx",
  "bash-language-server",
  "shellcheck",
  "shfmt",
  "google-java-format",
  "jdtls",
  "java-debug-adapter",
  "java-test",
  "kotlin-language-server",
  "kotlin-debug-adapter",
  "ktlint",
  "groovy-language-server",
  "npm-groovy-lint",
  "superhtml",
  "taplo",
  "checkstyle",
  "buf",
  "protols",
}

return {
  {
    "williamboman/mason.nvim",
    lazy = false,
    opts = {},
  },
  {
    "WhoIsSethDaniel/mason-tool-installer.nvim",
    -- NOTE: must load eagerly, not on an event like BufReadPre/BufNewFile.
    -- mason-tool-installer's actual auto-install trigger isn't its opts --
    -- it's a `VimEnter` autocmd registered by its own
    -- `plugin/mason-tool-installer.lua`, which only gets sourced once
    -- lazy.nvim loads the plugin. VimEnter fires exactly once, at Neovim
    -- startup; if this plugin were still event-gated on the first buffer
    -- read, that read can easily happen at or after VimEnter (e.g. `nvim .`
    -- with no file arg), so the plugin loads too late for its own VimEnter
    -- hook to ever fire -- `ensure_installed` (jdtls, kotlin-language-server,
    -- groovy-language-server, etc.) then silently never gets auto-installed,
    -- and every LSP relying on it shows "No active clients" forever until
    -- someone thinks to run `:MasonToolsInstall` by hand.
    lazy = false,
    dependencies = { "williamboman/mason.nvim" },
    opts = {
      ensure_installed = ensure_installed,
      auto_update = false,
      run_on_start = true,
    },
  },
}
