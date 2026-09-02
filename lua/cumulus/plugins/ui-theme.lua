-- Cumulus UI Theme & Statusline Integration Specs (Story 2.2, 8.2, 8.3, Story 5.1)
--
-- Consolidates theme color extraction to engine-generated highlights via generate-theme-highlights.
-- Lualine and Bufferline colors are dynamically cached on VimEnter and refreshed on theme switch.

local theme_colors = require("cumulus.util.theme_colors")

local function get_lualine_opts()
  local lualine_theme = {
    normal = {
      a = { fg = theme_colors.cache.bg, bg = theme_colors.cache.primary_color, bold = true },
      b = { fg = theme_colors.cache.fg, bg = theme_colors.cache.bg_cursorline },
      c = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
    },
    insert = {
      a = { fg = theme_colors.cache.bg, bg = theme_colors.cache.secondary, bold = true },
      b = { fg = theme_colors.cache.fg, bg = theme_colors.cache.bg_cursorline },
      c = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
    },
    visual = {
      a = { fg = theme_colors.cache.bg, bg = theme_colors.cache.purple, bold = true },
      b = { fg = theme_colors.cache.fg, bg = theme_colors.cache.bg_cursorline },
      c = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
    },
    replace = {
      a = { fg = theme_colors.cache.bg, bg = theme_colors.cache.error, bold = true },
      b = { fg = theme_colors.cache.fg, bg = theme_colors.cache.bg_cursorline },
      c = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
    },
    inactive = {
      a = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
      b = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
      c = { fg = theme_colors.cache.fg_dim, bg = theme_colors.cache.statusline_bg },
    },
  }

  local lsp_component = {
    function()
      local clients = vim.lsp.get_clients({ bufnr = 0 })
      if #clients == 0 then
        return "󰅛 No LSP"
      end
      local names = {}
      for _, client in ipairs(clients) do
        if client.name then
          table.insert(names, client.name)
        end
      end
      return "󰅍 " .. table.concat(names, ", ")
    end,
    color = { fg = theme_colors.cache.primary_color, gui = "bold" },
  }

  return {
    options = {
      theme = lualine_theme,
      globalstatus = true,
      component_separators = { left = "│", right = "│" },
      section_separators = { left = "", right = "" },
    },
    sections = {
      lualine_a = {
        {
          "mode",
          fmt = function(str)
            local icons = {
              NORMAL = "󰋜 ",
              INSERT = "󰏫 ",
              VISUAL = "󰈟 ",
              V_LINE = "󰈟 ",
              V_BLOCK = "󰈟 ",
              REPLACE = "󰑐 ",
              COMMAND = "󰌾 ",
            }
            return (icons[str] or "") .. str
          end,
        },
      },
      lualine_b = { "branch", "diff", "diagnostics" },
      lualine_c = { { "filename", path = 1 } },
      lualine_x = { lsp_component, "encoding", "fileformat", "filetype" },
      lualine_y = { "progress" },
      lualine_z = { "location" },
    },
  }
end

local function get_bufferline_opts()
  return {
    options = {
      mode = "buffers",
      numbers = "ordinal",
      diagnostics = "nvim_lsp",
      show_buffer_close_icons = true,
      show_close_icon = true,
      indicator = {
        icon = "▎",
        style = "icon",
      },
      separator_style = "thin",
    },
    highlights = {
      buffer_selected = {
        fg = theme_colors.cache.primary_color,
        bold = true,
      },
      indicator_selected = {
        fg = theme_colors.cache.primary_color,
        bg = theme_colors.cache.primary_color,
      },
    },
  }
end

return {
  -- Theme initialization on VimEnter (engine-driven, no static palettes)
  {
    "cumulus/theme-init",
    virtual = true,
    lazy = false,
    priority = 1000,
    config = function()
      local group = vim.api.nvim_create_augroup("cumulus_theme_init", { clear = true })

      -- Initialize colors on VimEnter
      vim.api.nvim_create_autocmd("VimEnter", {
        group = group,
        callback = function()
          local theme_module = require("cumulus.theme")
          local current_theme = theme_module.get_current_theme()
          theme_colors.refresh_cache(current_theme)
        end,
      })

      -- Refresh colors when theme changes
      vim.api.nvim_create_autocmd("User", {
        group = group,
        pattern = "CumulusThemeChanged",
        callback = function()
          -- theme_colors.cache is already updated by set_theme before firing this event.
          -- Do not call get_current_theme() here because set_theme might not have saved it to state yet.
          local ok_lualine, lualine = pcall(require, "lualine")
          if ok_lualine then
            lualine.setup(get_lualine_opts())
          end
          
          local ok_bufferline, bufferline = pcall(require, "bufferline")
          if ok_bufferline then
            bufferline.setup(get_bufferline_opts())
          end
        end,
      })
    end,
  },

  -- Lualine statusline with Mode Badges, Active LSP status pill & dynamic theme colors (Story 8.2)
  {
    "nvim-lualine/lualine.nvim",
    event = "VeryLazy",
    opts = get_lualine_opts,
  },

  -- Bufferline with Devicons, Buffer Numbers & dynamic theme-colored indicators (Story 8.3)
  {
    "akinsho/bufferline.nvim",
    event = "VeryLazy",
    dependencies = { "nvim-tree/nvim-web-devicons" },
    opts = get_bufferline_opts,
  },

  -- Global Devicons Specification (Story 30.2)
  {
    "nvim-tree/nvim-web-devicons",
    -- NOTE: must load eagerly because icons are consumed immediately by dashboard/statusline/telescope
    lazy = false,
    opts = {
      default = true,
    },
  },
}
