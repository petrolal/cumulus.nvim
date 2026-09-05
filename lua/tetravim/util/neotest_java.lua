-- TetraVim neotest-java JUnit jar bootstrapper
--
-- `neotest-java` needs the "JUnit Platform Console Standalone" jar present under
-- `stdpath("data")/neotest-java/` before it can build a test spec. Upstream only
-- fetches it through the interactive `:NeotestJava setup` (a `vim.ui.select`
-- consent prompt), so on a fresh clone the first `<leader>tr` throws:
--
--   Junit Platform Console Standalone jar not found at .../junit-platform-console-standalone-6.0.3.jar
--
-- This module downloads the pinned jar non-interactively (curl) and verifies its
-- SHA-256 against the value `neotest-java` ships in `default_config.lua`.

local M = {}

-- Keep in sync with neotest-java's LATEST_PINNED_VERSION in default_config.lua.
M.version = "6.0.3"
M.sha256 = "3ba0d6150af79214a1411f9ea2fbef864eef68b68c89a17f672c0b89bff9d3a2"

local function url()
  return ("https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/%s/junit-platform-console-standalone-%s.jar"):format(
    M.version,
    M.version
  )
end

function M.jar_path()
  return table.concat({
    vim.fn.stdpath("data"),
    "neotest-java",
    "junit-platform-console-standalone-" .. M.version .. ".jar",
  }, "/")
end

function M.is_installed()
  return vim.fn.filereadable(M.jar_path()) == 1
end

-- Directories that never hold hand-written sources; skipped when sniffing a
-- project so a Kotlin/Scala tree isn't misread as Java because of generated
-- stubs or an unpacked dependency jar under build/.
local PRUNE = {
  [".git"] = true,
  [".gradle"] = true,
  [".mvn"] = true,
  [".idea"] = true,
  ["build"] = true,
  ["target"] = true,
  ["out"] = true,
  ["bin"] = true,
  ["node_modules"] = true,
}

--- Does this directory tree contain any hand-written `.java` source file?
--- neotest-java only supports Java, but its `root_finder` happily claims any
--- Gradle/Maven project -- including Kotlin- or Scala-only ones -- which then
--- blows up in `client_provider` ("No Java file found in the directory").
--- @param dir string project root to sniff
--- @param max_depth integer|nil directory levels to descend (default 8)
--- @return boolean
function M.has_java_sources(dir, max_depth)
  max_depth = max_depth or 8
  local stack = { { path = dir, depth = 0 } }
  while #stack > 0 do
    local cur = table.remove(stack)
    local ok, entries = pcall(vim.fn.readdir, cur.path)
    if ok then
      for _, name in ipairs(entries) do
        local full = cur.path .. "/" .. name
        if name:match("%.java$") and vim.fn.isdirectory(full) == 0 then
          return true
        end
        if
          vim.fn.isdirectory(full) == 1
          and not PRUNE[name]
          and name:sub(1, 1) ~= "."
          and cur.depth + 1 <= max_depth
        then
          stack[#stack + 1] = { path = full, depth = cur.depth + 1 }
        end
      end
    end
  end
  return false
end

local function file_sha256(path)
  if vim.fn.executable("sha256sum") == 1 then
    return (vim.fn.system({ "sha256sum", path }):match("^(%x+)"))
  end
  if vim.fn.executable("shasum") == 1 then
    return (vim.fn.system({ "shasum", "-a", "256", path }):match("^(%x+)"))
  end
  return nil
end

local function warn(enabled, msg)
  if enabled then
    require("tetravim.util.notify").notify_warn(msg, "TetraVim Test")
  end
end

--- Ensure the JUnit standalone jar is present, downloading it if missing.
--- @param notify boolean|nil emit a single user-facing message on download/failure
--- @return boolean available true when the jar is on disk after the call
function M.ensure(notify)
  if M.is_installed() then
    return true
  end
  if vim.fn.executable("curl") ~= 1 then
    warn(notify, "neotest-java: curl not found -- run :NeotestJava setup to download the JUnit jar")
    return false
  end

  local path = M.jar_path()
  vim.fn.mkdir(vim.fn.fnamemodify(path, ":h"), "p")
  local out = vim.fn.system({ "curl", "-fsSL", "--create-dirs", "--output", path, url() })
  if vim.v.shell_error ~= 0 then
    vim.fn.delete(path)
    warn(notify, "neotest-java: failed to download the JUnit jar (" .. vim.trim(out) .. ")")
    return false
  end

  local got = file_sha256(path)
  if got and got:lower() ~= M.sha256 then
    vim.fn.delete(path)
    warn(notify, "neotest-java: JUnit jar checksum mismatch -- download discarded")
    return false
  end

  if notify then
    require("tetravim.util.notify").notify_info(
      "neotest-java: downloaded JUnit Platform Console Standalone " .. M.version,
      "TetraVim Test"
    )
  end
  return true
end

return M
