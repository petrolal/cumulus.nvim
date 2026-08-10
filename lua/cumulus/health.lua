-- Cumulus Healthcheck Module (Story 27.2 & Story 35.1)

local M = {}

function M.check()
  vim.health.start("Cumulus Neovim Core & Platform")

  if vim.fn.has("nvim-0.10.0") == 1 then
    vim.health.ok(string.format("Neovim version: %s (>= 0.10.0 required)", vim.version()))
  else
    vim.health.warn(string.format("Neovim version: %s (v0.10.0+ recommended)", vim.version()))
  end

  if vim.opt.confirm:get() == true then
    vim.health.ok("Global exit confirmation (vim.opt.confirm = true) is active")
  else
    vim.health.warn("Global exit confirmation is disabled")
  end

  vim.health.start("Cumulus System Dependencies")

  local binaries = {
    { name = "rg", desc = "ripgrep (required for Telescope live_grep & Snacks picker)", level = "warn" },
    { name = "fd", desc = "fd (optional high-speed file finder)", level = "info" },
    { name = "git", desc = "git (required for version control & git_files picker)", level = "warn" },
    { name = "cargo", desc = "cargo (required for building Rust native helper)", level = "info" },
    { name = "npm", desc = "npm (required for markdown-preview.nvim build)", level = "info" },
    { name = "node", desc = "node (required for markdown-preview & npm-based DevOps LSP servers)", level = "info" },
    { name = "python3", desc = "python3 (required for pynvim, ansible-lint, cfn-lint & BMad scripts)", level = "info" },
  }

  for _, b in ipairs(binaries) do
    if vim.fn.executable(b.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", b.name))
    else
      if b.level == "warn" then
        vim.health.warn(string.format("%s: NOT found on $PATH (%s)", b.name, b.desc))
      else
        vim.health.info(string.format("%s: NOT found on $PATH (%s)", b.name, b.desc))
      end
    end
  end

  local rust = require("cumulus.util.rust")
  if rust.is_available() then
    vim.health.ok(string.format("Cumulus Rust native helper ('cumulus-core'): active (%s)", rust.get_bin()))
  else
    vim.health.info("Cumulus Rust native helper ('cumulus-core'): not compiled (using Lua fallback routines)")
  end

  vim.health.start("Cumulus Signature Cloud Themes")

  local themes = { "aws-theme", "azure-theme", "gcp-theme", "oci-theme" }
  for _, theme in ipairs(themes) do
    local ok, _ = pcall(vim.cmd, "colorscheme " .. theme)
    if ok then
      vim.health.ok(string.format("Cloud Theme '%s': verified & loadable", theme))
    else
      vim.health.error(string.format("Cloud Theme '%s': FAILED to load", theme))
    end
  end

  local current_theme = require("cumulus.theme").get_current_theme()
  vim.health.info(string.format("Active persisted cloud theme: '%s'", current_theme))
  pcall(vim.cmd, "colorscheme " .. current_theme)
end

return M
