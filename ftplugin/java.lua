-- Java JDTLS Ftplugin Auto-Launcher (Story 4.1, Story 13.3 & Story 37.1)

-- SPEC-2.1: Project-Wide Safe Rename -- buffer-local override of the global
-- <leader>cr (plain vim.lsp.buf.rename() in core/keymaps.lua). Installed
-- UNCONDITIONALLY for every Java buffer, not gated on LSP attach: the I/O
-- matrix requires that pressing <leader>cr in a Java buffer with no JDTLS
-- client still produces a visible "no project-wide rename available" notify
-- (via project_rename's own guard) rather than silently falling through to
-- the default rename. The global mapping for non-JVM filetypes is untouched.
vim.keymap.set("n", "<leader>cr", function()
  require("tetravim.util.refactor").project_rename()
end, { buffer = 0, desc = "Project-Wide Rename (Java)" })

local ok, jdtls = pcall(require, "jdtls")
if not ok then
  return
end

local bundles = {}

local java_debug_path = vim.fn.expand("~/.local/share/nvim/mason/packages/java-debug-adapter/extension/server")
local java_debug_jars = vim.fn.glob(java_debug_path .. "/com.microsoft.java.debug.plugin-*.jar", true, true)
if type(java_debug_jars) == "table" and #java_debug_jars > 0 then
  vim.list_extend(bundles, java_debug_jars)
end

local java_test_path = vim.fn.expand("~/.local/share/nvim/mason/packages/java-test/extension/server")
local java_test_jars = vim.fn.glob(java_test_path .. "/*.jar", true, true)
if type(java_test_jars) == "table" and #java_test_jars > 0 then
  vim.list_extend(bundles, java_test_jars)
end

local opts = {}
local lazy_ok, lazy_config = pcall(require, "lazy.core.config")
if lazy_ok and lazy_config and lazy_config.spec and lazy_config.spec.plugins["nvim-jdtls"] then
  local plugin = lazy_config.spec.plugins["nvim-jdtls"]
  local lazy_plugin_ok, lazy_plugin = pcall(require, "lazy.core.plugin")
  if lazy_plugin_ok then
    opts = lazy_plugin.values(plugin, "opts", false) or {}
  end
end

local fname = vim.api.nvim_buf_get_name(0)
local cmd = opts.full_cmd and opts.full_cmd({ "jdtls" }) or { "jdtls" }
local root_dir = (opts.root_dir and opts.root_dir(fname))
  or jdtls.setup.find_root({ ".git", "mvnw", "gradlew", "pom.xml", "build.gradle", "build.gradle.kts" }, fname)
  or (fname and fname ~= "" and vim.fs.dirname(fname))
  or vim.fn.getcwd()

local project_name = vim.fn.fnamemodify(root_dir, ":p:h:t")
local workspace_dir = vim.fn.stdpath("cache") .. "/jdtls/workspace/" .. project_name
local has_data = false
for _, arg in ipairs(cmd) do
  if type(arg) == "string" and (arg == "-data" or arg:find("^-data")) then
    has_data = true
    break
  end
end
if not has_data then
  table.insert(cmd, "-data")
  table.insert(cmd, workspace_dir)
end

local config = {
  cmd = cmd,
  root_dir = root_dir,
  settings = opts.settings,
  init_options = {
    bundles = bundles,
  },
  on_attach = function(client, bufnr)
    jdtls.setup_dap({ hotcodereplace = "auto" })
    local ok_dap, jdtls_dap = pcall(require, "jdtls.dap")
    if ok_dap and jdtls_dap.setup_dap_main_class_configs then
      jdtls_dap.setup_dap_main_class_configs()
    end
    -- Setup Spring Boot DAP configurations (SPEC-006)
    local ok_sb, springboot_debug = pcall(require, "tetravim.util.springboot-debug")
    if ok_sb and springboot_debug.setup_springboot_dap then
      springboot_debug.setup_springboot_dap(root_dir)
    end
    if opts.on_attach then
      opts.on_attach(client, bufnr)
    end
    -- Attach notification is handled generically for every LSP client
    -- (including jdtls) by the LspAttach autocmd in lsp-core.lua.

    -- SPEC-2.1: <leader>cr is installed unconditionally at the top of this
    -- ftplugin (see there) so the no-client case still notifies -- not
    -- re-bound here.

    -- SPEC-2.2: Intelligent Extraction
    -- Wires up: extract_interface, inline, extract_method, extract_variable, extract_constant
    require("tetravim.util.extract").setup_keymaps(bufnr, "Java")
  end,
}

-- Capture JDTLS start time for sync health check (SPEC-005)
_G.tetravim_jdtls_start_time = os.time()

jdtls.start_or_attach(config)
