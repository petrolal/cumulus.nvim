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

local function is_jvm_project(buf)
  local ft = (buf and vim.api.nvim_buf_is_valid(buf) and vim.bo[buf].filetype) or vim.bo.filetype
  local jvm_fts = {
    java = true,
    kotlin = true,
    scala = true,
    groovy = true,
    gradle = true,
    sbt = true,
    xml = true,
    properties = true,
    jproperties = true,
  }
  if jvm_fts[ft] then
    return true
  end

  local maven = require("cumulus.util.maven")
  local gradle = require("cumulus.util.gradle")
  if maven.find_pom(buf) or gradle.find_gradle(buf) then
    return true
  end

  local dir = (buf and vim.api.nvim_buf_is_valid(buf) and vim.fs.dirname(vim.api.nvim_buf_get_name(buf)))
    or vim.fn.expand("%:p:h")
  if dir == "" or not dir then
    dir = vim.fn.getcwd()
  end

  local patterns = { "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "build.sbt", "mvnw", "gradlew" }
  return vim.fs.root(dir, patterns) ~= nil
    or vim.fs.root(vim.fn.getcwd(), patterns) ~= nil
end

lang_keymaps.register({
  filetypes = { "java", "kotlin", "scala", "groovy", "gradle", "sbt", "xml", "properties", "jproperties" },
  condition = is_jvm_project,
  group = "<leader>cj",
  label = "java/jvm build",
  icon = "󰬷 ",
  subgroups = {
    { group = "<leader>cjt", label = "test runner", icon = "󰙨 " },
  },
  keys = {
    {
      "<leader>cjm",
      function()
        require("cumulus.util.maven").run_maven_goal()
      end,
      "Maven: Select Goal",
    },
    {
      "<leader>cjg",
      function()
        require("cumulus.util.gradle").run_gradle_task()
      end,
      "Gradle: Select Task",
    },
    {
      "<leader>cjo",
      function()
        local maven = require("cumulus.util.maven")
        local gradle = require("cumulus.util.gradle")
        maven.toggle_offline_mode()
        gradle.toggle_offline_mode()
      end,
      "Toggle Offline Mode (-o / --offline)",
    },
    {
      "<leader>cje",
      function()
        require("cumulus.util.endpoints").select_endpoint()
      end,
      "Spring Boot: Select REST Endpoint",
    },
    {
      "<leader>cjb",
      function()
        require("cumulus.util.beans").select_bean()
      end,
      "Spring Boot: Select Bean Dependency",
    },
    {
      "<leader>cjv",
      function()
        require("cumulus.util.migrations").validate_migrations()
      end,
      "Flyway: Validate Migrations",
    },
    {
      "<leader>co",
      function()
        require("cumulus.util.import-optimizer").run()
      end,
      "Optimize Java/Kotlin Imports",
    },
    {
      "<leader>cjc",
      function()
        local maven = require("cumulus.util.maven")
        local gradle = require("cumulus.util.gradle")
        if maven.find_pom() then
          maven.run_maven_cmd(maven.get_mvn_cmd() .. " clean compile")
        elseif gradle.find_gradle() then
          gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " clean compile")
        else
          vim.notify("No pom.xml or build.gradle found in project", vim.log.levels.WARN)
        end
      end,
      "Build Project (Clean Compile)",
    },
    {
      "<leader>cjta",
      function()
        require("cumulus.util.test-runner").run_test("all")
      end,
      "JVM Build: Run All Tests",
    },
    {
      "<leader>cjtt",
      function()
        require("cumulus.util.test-runner").run_test("nearest")
      end,
      "Run Nearest Test Method",
    },
    {
      "<leader>cjtc",
      function()
        require("cumulus.util.test-runner").run_test("class")
      end,
      "Run Current Test Class",
    },
    {
      "<leader>cjtp",
      function()
        local ok, jdtls = pcall(require, "jdtls")
        if ok then
          jdtls.pick_test()
        else
          vim.notify("jdtls is not loaded", vim.log.levels.WARN)
        end
      end,
      "JDTLS: Pick Test",
    },
    {
      "<leader>cjs",
      function()
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
      end,
      "JVM: Run Spring Boot / Quarkus App",
    },
    {
      "<leader>cjr",
      function()
        vim.cmd("update")
        Snacks.terminal("groovy " .. vim.fn.shellescape(vim.fn.expand("%:p")))
      end,
      "Groovy: Run Script",
    },
    {
      "<leader>cjS",
      function()
        local sync_state = require("cumulus.util.build-sync-state")
        sync_state.reset()
        sync_state.run()
      end,
      "Maven/Gradle: Resync Dependencies",
    },
    {
      "<leader>cjH",
      function()
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
      end,
      "JDTLS: Check Sync Status",
    },
  },
})

lang_keymaps.register({
  filetypes = { "java", "kotlin", "scala", "groovy" },
  condition = is_jvm_project,
  group = "<leader>d",
  label = "debug",
  icon = "🐛 ",
  keys = {
    {
      "<leader>ds",
      function()
        require("cumulus.util.springboot-debug").launch_debug()
      end,
      "Debug Spring Boot Application",
    },
  },
})

lang_keymaps.register({
  filetypes = { "xml", "toml", "gradle" },
  group = "<leader>cd",
  label = "dependency",
  icon = "📦 ",
  keys = {
    {
      "<leader>cdu",
      function()
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
      end,
      "Check Dependency Versions",
    },
  },
})

lang_keymaps.register({
  filetypes = { "java", "kotlin", "scala", "groovy" },
  condition = is_jvm_project,
  group = "<leader>cx",
  label = "java refactor",
  icon = "󰨞 ",
  keys = {
    {
      "<leader>cxv",
      function()
        local ok, jdtls = pcall(require, "jdtls")
        if ok then
          jdtls.extract_variable()
        else
          vim.notify("jdtls is not loaded", vim.log.levels.WARN)
        end
      end,
      "JDTLS: Extract Variable",
    },
    {
      "<leader>cxc",
      function()
        local ok, jdtls = pcall(require, "jdtls")
        if ok then
          jdtls.extract_constant()
        else
          vim.notify("jdtls is not loaded", vim.log.levels.WARN)
        end
      end,
      "JDTLS: Extract Constant",
    },
    {
      "<leader>cxm",
      function()
        local ok, jdtls = pcall(require, "jdtls")
        if ok then
          jdtls.extract_method(true)
        else
          vim.notify("jdtls is not loaded", vim.log.levels.WARN)
        end
      end,
      "JDTLS: Extract Method",
      mode = "v",
    },
  },
})

lang_keymaps.register({
  filetypes = { "terraform", "terraform-vars", "hcl" },
  group = "<leader>ct",
  label = "terraform/opentofu",
  icon = "󱁢 ",
  keys = {
    { "<leader>ctv", "<cmd>!terraform validate<cr>", "Terraform: Validate" },
    { "<leader>ctp", "<cmd>!terraform plan<cr>", "Terraform: Plan" },
    { "<leader>ctf", "<cmd>!terraform fmt<cr>", "Terraform: Format" },
  },
})

lang_keymaps.register({
  filetypes = { "yaml.ansible", "ansible" },
  group = "<leader>cy",
  label = "ansible",
  icon = "󰚰 ",
  keys = {
    { "<leader>cya", "<cmd>!ansible-lint %<cr>", "Ansible: Lint Playbook" },
    { "<leader>cys", "<cmd>!ansible-playbook --syntax-check %<cr>", "Ansible: Syntax Check" },
  },
})

lang_keymaps.register({
  filetypes = { "dockerfile" },
  group = "<leader>cD",
  label = "docker",
  icon = "󰡨 ",
  keys = {
    { "<leader>cDb", "<cmd>!docker build -t %:h:t .<cr>", "Docker: Build Image" },
    { "<leader>cDl", "<cmd>!hadolint %<cr>", "Docker: Lint Dockerfile" },
  },
})

lang_keymaps.register({
  filetypes = { "helm" },
  group = "<leader>ck",
  label = "helm/k8s",
  icon = "󱃾 ",
  keys = {
    { "<leader>ckl", "<cmd>!helm lint %:h<cr>", "Helm: Lint Chart" },
    { "<leader>ckt", "<cmd>!helm template %:h<cr>", "Helm: Render Template" },
  },
})

lang_keymaps.setup()

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



