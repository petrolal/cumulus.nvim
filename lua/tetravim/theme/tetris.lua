-- TetraVim "Tetris" Palette — canonical, self-contained colour scheme
-- =====================================================================
--
-- This module is the single source of truth for the TetraVim visual
-- identity. It has NO external dependency: no binary, no
-- `~/.config/tetravim/theme/palette.json`. It always produces a complete,
-- spec-compliant highlight set so the editor is never left on bare Vim
-- defaults.
--
-- Token parity target: IntelliJ IDEA for the JVM trio (Java / Kotlin /
-- Scala). Semantic-token (`@lsp.type.*` / `@lsp.typemod.*`) groups are
-- populated explicitly because that is what drives IntelliJ-grade
-- colouring for jdtls, kotlin-language-server and Metals.
--
--   Interfaces / Types / Primitives ...... Cyan   (I-piece,  #00F0F0)
--   Keywords / Control flow / Modifiers .. Purple (T-piece,  #A000F0)
--   Functions / Methods / Declarations ... Yellow (O-piece,  #F0F000)
--   Annotations / Decorators ............. Green  (S-piece,  #00F000)
--   Strings / Characters ................. Orange (L-piece,  #FF7F00)
--   Constants / Enum members / Numbers ... Blue   (J-piece,  #0000F0)
--   Diagnostics / Errors ................. Red    (Z-piece,  #F00000)
--
-- Pure vs. tint — the readability model
-- -------------------------------------
-- The pure tetromino hexes are vivid, fully-saturated primaries. On the
-- `#111216` ground several of them measure well below WCAG AA and are
-- fatiguing to read as body text for hours (pure J-piece `#0000F0`
-- is ~2.0:1; pure O-piece `#F0F000` shimmers).
--
-- So every piece exists twice:
--   * `<piece>_pure` — the exact spec hex. Used for UI *chrome* and
--     transient accents that are glanced, not read: titles, the active
--     line number, selected-tab text, MatchParen, search backgrounds,
--     breakpoint signs, dashboard header, `:terminal` ANSI slots.
--   * `<piece>`      — a same-hue, chroma-reduced tint tuned to ~5:1+ on
--     the ground. Used for all on-background *code* text (Tree-sitter
--     captures, LSP semantic tokens, legacy syntax groups).
--
-- The Tetris identity is therefore fully preserved — the canonical seven
-- primaries still paint the frame of the editor — while the code buffer
-- stays legible across a long session.

local M = {}

---@class TetraVimPalette
M.palette = {
  -- Structural surfaces (spec)
  bg = "#111216", -- Background
  surface = "#1E1F26", -- Surface / Statusline / Floats / CursorLine
  fg = "#BCBEC4", -- Foreground
  gray = "#5C6370", -- Muted Gray (line numbers, borders, non-text)
  comment = "#6B7688", -- Comments — gray lifted to ~4.6:1 (was #5C6370, 3.6:1)
  member = "#A9B2C3", -- Fields / properties — cool dim tint, still scannable
  sel = "#33364A", -- Visual selection — reads over syntax colour

  -- Tetromino pieces — readable on-background text tints (spec hues)
  cyan = "#4EC9D9", -- I-piece  — types / interfaces / primitives
  purple = "#C792EA", -- T-piece  — keywords / control flow
  purple_dim = "#9A7FC7", -- T-piece  — recessive: modifiers / imports / qualifiers
  yellow = "#E0C878", -- O-piece  — functions / methods / declarations
  green = "#98C379", -- S-piece  — annotations / decorators
  red = "#E06C75", -- Z-piece  — diagnostics / errors (text)
  orange = "#E0965B", -- L-piece  — strings / characters
  blue = "#6D9EF0", -- J-piece  — constants / enums
  warn = "#E5C07B", -- Warning scale — distinct from function gold

  -- Tetromino pieces — exact spec hexes, chrome / accent / ANSI use only
  cyan_pure = "#00F0F0", -- I-piece
  purple_pure = "#A000F0", -- T-piece
  yellow_pure = "#F0F000", -- O-piece
  green_pure = "#00F000", -- S-piece
  red_pure = "#F00000", -- Z-piece
  orange_pure = "#FF7F00", -- L-piece
  blue_pure = "#0000F0", -- J-piece

  -- Derived neutrals (kept minimal; not part of the spec surface)
  bg_dark = "#0C0D10", -- EndOfBuffer / dark float border
  surface_hi = "#2A2C36", -- Pmenu selection / reference highlight
  fg_dim = "#8A8D94", -- punctuation, inlay hints, unnecessary code
}

-- Convenience aliases used only by the diagnostic scale (outside the
-- 7-role spec — chosen from the piece palette and flagged in the audit).
local p = M.palette
local diag_warn = p.warn
local diag_info = p.blue
local diag_hint = p.green

--- Map a `#rrggbb` string to the nearest xterm-256 colour index, so the
--- scheme degrades sanely on a terminal without `termguicolors`.
---@param hex string|nil
---@return integer|nil
local function hex_to_cterm(hex)
  if type(hex) ~= "string" then
    return nil
  end
  local rs, gs, bs = hex:match("^#(%x%x)(%x%x)(%x%x)$")
  if not rs then
    return nil
  end
  local r, g, b = tonumber(rs, 16), tonumber(gs, 16), tonumber(bs, 16)

  local levels = { 0, 95, 135, 175, 215, 255 }
  local function nearest(v)
    local best, best_d = 1, math.huge
    for i = 1, #levels do
      local d = math.abs(levels[i] - v)
      if d < best_d then
        best, best_d = i, d
      end
    end
    return best
  end

  local ri, gi, bi = nearest(r), nearest(g), nearest(b)
  local cube = 16 + 36 * (ri - 1) + 6 * (gi - 1) + (bi - 1)
  local cube_d = (levels[ri] - r) ^ 2 + (levels[gi] - g) ^ 2 + (levels[bi] - b) ^ 2

  local avg = (r + g + b) / 3
  local gi2 = math.floor((avg - 8) / 10 + 0.5)
  gi2 = math.max(0, math.min(23, gi2))
  local gray_val = 8 + gi2 * 10
  local gray_d = 3 * (gray_val - avg) ^ 2

  if gray_d < cube_d then
    return 232 + gi2
  end
  return cube
end

M.hex_to_cterm = hex_to_cterm

--- The full highlight map. Values are nvim_set_hl() option tables, or
--- `{ link = "Group" }`.
---@return table<string, table>
function M.highlights()
  local hl = {
    ------------------------------------------------------------------
    -- Editor UI
    ------------------------------------------------------------------
    Normal = { fg = p.fg, bg = p.bg },
    NormalNC = { fg = p.fg, bg = p.bg },
    NormalFloat = { fg = p.fg, bg = p.surface },
    FloatBorder = { fg = p.gray, bg = p.surface },
    FloatTitle = { fg = p.cyan_pure, bg = p.surface, bold = true },
    FloatFooter = { fg = p.gray, bg = p.surface },
    ColorColumn = { bg = p.surface },
    Conceal = { fg = p.gray },
    Cursor = { fg = p.bg, bg = p.fg },
    lCursor = { fg = p.bg, bg = p.fg },
    CursorIM = { fg = p.bg, bg = p.fg },
    CursorColumn = { bg = p.surface },
    CursorLine = { bg = p.surface },
    CursorLineNr = { fg = p.cyan_pure, bg = p.surface, bold = true },
    LineNr = { fg = p.gray },
    LineNrAbove = { fg = p.gray },
    LineNrBelow = { fg = p.gray },
    SignColumn = { fg = p.gray, bg = p.bg },
    FoldColumn = { fg = p.gray, bg = p.bg },
    Folded = { fg = p.fg_dim, bg = p.surface },
    Directory = { fg = p.cyan },
    EndOfBuffer = { fg = p.bg_dark },
    VertSplit = { fg = p.surface, bg = p.bg },
    WinSeparator = { fg = p.surface, bg = p.bg },
    MatchParen = { fg = p.yellow_pure, bold = true, underline = true },
    ModeMsg = { fg = p.fg, bold = true },
    MsgArea = { fg = p.fg, bg = p.bg },
    MsgSeparator = { fg = p.surface, bg = p.bg },
    MoreMsg = { fg = p.cyan },
    Question = { fg = p.green },
    NonText = { fg = p.gray },
    SpecialKey = { fg = p.gray },
    Whitespace = { fg = p.surface_hi },
    Title = { fg = p.cyan_pure, bold = true },
    Underlined = { underline = true },
    Ignore = { fg = p.gray },
    QuickFixLine = { bg = p.surface_hi, bold = true },

    -- Search / selection
    Search = { fg = p.bg, bg = p.yellow_pure },
    IncSearch = { fg = p.bg, bg = p.orange_pure, bold = true },
    CurSearch = { fg = p.bg, bg = p.orange_pure, bold = true },
    Substitute = { fg = p.bg, bg = p.red_pure },
    Visual = { bg = p.sel },
    VisualNOS = { bg = p.sel },

    -- Popup menu
    Pmenu = { fg = p.fg, bg = p.surface },
    PmenuSel = { fg = p.fg, bg = p.surface_hi, bold = true },
    PmenuKind = { fg = p.cyan, bg = p.surface },
    PmenuKindSel = { fg = p.cyan, bg = p.surface_hi },
    PmenuExtra = { fg = p.gray, bg = p.surface },
    PmenuExtraSel = { fg = p.gray, bg = p.surface_hi },
    PmenuSbar = { bg = p.surface },
    PmenuThumb = { bg = p.gray },
    WildMenu = { fg = p.bg, bg = p.cyan_pure },

    -- Status / tab / winbar
    StatusLine = { fg = p.fg, bg = p.surface },
    StatusLineNC = { fg = p.gray, bg = p.surface },
    TabLine = { fg = p.gray, bg = p.surface },
    TabLineFill = { bg = p.bg },
    TabLineSel = { fg = p.cyan_pure, bg = p.bg, bold = true },
    WinBar = { fg = p.fg, bg = p.bg },
    WinBarNC = { fg = p.gray, bg = p.bg },

    -- dropbar.nvim breadcrumbs: dim path trail, I-piece cyan for the
    -- symbol the cursor is currently inside, rounded surface menu.
    DropBarIconUISeparator = { fg = p.gray },
    DropBarIconUISeparatorMenu = { fg = p.gray },
    DropBarIconUIPickPivot = { fg = p.yellow_pure, bold = true },
    DropBarCurrentContext = { fg = p.cyan, bg = p.bg },
    DropBarMenuNormalFloat = { fg = p.fg, bg = p.surface },
    DropBarMenuFloatBorder = { fg = p.gray, bg = p.surface },
    DropBarMenuCurrentContext = { bg = p.surface_hi },
    DropBarMenuHoverEntry = { bg = p.surface_hi, bold = true },
    DropBarMenuHoverIcon = { fg = p.cyan_pure, bg = p.surface_hi },
    DropBarPreview = { bg = p.surface },

    -- Messages
    ErrorMsg = { fg = p.red, bold = true },
    WarningMsg = { fg = diag_warn },

    -- Spell
    SpellBad = { sp = p.red, undercurl = true },
    SpellCap = { sp = diag_warn, undercurl = true },
    SpellLocal = { sp = p.cyan, undercurl = true },
    SpellRare = { sp = p.purple, undercurl = true },

    -- Diff
    DiffAdd = { bg = "#0E2A16" },
    DiffChange = { bg = "#16233A" },
    DiffDelete = { bg = "#2E1214" },
    DiffText = { bg = "#1E3350", bold = true },
    diffAdded = { fg = p.green },
    diffRemoved = { fg = p.red },
    diffChanged = { fg = diag_info },
    diffOldFile = { fg = p.orange },
    diffNewFile = { fg = p.yellow },
    diffFile = { fg = p.cyan },
    diffLine = { fg = p.gray },
    diffIndexLine = { fg = p.gray },

    ------------------------------------------------------------------
    -- Legacy syntax groups (regex highlighting / no-LSP fallback)
    ------------------------------------------------------------------
    Comment = { fg = p.comment, italic = true },

    Constant = { fg = p.blue },
    String = { fg = p.orange },
    Character = { fg = p.orange },
    Number = { fg = p.blue },
    Float = { fg = p.blue },
    Boolean = { fg = p.purple },

    Identifier = { fg = p.fg },
    Function = { fg = p.yellow },

    Statement = { fg = p.purple },
    Conditional = { fg = p.purple },
    Repeat = { fg = p.purple },
    Label = { fg = p.purple },
    Operator = { fg = p.fg },
    Keyword = { fg = p.purple },
    Exception = { fg = p.purple },

    PreProc = { fg = p.purple },
    Include = { fg = p.purple },
    Define = { fg = p.purple },
    Macro = { fg = p.purple },
    PreCondit = { fg = p.purple },

    Type = { fg = p.cyan },
    StorageClass = { fg = p.purple_dim },
    Structure = { fg = p.cyan },
    Typedef = { fg = p.cyan },

    Special = { fg = p.orange },
    SpecialChar = { fg = p.orange, bold = true },
    Tag = { fg = p.purple },
    Delimiter = { fg = p.fg_dim },
    SpecialComment = { fg = p.gray, bold = true },
    Debug = { fg = p.red },

    Error = { fg = p.red, bold = true },
    Todo = { fg = p.bg, bg = p.yellow_pure, bold = true },

    ------------------------------------------------------------------
    -- Tree-sitter captures (`:h treesitter-highlight-groups`)
    ------------------------------------------------------------------
    ["@comment"] = { link = "Comment" },
    ["@comment.documentation"] = { fg = p.comment, italic = true },
    ["@comment.error"] = { fg = p.bg, bg = p.red_pure, bold = true },
    ["@comment.warning"] = { fg = p.bg, bg = diag_warn, bold = true },
    ["@comment.todo"] = { fg = p.bg, bg = p.yellow_pure, bold = true },
    ["@comment.note"] = { fg = p.bg, bg = p.cyan_pure, bold = true },

    ["@string"] = { fg = p.orange },
    ["@string.documentation"] = { fg = p.orange },
    ["@string.regexp"] = { fg = p.orange },
    ["@string.escape"] = { fg = p.orange, bold = true },
    ["@string.special"] = { fg = p.orange },
    ["@string.special.symbol"] = { fg = p.blue }, -- scala symbols / atoms
    ["@string.special.url"] = { fg = p.cyan, underline = true },
    ["@character"] = { fg = p.orange },
    ["@character.special"] = { fg = p.orange, bold = true },

    ["@constant"] = { fg = p.blue },
    ["@constant.builtin"] = { fg = p.purple }, -- null / true / false
    ["@constant.macro"] = { fg = p.blue },
    ["@number"] = { fg = p.blue },
    ["@number.float"] = { fg = p.blue },
    ["@boolean"] = { fg = p.purple },

    ["@keyword"] = { fg = p.purple },
    ["@keyword.coroutine"] = { fg = p.purple },
    ["@keyword.function"] = { fg = p.purple }, -- fun / def / void
    ["@keyword.operator"] = { fg = p.purple }, -- instanceof / in / is
    ["@keyword.import"] = { fg = p.purple_dim }, -- import / package
    ["@keyword.type"] = { fg = p.purple }, -- class / interface / enum
    ["@keyword.modifier"] = { fg = p.purple_dim }, -- public / static / final
    ["@keyword.repeat"] = { fg = p.purple },
    ["@keyword.return"] = { fg = p.purple },
    ["@keyword.debug"] = { fg = p.red },
    ["@keyword.exception"] = { fg = p.purple },
    ["@keyword.conditional"] = { fg = p.purple },
    ["@keyword.conditional.ternary"] = { fg = p.purple },
    ["@keyword.directive"] = { fg = p.purple },
    ["@keyword.directive.define"] = { fg = p.purple },
    ["@conditional"] = { fg = p.purple },
    ["@repeat"] = { fg = p.purple },
    ["@debug"] = { fg = p.red },
    ["@exception"] = { fg = p.purple },
    ["@label"] = { fg = p.purple }, -- labels, `case` labels

    ["@function"] = { fg = p.yellow },
    ["@function.builtin"] = { fg = p.yellow },
    ["@function.call"] = { fg = p.yellow },
    ["@function.macro"] = { fg = p.green }, -- scala macro annotations
    ["@function.method"] = { fg = p.yellow },
    ["@function.method.call"] = { fg = p.yellow },
    ["@method"] = { fg = p.yellow },
    ["@method.call"] = { fg = p.yellow },
    ["@constructor"] = { fg = p.cyan }, -- `new Foo()` — type-coloured

    ["@operator"] = { fg = p.fg },

    ["@variable"] = { fg = p.fg },
    ["@variable.builtin"] = { fg = p.purple, italic = true }, -- this / super
    ["@variable.parameter"] = { fg = p.fg },
    ["@variable.parameter.builtin"] = { fg = p.fg, italic = true },
    ["@variable.member"] = { fg = p.member }, -- field access
    ["@property"] = { fg = p.member },
    ["@field"] = { fg = p.member },

    ["@type"] = { fg = p.cyan },
    ["@type.builtin"] = { fg = p.cyan }, -- int / boolean / char …
    ["@type.definition"] = { fg = p.cyan },
    ["@type.qualifier"] = { fg = p.purple_dim }, -- final / sealed
    ["@storageclass"] = { fg = p.purple_dim },
    ["@attribute"] = { fg = p.green }, -- @Override / @Service …
    ["@attribute.builtin"] = { fg = p.green },
    ["@annotation"] = { fg = p.green },

    ["@module"] = { fg = p.fg_dim }, -- package / namespace segments
    ["@module.builtin"] = { fg = p.fg_dim },
    ["@namespace"] = { fg = p.fg_dim },

    ["@punctuation.delimiter"] = { fg = p.fg_dim },
    ["@punctuation.bracket"] = { fg = p.fg_dim },
    ["@punctuation.special"] = { fg = p.purple }, -- ${} interpolation

    ["@tag"] = { fg = p.purple }, -- xml/html ftplugins
    ["@tag.builtin"] = { fg = p.purple },
    ["@tag.attribute"] = { fg = p.cyan },
    ["@tag.delimiter"] = { fg = p.fg_dim },

    ["@markup.heading"] = { fg = p.cyan, bold = true },
    ["@markup.strong"] = { bold = true },
    ["@markup.italic"] = { italic = true },
    ["@markup.strikethrough"] = { strikethrough = true },
    ["@markup.underline"] = { underline = true },
    ["@markup.link"] = { fg = p.cyan, underline = true },
    ["@markup.link.label"] = { fg = p.purple },
    ["@markup.link.url"] = { fg = p.cyan, underline = true },
    ["@markup.raw"] = { fg = p.orange },
    ["@markup.raw.block"] = { fg = p.fg },
    ["@markup.list"] = { fg = p.purple },
    ["@markup.quote"] = { fg = p.gray, italic = true },

    ["@diff.plus"] = { fg = p.green },
    ["@diff.minus"] = { fg = p.red },
    ["@diff.delta"] = { fg = diag_info },

    ["@error"] = { fg = p.red },
    ["@none"] = {},

    ------------------------------------------------------------------
    -- LSP semantic tokens — the IntelliJ-parity layer
    -- (jdtls / kotlin-language-server / Metals)
    ------------------------------------------------------------------
    ["@lsp.type.namespace"] = { fg = p.fg_dim },
    ["@lsp.type.type"] = { fg = p.cyan },
    ["@lsp.type.class"] = { fg = p.cyan },
    ["@lsp.type.interface"] = { fg = p.cyan },
    ["@lsp.type.struct"] = { fg = p.cyan },
    ["@lsp.type.enum"] = { fg = p.cyan }, -- the enum *type* (like a class)
    ["@lsp.type.typeParameter"] = { fg = p.cyan, italic = true },
    ["@lsp.type.typeAlias"] = { fg = p.cyan },
    ["@lsp.type.parameter"] = { fg = p.fg },
    ["@lsp.type.variable"] = { fg = p.fg },
    ["@lsp.type.property"] = { fg = p.member },
    ["@lsp.type.field"] = { fg = p.member },
    ["@lsp.type.enumMember"] = { fg = p.blue }, -- enum *constants*
    ["@lsp.type.event"] = { fg = p.fg },
    ["@lsp.type.function"] = { fg = p.yellow },
    ["@lsp.type.method"] = { fg = p.yellow },
    ["@lsp.type.macro"] = { fg = p.purple },
    ["@lsp.type.keyword"] = { fg = p.purple },
    ["@lsp.type.modifier"] = { fg = p.purple_dim },
    ["@lsp.type.comment"] = { link = "Comment" },
    ["@lsp.type.string"] = { fg = p.orange },
    ["@lsp.type.number"] = { fg = p.blue },
    ["@lsp.type.regexp"] = { fg = p.orange },
    ["@lsp.type.operator"] = { fg = p.fg },
    ["@lsp.type.decorator"] = { fg = p.green },
    ["@lsp.type.annotation"] = { fg = p.green }, -- @Override / @Service …
    ["@lsp.type.annotationMember"] = { fg = p.green },
    ["@lsp.type.selfKeyword"] = { fg = p.purple, italic = true },
    ["@lsp.type.builtinType"] = { fg = p.cyan },
    ["@lsp.type.escapeSequence"] = { fg = p.orange, bold = true },

    -- Type modifiers (jdtls emits these; key IntelliJ affordances)
    ["@lsp.typemod.class.abstract"] = { fg = p.cyan, italic = true },
    ["@lsp.typemod.interface.abstract"] = { fg = p.cyan, italic = true },
    ["@lsp.typemod.method.static"] = { fg = p.yellow, italic = true },
    ["@lsp.typemod.method.abstract"] = { fg = p.yellow, italic = true },
    ["@lsp.typemod.method.defaultLibrary"] = { fg = p.yellow },
    ["@lsp.typemod.function.defaultLibrary"] = { fg = p.yellow },
    ["@lsp.typemod.function.declaration"] = { fg = p.yellow, bold = true },
    ["@lsp.typemod.method.declaration"] = { fg = p.yellow, bold = true },
    ["@lsp.typemod.variable.static"] = { fg = p.fg, italic = true },
    ["@lsp.typemod.variable.readonly"] = { fg = p.blue }, -- final field → constant
    ["@lsp.typemod.variable.static.readonly"] = { fg = p.blue, bold = true },
    ["@lsp.typemod.property.readonly"] = { fg = p.blue },
    ["@lsp.typemod.property.static"] = { fg = p.member, italic = true },
    ["@lsp.typemod.parameter.readonly"] = { fg = p.fg },
    ["@lsp.typemod.parameter.declaration"] = { fg = p.fg },
    ["@lsp.typemod.enumMember.readonly"] = { fg = p.blue },
    ["@lsp.typemod.type.defaultLibrary"] = { fg = p.cyan },
    ["@lsp.typemod.class.defaultLibrary"] = { fg = p.cyan },
    ["@lsp.typemod.enum.defaultLibrary"] = { fg = p.cyan },
    ["@lsp.typemod.annotation.defaultLibrary"] = { fg = p.green },
    ["@lsp.typemod.keyword.documentation"] = { fg = p.purple },

    -- Neutralise a few semantic tokens that would otherwise flatten
    -- Tree-sitter's richer detail.
    ["@lsp.type.unresolvedReference"] = {},
    ["@lsp.typemod.variable.globalScope"] = {},

    ------------------------------------------------------------------
    -- Diagnostics (spec defines Red for errors; warn/info/hint are
    -- drawn from the piece palette and noted in the audit report)
    ------------------------------------------------------------------
    DiagnosticError = { fg = p.red },
    DiagnosticWarn = { fg = diag_warn },
    DiagnosticInfo = { fg = diag_info },
    DiagnosticHint = { fg = diag_hint },
    DiagnosticOk = { fg = p.green },

    DiagnosticVirtualTextError = { fg = p.red, bg = "#1E1416" },
    DiagnosticVirtualTextWarn = { fg = diag_warn, bg = "#1E1D12" },
    DiagnosticVirtualTextInfo = { fg = diag_info, bg = "#14192B" },
    DiagnosticVirtualTextHint = { fg = diag_hint, bg = "#0E1E10" },
    DiagnosticVirtualTextOk = { fg = p.green, bg = "#0E1E10" },

    DiagnosticUnderlineError = { sp = p.red, undercurl = true },
    DiagnosticUnderlineWarn = { sp = diag_warn, undercurl = true },
    DiagnosticUnderlineInfo = { sp = diag_info, undercurl = true },
    DiagnosticUnderlineHint = { sp = diag_hint, undercurl = true },
    DiagnosticUnderlineOk = { sp = p.green, undercurl = true },

    DiagnosticFloatingError = { fg = p.red },
    DiagnosticFloatingWarn = { fg = diag_warn },
    DiagnosticFloatingInfo = { fg = diag_info },
    DiagnosticFloatingHint = { fg = diag_hint },
    DiagnosticFloatingOk = { fg = p.green },

    DiagnosticSignError = { fg = p.red_pure },
    DiagnosticSignWarn = { fg = diag_warn },
    DiagnosticSignInfo = { fg = diag_info },
    DiagnosticSignHint = { fg = diag_hint },
    DiagnosticSignOk = { fg = p.green },

    DiagnosticDeprecated = { fg = p.gray, strikethrough = true },
    DiagnosticUnnecessary = { fg = p.fg_dim },

    ------------------------------------------------------------------
    -- Core LSP UI
    ------------------------------------------------------------------
    LspReferenceText = { bg = p.surface_hi },
    LspReferenceRead = { bg = p.surface_hi },
    LspReferenceWrite = { bg = p.surface_hi, bold = true },
    LspSignatureActiveParameter = { fg = p.yellow, bold = true },
    LspInlayHint = { fg = p.fg_dim, bg = p.surface },
    LspCodeLens = { fg = p.gray, italic = true },
    LspCodeLensSeparator = { fg = p.gray },
    LspInfoBorder = { fg = p.gray, bg = p.surface },

    ------------------------------------------------------------------
    -- Plugin groups (only those in lazy-lock.json)
    ------------------------------------------------------------------
    -- gitsigns.nvim
    GitSignsAdd = { fg = p.green },
    GitSignsChange = { fg = diag_info },
    GitSignsDelete = { fg = p.red },
    GitSignsAddNr = { fg = p.green },
    GitSignsChangeNr = { fg = diag_info },
    GitSignsDeleteNr = { fg = p.red },
    GitSignsAddLn = { bg = "#0E2A16" },
    GitSignsChangeLn = { bg = "#16233A" },
    GitSignsDeleteLn = { bg = "#2E1214" },
    GitSignsCurrentLineBlame = { fg = p.gray, italic = true },
    GitSignsAddInline = { bg = "#12401F" },
    GitSignsChangeInline = { bg = "#1E3350" },
    GitSignsDeleteInline = { bg = "#4A1B1E" },

    -- telescope.nvim
    TelescopeNormal = { fg = p.fg, bg = p.surface },
    TelescopeBorder = { fg = p.gray, bg = p.surface },
    TelescopeTitle = { fg = p.fg, bold = true },
    TelescopePromptNormal = { fg = p.fg, bg = p.surface_hi },
    TelescopePromptBorder = { fg = p.surface_hi, bg = p.surface_hi },
    TelescopePromptTitle = { fg = p.bg, bg = p.purple_pure, bold = true },
    TelescopePromptPrefix = { fg = p.purple },
    TelescopePromptCounter = { fg = p.gray },
    TelescopeResultsTitle = { fg = p.surface, bg = p.surface },
    TelescopePreviewTitle = { fg = p.bg, bg = p.green_pure, bold = true },
    TelescopeSelection = { bg = p.surface_hi, bold = true },
    TelescopeSelectionCaret = { fg = p.purple, bg = p.surface_hi },
    TelescopeMatching = { fg = p.yellow, bold = true },
    TelescopeMultiSelection = { fg = p.cyan },

    -- nvim-cmp
    CmpItemAbbr = { fg = p.fg },
    CmpItemAbbrDeprecated = { fg = p.gray, strikethrough = true },
    CmpItemAbbrMatch = { fg = p.yellow, bold = true },
    CmpItemAbbrMatchFuzzy = { fg = p.yellow, bold = true },
    CmpItemMenu = { fg = p.gray, italic = true },
    CmpItemKind = { fg = p.gray },
    CmpItemKindKeyword = { fg = p.purple },
    CmpItemKindText = { fg = p.fg },
    CmpItemKindMethod = { fg = p.yellow },
    CmpItemKindFunction = { fg = p.yellow },
    CmpItemKindConstructor = { fg = p.cyan },
    CmpItemKindField = { fg = p.member },
    CmpItemKindVariable = { fg = p.fg },
    CmpItemKindClass = { fg = p.cyan },
    CmpItemKindInterface = { fg = p.cyan },
    CmpItemKindModule = { fg = p.fg_dim },
    CmpItemKindProperty = { fg = p.member },
    CmpItemKindUnit = { fg = p.blue },
    CmpItemKindValue = { fg = p.blue },
    CmpItemKindEnum = { fg = p.cyan },
    CmpItemKindEnumMember = { fg = p.blue },
    CmpItemKindConstant = { fg = p.blue },
    CmpItemKindStruct = { fg = p.cyan },
    CmpItemKindSnippet = { fg = p.orange },
    CmpItemKindColor = { fg = p.orange },
    CmpItemKindFile = { fg = p.cyan },
    CmpItemKindReference = { fg = p.fg },
    CmpItemKindFolder = { fg = p.cyan },
    CmpItemKindEvent = { fg = p.green },
    CmpItemKindOperator = { fg = p.fg },
    CmpItemKindTypeParameter = { fg = p.cyan },

    -- which-key.nvim
    WhichKey = { fg = p.purple },
    WhichKeyGroup = { fg = p.cyan },
    WhichKeyDesc = { fg = p.fg },
    WhichKeySeparator = { fg = p.gray },
    WhichKeyValue = { fg = p.gray },
    WhichKeyFloat = { bg = p.surface },
    WhichKeyBorder = { fg = p.gray, bg = p.surface },
    WhichKeyTitle = { fg = p.cyan_pure, bg = p.surface, bold = true },

    -- oil.nvim
    OilDir = { fg = p.cyan },
    OilDirIcon = { fg = p.cyan },
    OilLink = { fg = p.purple, underline = true },
    OilLinkTarget = { fg = p.gray },
    OilCopy = { fg = p.green, bold = true },
    OilMove = { fg = diag_info, bold = true },
    OilChange = { fg = p.yellow, bold = true },
    OilCreate = { fg = p.green, bold = true },
    OilDelete = { fg = p.red, bold = true },
    OilPermissionNone = { fg = p.gray },
    OilPermissionRead = { fg = p.yellow },
    OilPermissionWrite = { fg = p.red },
    OilPermissionExecute = { fg = p.green },
    OilTypeDir = { fg = p.cyan },
    OilTypeFile = { fg = p.fg },
    OilTypeLink = { fg = p.purple },

    -- trouble.nvim
    TroubleNormal = { fg = p.fg, bg = p.surface },
    TroubleNormalNC = { fg = p.fg, bg = p.surface },
    TroubleText = { fg = p.fg },
    TroubleCount = { fg = p.purple, bg = p.surface_hi, bold = true },
    TroubleFoldIcon = { fg = p.gray },
    TroubleIndent = { fg = p.gray },
    TroubleLocation = { fg = p.gray },
    TroublePos = { fg = p.gray },
    TroubleSource = { fg = p.gray, italic = true },

    -- noice.nvim
    NoiceCmdline = { fg = p.fg, bg = p.surface },
    NoiceCmdlinePopup = { fg = p.fg, bg = p.surface },
    NoiceCmdlinePopupBorder = { fg = p.gray, bg = p.surface },
    NoiceCmdlinePopupTitle = { fg = p.cyan_pure, bold = true },
    NoiceCmdlineIcon = { fg = p.purple },
    NoiceCmdlineIconSearch = { fg = p.yellow },
    NoiceConfirm = { fg = p.fg, bg = p.surface },
    NoiceConfirmBorder = { fg = p.gray, bg = p.surface },
    NoiceMini = { fg = p.fg, bg = p.surface },
    NoicePopup = { fg = p.fg, bg = p.surface },
    NoicePopupBorder = { fg = p.gray, bg = p.surface },
    NoiceLspProgressTitle = { fg = p.fg },
    NoiceLspProgressClient = { fg = p.cyan, bold = true },
    NoiceLspProgressSpinner = { fg = p.purple },

    -- snacks.nvim
    SnacksNormal = { fg = p.fg, bg = p.surface },
    SnacksBackdrop = { bg = p.bg_dark },
    SnacksWinBar = { fg = p.cyan_pure, bg = p.surface, bold = true },
    SnacksWinBarNC = { fg = p.gray, bg = p.surface },
    SnacksDashboardHeader = { fg = p.cyan_pure, bold = true },
    SnacksDashboardTitle = { fg = p.purple },
    SnacksDashboardDesc = { fg = p.fg },
    SnacksDashboardKey = { fg = p.yellow },
    SnacksDashboardIcon = { fg = p.green },
    SnacksDashboardFooter = { fg = p.gray, italic = true },
    SnacksDashboardSpecial = { fg = p.purple },
    -- Quiet indent guides; the scope the cursor is inside gets the I-piece
    -- cyan tint so the active nesting level reads without shouting.
    SnacksIndent = { fg = p.surface_hi },
    SnacksIndentScope = { fg = p.cyan },
    SnacksNotifierInfo = { fg = diag_info, bg = p.surface },
    SnacksNotifierWarn = { fg = diag_warn, bg = p.surface },
    SnacksNotifierError = { fg = p.red, bg = p.surface },
    SnacksNotifierDebug = { fg = p.gray, bg = p.surface },
    SnacksNotifierTrace = { fg = p.purple, bg = p.surface },
    SnacksPickerDir = { fg = p.gray },
    SnacksPickerMatch = { fg = p.yellow, bold = true },
    SnacksPickerBorder = { fg = p.gray, bg = p.surface },
    SnacksPickerTitle = { fg = p.cyan, bold = true },

    -- render-markdown.nvim (names referenced in editor-markdown.lua)
    RenderMarkdownInfo = { fg = diag_info },
    RenderMarkdownSuccess = { fg = p.green },
    RenderMarkdownHint = { fg = diag_hint },
    RenderMarkdownWarn = { fg = diag_warn },
    RenderMarkdownError = { fg = p.red },
    RenderMarkdownH1 = { fg = p.cyan_pure, bold = true },
    RenderMarkdownH2 = { fg = p.purple, bold = true },
    RenderMarkdownH3 = { fg = p.yellow, bold = true },
    RenderMarkdownH4 = { fg = p.green, bold = true },
    RenderMarkdownH5 = { fg = p.orange, bold = true },
    RenderMarkdownH6 = { fg = p.blue, bold = true },
    RenderMarkdownCode = { bg = p.surface },
    RenderMarkdownCodeInline = { fg = p.orange, bg = p.surface },
    RenderMarkdownBullet = { fg = p.purple },
    RenderMarkdownQuote = { fg = p.gray },
    RenderMarkdownTableHead = { fg = p.gray },
    RenderMarkdownTableRow = { fg = p.fg },
    RenderMarkdownLink = { fg = p.cyan, underline = true },
    RenderMarkdownChecked = { fg = p.green },
    RenderMarkdownUnchecked = { fg = p.gray },

    -- mason.nvim
    MasonHeader = { fg = p.bg, bg = p.purple_pure, bold = true },
    MasonHeaderSecondary = { fg = p.bg, bg = p.cyan_pure, bold = true },
    MasonHighlight = { fg = p.cyan },
    MasonHighlightBlock = { fg = p.bg, bg = p.cyan_pure },
    MasonHighlightBlockBold = { fg = p.bg, bg = p.cyan_pure, bold = true },
    MasonMuted = { fg = p.gray },
    MasonMutedBlock = { fg = p.fg, bg = p.surface_hi },
    MasonError = { fg = p.red },

    -- nvim-dap / nvim-dap-ui / virtual text
    DapBreakpoint = { fg = p.red_pure },
    DapBreakpointCondition = { fg = p.orange },
    DapBreakpointRejected = { fg = p.gray },
    DapLogPoint = { fg = diag_info },
    DapStopped = { fg = p.yellow },
    DapStoppedLine = { bg = "#1E1D12" },
    NvimDapVirtualText = { fg = p.gray, italic = true },
    NvimDapVirtualTextChanged = { fg = p.yellow, italic = true },
    NvimDapVirtualTextError = { fg = p.red, italic = true },
    DapUINormal = { fg = p.fg, bg = p.surface },
    DapUIVariable = { fg = p.fg },
    DapUIScope = { fg = p.cyan },
    DapUIType = { fg = p.purple },
    DapUIValue = { fg = p.fg },
    DapUIModifiedValue = { fg = p.yellow, bold = true },
    DapUIDecoration = { fg = p.cyan },
    DapUIThread = { fg = p.green },
    DapUIStoppedThread = { fg = p.cyan },
    DapUIFrameName = { fg = p.fg },
    DapUISource = { fg = p.purple },
    DapUILineNumber = { fg = p.cyan },
    DapUIFloatBorder = { fg = p.gray, bg = p.surface },
    DapUIWatchesEmpty = { fg = p.red },
    DapUIWatchesValue = { fg = p.green },
    DapUIWatchesError = { fg = p.red },
    DapUIBreakpointsPath = { fg = p.cyan },
    DapUIBreakpointsInfo = { fg = p.green },
    DapUIBreakpointsCurrentLine = { fg = p.yellow, bold = true },
    DapUIBreakpointsDisabledLine = { fg = p.gray },
    DapUIButton = { fg = p.bg, bg = p.cyan_pure },
    DapUIPlayPause = { fg = p.green, bg = p.surface },
    DapUIRestart = { fg = p.green, bg = p.surface },
    DapUIStop = { fg = p.red, bg = p.surface },
    DapUIStepOver = { fg = p.cyan, bg = p.surface },
    DapUIStepInto = { fg = p.cyan, bg = p.surface },
    DapUIStepOut = { fg = p.cyan, bg = p.surface },
    DapUIStepBack = { fg = p.cyan, bg = p.surface },
    DapUIUnavailable = { fg = p.gray },

    -- diffview.nvim
    DiffviewNormal = { link = "Normal" },
    DiffviewFilePanelTitle = { fg = p.cyan_pure, bold = true },
    DiffviewFilePanelCounter = { fg = p.purple, bold = true },
    DiffviewFilePanelFileName = { fg = p.fg },
    DiffviewFilePanelPath = { fg = p.gray },
    DiffviewStatusAdded = { fg = p.green },
    DiffviewStatusModified = { fg = diag_info },
    DiffviewStatusDeleted = { fg = p.red },
    DiffviewStatusRenamed = { fg = p.purple },
    DiffviewStatusUntracked = { fg = p.cyan },
    DiffviewFolderName = { fg = p.cyan },
    DiffviewFolderSign = { fg = p.gray },
    DiffviewDiffAdd = { link = "DiffAdd" },
    DiffviewDiffDelete = { link = "DiffDelete" },

    -- vim-dadbod-ui
    NotificationInfo = { fg = diag_info },
    NotificationWarning = { fg = diag_warn },
    NotificationError = { fg = p.red },

    -- treesitter-context (bundled behaviour of nvim-treesitter)
    TreesitterContext = { bg = p.surface },
    TreesitterContextLineNumber = { fg = p.gray, bg = p.surface },
    TreesitterContextBottom = { underline = true, sp = p.gray },

    -- bufferline.nvim (base groups; ui-theme.lua overrides the accents)
    BufferLineFill = { bg = p.bg_dark },
    BufferLineBackground = { fg = p.gray, bg = p.surface },
    BufferLineBufferVisible = { fg = p.fg_dim, bg = p.surface },
    BufferLineBufferSelected = { fg = p.cyan_pure, bg = p.bg, bold = true },
    BufferLineIndicatorSelected = { fg = p.cyan_pure, bg = p.bg },
    BufferLineSeparator = { fg = p.bg_dark, bg = p.surface },
    BufferLineSeparatorSelected = { fg = p.bg_dark, bg = p.bg },
    BufferLineCloseButton = { fg = p.gray, bg = p.surface },
    BufferLineCloseButtonSelected = { fg = p.red, bg = p.bg },
    BufferLineModified = { fg = p.green, bg = p.surface },
    BufferLineModifiedSelected = { fg = p.green, bg = p.bg },
  }

  return hl
end

--- Apply the palette to the running editor.
--- Performs a full `hi clear` + `syntax reset` so no stale groups linger.
function M.apply()
  if vim.g.colors_name then
    vim.cmd("highlight clear")
  end
  if vim.fn.exists("syntax_on") == 1 then
    vim.cmd("syntax reset")
  end

  vim.o.termguicolors = true
  vim.o.background = "dark"
  vim.g.colors_name = "tetravim"

  local set_hl = vim.api.nvim_set_hl
  for group, spec in pairs(M.highlights()) do
    if not spec.link then
      -- 256-colour fallback so the scheme still reads without truecolor.
      if spec.fg then
        spec.ctermfg = hex_to_cterm(spec.fg)
      end
      if spec.bg then
        spec.ctermbg = hex_to_cterm(spec.bg)
      end
    end
    set_hl(0, group, spec)
  end

  -- Terminal ANSI colours — :terminal keeps the *pure* tetromino hues so
  -- shell output stays fully saturated (it is glanced, not read as code).
  vim.g.terminal_color_0 = p.surface
  vim.g.terminal_color_1 = p.red_pure
  vim.g.terminal_color_2 = p.green_pure
  vim.g.terminal_color_3 = p.yellow_pure
  vim.g.terminal_color_4 = p.blue_pure
  vim.g.terminal_color_5 = p.purple_pure
  vim.g.terminal_color_6 = p.cyan_pure
  vim.g.terminal_color_7 = p.fg
  vim.g.terminal_color_8 = p.gray
  vim.g.terminal_color_9 = p.red_pure
  vim.g.terminal_color_10 = p.green_pure
  vim.g.terminal_color_11 = p.yellow_pure
  vim.g.terminal_color_12 = p.blue_pure
  vim.g.terminal_color_13 = p.purple_pure
  vim.g.terminal_color_14 = p.cyan_pure
  vim.g.terminal_color_15 = "#FFFFFF"
end

return M
