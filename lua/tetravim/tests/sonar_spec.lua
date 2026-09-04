-- Unit tests for tetravim.util.sonar (Epic 6, Story 6.1)
--
-- Covers the pure `sonar-project.properties` parser (settings_from_properties
-- / project_key), the FILETYPES contract, and the
-- missing-`sonarlint-language-server` behaviour of language_server_cmd.
-- `vim.fn.executable` is monkeypatched -- nothing on the real $PATH is
-- consulted.

describe("tetravim.util.sonar", function()
  local sonar = require("tetravim.util.sonar")

  local orig_executable
  before_each(function()
    orig_executable = vim.fn.executable
  end)
  after_each(function()
    vim.fn.executable = orig_executable
  end)

  describe("FILETYPES", function()
    it("covers java, kotlin and scala", function()
      assert.are.same({ "java", "kotlin", "scala" }, sonar.FILETYPES)
    end)
  end)

  describe("settings_from_properties", function()
    it("parses key=value pairs, trimming whitespace and skipping comments/blanks", function()
      local text = table.concat({
        "# SonarQube project config",
        "",
        "sonar.projectKey = com.example:my-service ",
        "sonar.qualitygate.wait=true",
        "! bang comment",
        "sonar.host.url: https://sonar.example.com",
      }, "\n")
      local settings = sonar.settings_from_properties(text)
      assert.are.equal("com.example:my-service", settings["sonar.projectKey"])
      assert.are.equal("true", settings["sonar.qualitygate.wait"])
      assert.are.equal("https://sonar.example.com", settings["sonar.host.url"])
      assert.is_nil(settings["# SonarQube project config"])
    end)

    it("returns an empty table for empty / nil input", function()
      assert.are.same({}, sonar.settings_from_properties(""))
      assert.are.same({}, sonar.settings_from_properties(nil))
    end)
  end)

  describe("project_key", function()
    it("extracts sonar.projectKey", function()
      assert.are.equal("k", sonar.project_key("sonar.projectKey=k\nsonar.foo=bar"))
    end)

    it("is nil when absent", function()
      assert.is_nil(sonar.project_key("sonar.foo=bar"))
    end)
  end)

  describe("language_server_cmd", function()
    it("returns nil when sonarlint-language-server is not executable", function()
      vim.fn.executable = function(name)
        if name == "sonarlint-language-server" then
          return 0
        end
        return orig_executable(name)
      end
      assert.is_nil(sonar.language_server_cmd())
    end)

    it("starts with the binary and -stdio when it is executable", function()
      vim.fn.executable = function(name)
        if name == "sonarlint-language-server" then
          return 1
        end
        return orig_executable(name)
      end
      local cmd = sonar.language_server_cmd()
      assert.is_table(cmd)
      assert.are.equal("sonarlint-language-server", cmd[1])
      assert.are.equal("-stdio", cmd[2])
    end)
  end)

  describe("find_project_settings", function()
    it("returns nil when no sonar-project.properties exists at the root", function()
      assert.is_nil(sonar.find_project_settings(vim.fn.tempname()))
    end)

    it("reads and parses an on-disk properties file", function()
      local dir = vim.fn.tempname()
      vim.fn.mkdir(dir, "p")
      vim.fn.writefile({ "sonar.projectKey=disk-key", "sonar.sources=src" }, dir .. "/sonar-project.properties")
      local settings = sonar.find_project_settings(dir)
      assert.are.equal("disk-key", settings["sonar.projectKey"])
      assert.are.equal("src", settings["sonar.sources"])
    end)

    it("walks upward from a nested module directory to the project root", function()
      local root = vim.fn.tempname()
      vim.fn.mkdir(root .. "/module-a/src/main", "p")
      vim.fn.writefile({ "sonar.projectKey=upward-key" }, root .. "/sonar-project.properties")
      local settings = sonar.find_project_settings(root .. "/module-a/src/main")
      assert.are.equal("upward-key", settings["sonar.projectKey"])
    end)
  end)
end)
