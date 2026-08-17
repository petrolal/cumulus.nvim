-- Cumulus Core Keymaps (Story 1.1, Story 4.1 & Epic 9)

local map = vim.keymap.set

-- Leader alternatives for window navigation
map("n", "<leader>ww", "<C-w>w", { desc = "Cycle windows" })
map("n", "<leader>wh", "<C-w>h", { desc = "Focus left window" })
map("n", "<leader>wj", "<C-w>j", { desc = "Focus lower window" })
map("n", "<leader>wk", "<C-w>k", { desc = "Focus upper window" })
map("n", "<leader>wl", "<C-w>l", { desc = "Focus right window" })

-- Visual Selection & Line Movement Chords (Story 9.2)
map("v", "J", ":m '>+1<cr>gv=gv", { desc = "Move Down" })
map("v", "K", ":m '<-2<cr>gv=gv", { desc = "Move Up" })
map("v", "<", "<gv", { desc = "Outdent and Reselect" })
map("v", ">", ">gv", { desc = "Indent and Reselect" })
map("n", "n", "nzzzv", { desc = "Next Search Centered" })
map("n", "N", "Nzzzv", { desc = "Prev Search Centered" })

-- LSP Diagnostics & Symbol Navigation Chords (Story 9.3 & Story 13.2)
map("n", "[d", function() vim.diagnostic.jump({ count = -1 }) end, { desc = "Prev Diagnostic" })
map("n", "]d", function() vim.diagnostic.jump({ count = 1 }) end, { desc = "Next Diagnostic" })
map("n", "[e", function() vim.diagnostic.jump({ count = -1, severity = vim.diagnostic.severity.ERROR }) end, { desc = "Prev Error" })
map("n", "]e", function() vim.diagnostic.jump({ count = 1, severity = vim.diagnostic.severity.ERROR }) end, { desc = "Next Error" })
map("n", "<leader>ca", function() vim.lsp.buf.code_action() end, { desc = "Code Action" })
map("n", "<leader>cr", function() vim.lsp.buf.rename() end, { desc = "Rename Symbol" })

-- Global code group keymaps: format, diagnostics, codelens, organize
-- imports, source action, rename file, lsp info (Story 34.2)
map("n", "<leader>cd", function() vim.diagnostic.open_float() end, { desc = "Line Diagnostics" })
map({ "n", "x" }, "<leader>cf", function() require("cumulus.util.format").format({ force = true }) end, { desc = "Format" })
map({ "n", "x" }, "<leader>cF", function()
  require("conform").format({ formatters = { "injected" }, timeout_ms = 3000 })
end, { desc = "Format Injected Langs" })
map({ "n", "x" }, "<leader>cc", function() vim.lsp.codelens.run() end, { desc = "Run Codelens" })
map("n", "<leader>cC", function() vim.lsp.codelens.refresh() end, { desc = "Refresh & Display Codelens" })
map("n", "<leader>co", function()
  vim.lsp.buf.code_action({
    context = { only = { "source.organizeImports" }, diagnostics = {} },
    apply = true,
  })
end, { desc = "Organize Imports" })
map("n", "<leader>cA", function()
  vim.lsp.buf.code_action({
    context = { only = { "source" }, diagnostics = {} },
  })
end, { desc = "Source Action" })
map("n", "<leader>cR", function()
  local old_name = vim.api.nvim_buf_get_name(0)
  vim.ui.input({ prompt = "New file name: ", default = vim.fn.fnamemodify(old_name, ":t") }, function(new_name)
    if not new_name or new_name == "" then
      return
    end
    local new_path = vim.fn.fnamemodify(old_name, ":h") .. "/" .. new_name
    vim.lsp.util.rename(old_name, new_path)
    vim.cmd("edit " .. vim.fn.fnameescape(new_path))
  end)
end, { desc = "Rename File" })
-- `:LspInfo` (nvim-lspconfig) is a dead command on Neovim 0.11+: its
-- plugin/lspconfig.lua skips defining LspInfo/LspStart/LspStop entirely
-- once Neovim's own native `:lsp` command exists (see
-- `vim.fn.exists(':lsp')` check in nvim-lspconfig's plugin file). That
-- native `:lsp` only has enable/disable/restart/stop subcommands, no info
-- view, so `:checkhealth vim.lsp` is the actual replacement -- it lists
-- active clients, capabilities, and file-watcher status.
map("n", "<leader>cl", "<cmd>checkhealth vim.lsp<cr>", { desc = "Lsp Info" })

-- Per-language <leader>c* subgroups (Story 34.1): build/lint/format commands
-- for a given language stack only appear as buffer-local keymaps while
-- editing a matching filetype, so <leader>c no longer mixes e.g. Maven
-- keymaps into a Python or Terraform buffer's popup. See lang-keymaps.lua.
local lang_keymaps = require("cumulus.core.lang-keymaps")

-- ==============================================================================
-- ⭐ JVM PLATFORM KEYMAP SUITE (<leader>j) - Flagship Group
-- ==============================================================================

-- 1. Build & Tasks (<leader>jb)
map("n", "<leader>jbc", function()
  local maven = require("cumulus.util.maven")
  local gradle = require("cumulus.util.gradle")
  if maven.find_pom() then
    maven.run_maven_cmd(maven.get_mvn_cmd() .. " clean compile")
  elseif gradle.find_gradle() then
    gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " clean compile")
  else
    vim.notify("No pom.xml or build.gradle found in project", vim.log.levels.WARN)
  end
end, { desc = "Build: Clean Compile" })

map("n", "<leader>jbg", function()
  require("cumulus.util.gradle").run_gradle_task()
end, { desc = "Gradle: Select & Run Task" })

map("n", "<leader>jbm", function()
  require("cumulus.util.maven").run_maven_goal()
end, { desc = "Maven: Select & Run Goal" })

map("n", "<leader>jbo", function()
  local maven = require("cumulus.util.maven")
  local gradle = require("cumulus.util.gradle")
  maven.toggle_offline_mode()
  gradle.toggle_offline_mode()
end, { desc = "Toggle Offline Mode (-o / --offline)" })

map("n", "<leader>jbS", function()
  local sync_state = require("cumulus.util.build-sync-state")
  sync_state.reset()
  sync_state.run()
end, { desc = "Resync Dependencies (Maven/Gradle)" })

-- 2. Test Runner (<leader>jt)
map("n", "<leader>jta", function()
  require("cumulus.util.test-runner").run_test("all")
end, { desc = "Run All Tests in Workspace" })

map("n", "<leader>jtt", function()
  require("cumulus.util.test-runner").run_test("nearest")
end, { desc = "Run Nearest Test Method" })

map("n", "<leader>jtc", function()
  require("cumulus.util.test-runner").run_test("class")
end, { desc = "Run Current Test Class" })

map("n", "<leader>jtp", function()
  local ok, jdtls = pcall(require, "jdtls")
  if ok then
    jdtls.pick_test()
  else
    vim.notify("jdtls is not loaded", vim.log.levels.WARN)
  end
end, { desc = "JDTLS: Pick & Run Test" })

-- 3. Run & Execute (<leader>jr)
map("n", "<leader>jrs", function()
  local maven = require("cumulus.util.maven")
  local gradle = require("cumulus.util.gradle")
  if maven.find_pom() then
    local pom_path = vim.fn.findfile("pom.xml", vim.fn.getcwd() .. ";")
    local pom_content = pom_path ~= "" and table.concat(vim.fn.readfile(pom_path), "\n") or ""
    if pom_content:match("quarkus%-maven%-plugin") then
      maven.run_maven_cmd(maven.get_mvn_cmd() .. " quarkus:dev")
    else
      maven.run_maven_cmd(maven.get_mvn_cmd() .. " spring-boot:run")
    end
  elseif gradle.find_gradle() then
    local gradle_file = vim.fn.findfile("build.gradle", vim.fn.getcwd() .. ";")
    local gradle_kts = vim.fn.findfile("build.gradle.kts", vim.fn.getcwd() .. ";")
    local g_path = gradle_file ~= "" and gradle_file or gradle_kts
    local g_content = g_path ~= "" and table.concat(vim.fn.readfile(g_path), "\n") or ""
    if g_content:match("quarkus") then
      gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " quarkusDev")
    else
      gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " bootRun")
    end
  else
    vim.notify("No pom.xml or build.gradle found in project", vim.log.levels.WARN)
  end
end, { desc = "Run Spring Boot / Quarkus App" })

map("n", "<leader>jrg", function()
  vim.cmd("update")
  Snacks.terminal("groovy " .. vim.fn.shellescape(vim.fn.expand("%:p")))
end, { desc = "Groovy: Run Current Script" })

map("n", "<leader>jrd", function()
  require("cumulus.util.springboot-debug").launch_debug()
end, { desc = "Debug: Launch Spring Boot (DAP)" })

-- 4. Spring Boot & Frameworks (<leader>js)
map("n", "<leader>jse", function()
  require("cumulus.util.endpoints").select_endpoint()
end, { desc = "Spring: Select REST Endpoint" })

map("n", "<leader>jsb", function()
  require("cumulus.util.beans").select_bean()
end, { desc = "Spring: Select Bean Dependency" })

map("n", "<leader>jsm", function()
  require("cumulus.util.migrations").validate_migrations()
end, { desc = "Flyway: Validate Migrations" })

-- 5. Refactoring & JDTLS (<leader>jx)
map("n", "<leader>jxv", function()
  local ok, jdtls = pcall(require, "jdtls")
  if ok then
    jdtls.extract_variable()
  else
    vim.notify("jdtls is not loaded", vim.log.levels.WARN)
  end
end, { desc = "JDTLS: Extract Variable" })

map("n", "<leader>jxc", function()
  local ok, jdtls = pcall(require, "jdtls")
  if ok then
    jdtls.extract_constant()
  else
    vim.notify("jdtls is not loaded", vim.log.levels.WARN)
  end
end, { desc = "JDTLS: Extract Constant" })

map("v", "<leader>jxm", function()
  local ok, jdtls = pcall(require, "jdtls")
  if ok then
    jdtls.extract_method(true)
  else
    vim.notify("jdtls is not loaded", vim.log.levels.WARN)
  end
end, { desc = "JDTLS: Extract Method" })

map("n", "<leader>jxo", function()
  require("cumulus.util.import-optimizer").run()
end, { desc = "Optimize Java/Kotlin Imports" })

map("n", "<leader>jxH", function()
  local engine = require("cumulus.util.engine")
  if not _G.cumulus_jdtls_start_time then
    vim.notify("JDTLS not started yet", vim.log.levels.WARN)
    return
  end
  local cwd = vim.fn.getcwd()
  local status = engine.check_jdtls_sync(cwd, _G.cumulus_jdtls_start_time)
  if status and status.sync_needed then
    vim.notify(
      "JDTLS classpath is stale (modified: " .. (status.modified_file or "unknown") .. "). Run dependency sync and JdtRestart.",
      vim.log.levels.WARN
    )
  else
    vim.notify("JDTLS classpath is in sync", vim.log.levels.INFO)
  end
end, { desc = "JDTLS: Check Classpath Sync" })

-- 6. Dependencies (<leader>jd)
map("n", "<leader>jdu", function()
  local filepath = vim.api.nvim_buf_get_name(0)
  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local lenses = engine.check_dep_versions(filepath)
    if lenses and #lenses > 0 then
      vim.notify(string.format("Found %d dependencies to check", #lenses), vim.log.levels.INFO)
    else
      vim.notify("No dependencies found or already up-to-date", vim.log.levels.INFO)
    end
  end
end, { desc = "Check Dependency Versions" })

map("n", "<leader>jds", function()
  local sync_state = require("cumulus.util.build-sync-state")
  sync_state.reset()
  sync_state.run()
end, { desc = "Maven/Gradle: Resync Dependencies" })

-- 7. Engine & Diagnostics (<leader>ji)
map("n", "<leader>jii", function()
  local engine = require("cumulus.util.engine")
  local p = engine.ping()
  if p then
    vim.notify(
      string.format("Cumulus Engine Active\nVersion: %s\nScala: %s\nCommit: %s", p.version, p.scala, p.commit),
      vim.log.levels.INFO
    )
  else
    vim.notify("Cumulus Engine is not active", vim.log.levels.WARN)
  end
end, { desc = "Cumulus Engine: Status & Ping" })

map("n", "<leader>jid", "<cmd>CumulusInstallEngine<cr>", { desc = "Cumulus Engine: Download Binary" })
map("n", "<leader>jih", "<cmd>checkhealth cumulus<cr>", { desc = "Cumulus Health Check" })

-- ==============================================================================
-- 󱁢 Infrastructure & DevOps Language Scopes (<leader>o)
-- ==============================================================================
local devops = require("cumulus.util.devops")

lang_keymaps.register({
  filetypes = { "terraform", "terraform-vars", "hcl" },
  group = "<leader>ot",
  label = "terraform/opentofu",
  icon = "󱁢 ",
  keys = {
    { "<leader>oti", devops.terraform_init, "Terraform: Init" },
    { "<leader>otv", devops.terraform_validate, "Terraform: Validate" },
    { "<leader>otp", devops.terraform_plan, "Terraform: Plan" },
    { "<leader>ota", devops.terraform_apply, "Terraform: Apply" },
    { "<leader>otf", devops.terraform_fmt, "Terraform: Format" },
    { "<leader>otl", devops.terraform_lint, "Terraform: Lint (tflint)" },
    { "<leader>ots", devops.terraform_security, "Terraform: Security Scan (trivy/tfsec)" },
    { "<leader>oto", devops.terraform_output, "Terraform: Output" },
  },
})

lang_keymaps.register({
  filetypes = { "yaml", "yaml.cfn", "yaml.sam", "cloudformation", "sam" },
  condition = devops.is_cloudformation_buffer,
  group = "<leader>oc",
  label = "cloudformation/sam",
  icon = "󰅟 ",
  keys = {
    { "<leader>ocv", devops.cfn_validate, "CloudFormation: Validate Template" },
    { "<leader>ocl", devops.cfn_lint, "CloudFormation: Lint (cfn-lint)" },
    { "<leader>ocV", devops.sam_validate, "SAM: Validate" },
    { "<leader>ocb", devops.sam_build, "SAM: Build" },
    { "<leader>oci", devops.sam_local_invoke, "SAM: Local Invoke" },
    { "<leader>ocr", devops.sam_local_start_api, "SAM: Local Start API" },
    { "<leader>ocg", devops.cfn_guard_validate, "CloudFormation: Policy Check (cfn-guard)" },
  },
})

lang_keymaps.register({
  filetypes = { "yaml", "yaml.ansible", "ansible" },
  condition = devops.is_ansible_buffer,
  group = "<leader>oy",
  label = "ansible",
  icon = "󰚰 ",
  keys = {
    { "<leader>oys", devops.ansible_syntax_check, "Ansible: Syntax Check" },
    { "<leader>oyl", devops.ansible_lint, "Ansible: Lint Playbook" },
    { "<leader>oyc", devops.ansible_dry_run, "Ansible: Dry Run (--check)" },
    { "<leader>oyr", devops.ansible_run_playbook, "Ansible: Run Playbook" },
    { "<leader>oyi", devops.ansible_inventory_graph, "Ansible: Inventory Graph" },
    { "<leader>oyd", devops.ansible_doc_lookup, "Ansible: Module Documentation" },
    { "<leader>oyv", devops.ansible_vault_action, "Ansible: Vault Action" },
  },
})

lang_keymaps.register({
  filetypes = { "dockerfile" },
  group = "<leader>od",
  label = "docker",
  icon = "󰡨 ",
  keys = {
    { "<leader>odb", "<cmd>!docker build -t %:h:t .<cr>", "Docker: Build Image" },
    { "<leader>odl", "<cmd>!hadolint %<cr>", "Docker: Lint Dockerfile" },
  },
})

lang_keymaps.register({
  filetypes = { "helm" },
  group = "<leader>ok",
  label = "helm/k8s",
  icon = "󱃾 ",
  keys = {
    { "<leader>okl", "<cmd>!helm lint %:h<cr>", "Helm: Lint Chart" },
    { "<leader>okt", "<cmd>!helm template %:h<cr>", "Helm: Render Template" },
  },
})

lang_keymaps.setup()

-- Plugin & Package Management Keymaps (<leader>l)
map("n", "<leader>ll", "<cmd>Lazy<cr>", { desc = "Lazy Plugin Manager" })
map("n", "<leader>lm", "<cmd>Mason<cr>", { desc = "Mason Tool Manager" })
map("n", "<leader>lc", "<cmd>checkhealth<cr>", { desc = "Checkhealth System" })

-- Buffer Navigation Keymaps (<leader>b)
map("n", "<leader>bp", "<cmd>bprevious<cr>", { desc = "Previous Buffer" })
map("n", "<leader>bn", "<cmd>bnext<cr>", { desc = "Next Buffer" })

-- Window Management Splits & Navigation (<leader>w)
map("n", "<leader>ws", "<cmd>split<cr>", { desc = "Split Window Horizontally" })
map("n", "<leader>wv", "<cmd>vsplit<cr>", { desc = "Split Window Vertically" })
map("n", "<leader>wd", "<cmd>close<cr>", { desc = "Close Window" })

-- Session & Quit Keymaps (Story 10.1 & Story 29.1)
map("n", "<leader>qq", "<cmd>confirm qa<cr>", { desc = "Quit Neovim (Confirm)" })
map("n", "<leader>qQ", "<cmd>qa!<cr>", { desc = "Force Quit Neovim (No Save)" })

-- Cloud Theme Switcher Keymap (Story 31.2)
map("n", "<leader>ut", function()
  require("cumulus.theme").select_theme()
end, { desc = "Select Cloud Theme (AWS/Azure/GCP/OCI)" })

-- Database Client Keymaps (vim-dadbod UI)
map("n", "<leader>Du", "<cmd>DBUIToggle<cr>", { desc = "Toggle Database UI" })
map("n", "<leader>Df", "<cmd>DBUIFindBuffer<cr>", { desc = "Find DB Buffer" })
map("n", "<leader>Da", "<cmd>DBUIAddConnection<cr>", { desc = "Add DB Connection" })

-- Autoformat toggle (Story 34.2): <leader>uf toggles for the current
-- buffer only, <leader>uF toggles the global default
map("n", "<leader>uf", function()
  require("cumulus.util.format").toggle(true)
end, { desc = "Toggle Autoformat (Buffer)" })
map("n", "<leader>uF", function()
  require("cumulus.util.format").toggle(false)
end, { desc = "Toggle Autoformat (Global)" })

-- Universal File Operations: Save, Save All, Save As (Epic 33)
local function save_current_file()
  vim.cmd("update")
  local name = vim.fn.expand("%:t")
  if name == "" then
    name = "[No Name]"
  end
  vim.notify("Saved " .. name, vim.log.levels.INFO)
end

map({ "n", "i" }, "<C-s>", save_current_file, { desc = "Save Current File" })
map("n", "<leader>fs", save_current_file, { desc = "Save Current File" })

map("n", "<leader>fa", function()
  vim.cmd("wall")
  vim.notify("Saved all modified files", vim.log.levels.INFO)
end, { desc = "Save All Files" })

map("n", "<leader>fS", function()
  local current = vim.fn.expand("%:p")
  vim.ui.input({ prompt = " Save As: ", default = current }, function(input)
    if input and #input > 0 then
      vim.cmd("saveas! " .. vim.fn.fnameescape(input))
      vim.notify("Saved as: " .. input, vim.log.levels.INFO)
    end
  end)
end, { desc = "Save As..." })



