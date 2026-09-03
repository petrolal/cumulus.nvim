-- TetraVim "Tetris" palette integration spec.
--
-- The distribution standardises on a single canonical colour scheme. These
-- tests pin the palette hex values, the loader API, and the fact that the
-- legacy multi-provider cloud-theme switcher is gone.

describe("TetraVim Tetris Palette", function()
  local theme_module
  local tetris

  before_each(function()
    package.loaded["tetravim.theme"] = nil
    package.loaded["tetravim.theme.tetris"] = nil
    theme_module = require("tetravim.theme")
    tetris = require("tetravim.theme.tetris")
  end)

  describe("Palette values", function()
    it("preserves the seven canonical tetromino hexes as the *_pure accents", function()
      local p = tetris.palette
      assert.are.equal("#00F0F0", p.cyan_pure)
      assert.are.equal("#A000F0", p.purple_pure)
      assert.are.equal("#F0F000", p.yellow_pure)
      assert.are.equal("#00F000", p.green_pure)
      assert.are.equal("#F00000", p.red_pure)
      assert.are.equal("#FF7F00", p.orange_pure)
      assert.are.equal("#0000F0", p.blue_pure)
    end)

    it("exposes AA-legible on-background text tints of the same seven hues", function()
      local p = tetris.palette
      assert.are.equal("#111216", p.bg)
      assert.are.equal("#1E1F26", p.surface)
      assert.are.equal("#BCBEC4", p.fg)
      assert.are.equal("#5C6370", p.gray)
      assert.are.equal("#4EC9D9", p.cyan)
      assert.are.equal("#C792EA", p.purple)
      assert.are.equal("#E0C878", p.yellow)
      assert.are.equal("#98C379", p.green)
      assert.are.equal("#E06C75", p.red)
      assert.are.equal("#E0965B", p.orange)
      assert.are.equal("#6D9EF0", p.blue)
    end)

    it("carries no cloud-provider accent colours", function()
      local serialised = vim.inspect(tetris.palette):lower()
      for _, legacy in ipairs({ "#ff9900", "#c74634", "#4285f4", "#f80000", "#0078d4" }) do
        assert.is_nil(serialised:find(legacy, 1, true), "legacy hex present: " .. legacy)
      end
    end)
  end)

  describe("Loader API", function()
    it("reports a single canonical theme name", function()
      assert.are.equal("tetravim", theme_module.get_current_theme())
    end)

    it("no longer exposes the cloud provider switcher", function()
      assert.is_nil(theme_module.set_theme)
      assert.is_nil(theme_module.select_theme)
    end)

    it("applies the colourscheme via apply()/setup()/load_saved_theme()", function()
      for _, entry in ipairs({ "apply", "setup", "load_saved_theme" }) do
        vim.g.colors_name = nil
        theme_module[entry]()
        assert.are.equal("tetravim", vim.g.colors_name)
      end
    end)
  end)

  describe("Applied highlights", function()
    before_each(function()
      tetris.apply()
    end)

    local function fg(group)
      local h = vim.api.nvim_get_hl(0, { name = group, link = false })
      return h.fg and string.format("#%06X", h.fg) or nil
    end
    local function bg(group)
      local h = vim.api.nvim_get_hl(0, { name = group, link = false })
      return h.bg and string.format("#%06X", h.bg) or nil
    end

    it("maps core UI groups to the structural palette", function()
      assert.are.equal("#BCBEC4", fg("Normal"))
      assert.are.equal("#111216", bg("Normal"))
      assert.are.equal("#1E1F26", bg("NormalFloat"))
      assert.are.equal("#1E1F26", bg("StatusLine"))
      assert.are.equal("#1E1F26", bg("CursorLine"))
      assert.are.equal("#5C6370", fg("LineNr"))
      assert.are.equal("#6B7688", fg("Comment"))
    end)

    it("maps syntax / semantic tokens to the readable piece tints", function()
      assert.are.equal("#4EC9D9", fg("@type"))
      assert.are.equal("#4EC9D9", fg("@lsp.type.interface"))
      assert.are.equal("#C792EA", fg("@keyword"))
      assert.are.equal("#E0C878", fg("@function"))
      assert.are.equal("#98C379", fg("@attribute"))
      assert.are.equal("#E0965B", fg("String"))
      assert.are.equal("#E06C75", fg("DiagnosticError"))
    end)

    it("keeps the vivid tetromino hexes for chrome accents", function()
      assert.are.equal("#00F0F0", fg("CursorLineNr"))
      assert.are.equal("#00F0F0", fg("Title"))
      assert.are.equal("#F0F000", fg("MatchParen"))
      assert.are.equal("#F00000", fg("DiagnosticSignError"))
    end)
  end)
end)
