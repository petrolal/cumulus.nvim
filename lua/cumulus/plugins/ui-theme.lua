-- Cumulus UI Theme & Statusline Integration Specs (Story 2.2, 8.2, 8.3)

return {
  -- AWS Theme entry spec
  {
    "cumulus/aws-theme",
    virtual = true,
    -- NOTE: must load eagerly so the colorscheme applies before first paint
    lazy = false,
    priority = 1000,
    config = function()
      require("cumulus.theme.aws").load()
    end,
  },

  -- Lualine statusline with Mode Badges, Active LSP status pill & AWS Navy palette (Story 8.2)
  {
    "nvim-lualine/lualine.nvim",
    event = "VeryLazy",
    opts = function()
      local aws = require("cumulus.theme.aws").palette
      local aws_lualine_theme = {
        normal = {
          a = { fg = aws.bg, bg = aws.aws_orange, bold = true },
          b = { fg = aws.fg, bg = aws.bg_cursorline },
          c = { fg = aws.fg_dim, bg = aws.statusline_bg },
        },
        insert = {
          a = { fg = aws.bg, bg = aws.secondary, bold = true },
          b = { fg = aws.fg, bg = aws.bg_cursorline },
          c = { fg = aws.fg_dim, bg = aws.statusline_bg },
        },
        visual = {
          a = { fg = aws.bg, bg = aws.purple, bold = true },
          b = { fg = aws.fg, bg = aws.bg_cursorline },
          c = { fg = aws.fg_dim, bg = aws.statusline_bg },
        },
        replace = {
          a = { fg = aws.bg, bg = aws.error, bold = true },
          b = { fg = aws.fg, bg = aws.bg_cursorline },
          c = { fg = aws.fg_dim, bg = aws.statusline_bg },
        },
        inactive = {
          a = { fg = aws.fg_dim, bg = aws.statusline_bg },
          b = { fg = aws.fg_dim, bg = aws.statusline_bg },
          c = { fg = aws.fg_dim, bg = aws.statusline_bg },
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
        color = { fg = aws.aws_orange, gui = "bold" },
      }

      return {
        options = {
          theme = aws_lualine_theme,
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
    end,
  },

  -- Bufferline with Devicons, Buffer Numbers & AWS Orange active indicators (Story 8.3)
  {
    "akinsho/bufferline.nvim",
    event = "VeryLazy",
    dependencies = { "nvim-tree/nvim-web-devicons" },
    opts = function()
      local aws = require("cumulus.theme.aws").palette
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
            fg = aws.aws_orange,
            bold = true,
          },
          indicator_selected = {
            fg = aws.aws_orange,
            bg = aws.aws_orange,
          },
        },
      }
    end,
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
