-- Cumulus Spring Datasource Credential Auto-Discovery (SPEC-3.1)
--
-- Pure-Lua file/string parsing only -- never shells out to the compiled
-- Scala cumulus-engine binary for this. Discovery is stateless: config files
-- are read fresh from disk on every call, nothing is cached or persisted.
--
-- Precedence mirrors Spring Boot's own: if a project has BOTH
-- application.properties and application.yml/application.yaml with a usable
-- spring.datasource.* block, the .properties values win and the YAML
-- file(s) are ignored entirely.
--
-- `${ENV_VAR}` / `${ENV_VAR:default}` placeholders (the standard, extremely
-- common Spring Boot idiom for datasource credentials) are resolved against
-- the current process environment, mirroring Spring's own
-- PropertySourcesPlaceholderConfigurer semantics -- see resolve_placeholders
-- below.

local ui = require("cumulus.util.ui")

local M = {}

-- Directories that are never worth descending into while hunting for Spring
-- config files -- keeps the on-every-init scan from wandering into VCS
-- internals, dependency caches, or build output on large projects.
local IGNORED_DIRS = {
  [".git"] = true,
  [".hg"] = true,
  [".svn"] = true,
  [".idea"] = true,
  [".gradle"] = true,
  [".mvn"] = true,
  ["node_modules"] = true,
  ["target"] = true,
  ["build"] = true,
  ["dist"] = true,
  ["out"] = true,
  [".venv"] = true,
}

local MAX_DEPTH = 8

--- Recursively collect every file named `filename` under `root_dir`, using a
--- bounded manual walk (rather than an unfiltered vim.fs.find) so common
--- huge/irrelevant directories never get traversed.
---@param root_dir string
---@param filename string
---@param depth? integer
---@param results? string[]
---@return string[]
local function find_files(root_dir, filename, depth, results)
  depth = depth or 0
  results = results or {}
  if depth > MAX_DEPTH then
    return results
  end

  local ok, iter = pcall(vim.fs.dir, root_dir)
  if not ok or not iter then
    return results
  end

  for name, kind in iter do
    if kind == "file" and name == filename then
      table.insert(results, root_dir .. "/" .. name)
    elseif kind == "directory" and not IGNORED_DIRS[name] and not name:match("^%.") then
      find_files(root_dir .. "/" .. name, filename, depth + 1, results)
    end
  end

  return results
end

--- Parse `spring.datasource.{url,username,password}` out of flat dotted
--- `.properties` lines (`key = value`, `#`/`!` comments ignored).
---@param lines string[]
---@return { url?: string, username?: string, password?: string }
local function parse_properties_lines(lines)
  local raw = {}
  for _, line in ipairs(lines) do
    local trimmed = line:match("^%s*(.-)%s*$")
    if trimmed ~= "" and not trimmed:match("^[#!]") then
      local key, val = trimmed:match("^([%w%.%-_]+)%s*=%s*(.-)%s*$")
      if key == "spring.datasource.url" then
        raw.url = val
      elseif key == "spring.datasource.username" then
        raw.username = val
      elseif key == "spring.datasource.password" then
        raw.password = val
      end
    end
  end
  return raw
end

--- Strip surrounding quotes from a YAML scalar value, if present.
---@param val string
---@return string
local function unquote(val)
  local unwrapped = val:match('^"(.*)"$') or val:match("^'(.*)'$")
  return unwrapped or val
end

--- Parse `spring: \n  datasource: \n    url: ...` out of indentation-nested
--- `.yml` lines. This is intentionally NOT a general-purpose YAML parser --
--- it only tracks an indentation stack of `key:` lines well enough to find
--- the exact `spring.datasource.{url,username,password}` path.
---@param lines string[]
---@return { url?: string, username?: string, password?: string }
local function parse_yaml_lines(lines)
  local raw = {}
  -- Stack of { indent = <n>, key = <string> } describing the current
  -- nesting path down to (but not including) the line being processed.
  local stack = {}

  for _, line in ipairs(lines) do
    if line:match("%S") and not line:match("^%s*#") then
      local indent_str, key, val = line:match("^(%s*)([%w_%-]+):%s*(.-)%s*$")
      if key then
        local indent = #indent_str
        while #stack > 0 and stack[#stack].indent >= indent do
          table.remove(stack)
        end
        table.insert(stack, { indent = indent, key = key })

        if val ~= "" then
          local path = {}
          for _, entry in ipairs(stack) do
            table.insert(path, entry.key)
          end
          local full_path = table.concat(path, ".")
          local value = unquote(val)
          if full_path == "spring.datasource.url" then
            raw.url = value
          elseif full_path == "spring.datasource.username" then
            raw.username = value
          elseif full_path == "spring.datasource.password" then
            raw.password = value
          end
        end
      end
    end
  end

  return raw
end

--- Percent-encode a credential component per RFC 3986 (keep only unreserved
--- characters -- ALPHA / DIGIT / "-" / "." / "_" / "~" -- literal). Without
--- this, a credential containing "@", ":", "/", or "%" would be spliced
--- straight into the URL's authority component and misparsed (e.g. an "@"
--- in a password would be read as the userinfo/host separator).
---@param str string
---@return string
local function url_encode(str)
  return (str:gsub("[^%w%-%.%_%~]", function(c)
    return string.format("%%%02X", string.byte(c))
  end))
end

--- Parse a plain `.env` file (`KEY=VALUE` lines, `#` comments, an optional
--- leading `export `, optional surrounding quotes on the value) into a
--- lookup table. Not a full dotenv implementation -- no variable
--- interpolation, no multi-line values -- just enough to resolve the
--- `${VAR}` placeholders discover_datasources() needs.
---@param lines string[]
---@return table<string, string>
local function parse_dotenv_lines(lines)
  local values = {}
  for _, line in ipairs(lines) do
    local trimmed = line:match("^%s*(.-)%s*$")
    if trimmed ~= "" and not trimmed:match("^#") then
      trimmed = trimmed:gsub("^export%s+", "")
      local key, val = trimmed:match("^([%w_]+)%s*=%s*(.-)%s*$")
      if key then
        values[key] = unquote(val)
      end
    end
  end
  return values
end

--- Load `root_dir/.env`, if present, as a fallback `${VAR}` resolution
--- source. Very common in local Spring Boot dev (IntelliJ's EnvFile plugin,
--- `direnv`, manually-sourced `.env` files, etc. all populate exactly this
--- shape) for projects that document "create a .env file" rather than
--- exporting SPRING_DATASOURCE_* in the shell cumulus.nvim itself inherits.
---@param root_dir string
---@return table<string, string>
local function load_dotenv(root_dir)
  local path = root_dir .. "/.env"
  if vim.fn.filereadable(path) ~= 1 then
    return {}
  end
  local ok, lines = pcall(vim.fn.readfile, path)
  if not ok then
    return {}
  end
  return parse_dotenv_lines(lines)
end

--- Resolve Spring `${ENV_VAR}` / `${ENV_VAR:default}` placeholders in a
--- config value, mirroring Spring Boot's own
--- PropertySourcesPlaceholderConfigurer semantics: a set, non-empty process
--- environment variable always wins; otherwise a matching key in the
--- project's `root_dir/.env` file (if any) is used, since that's how these
--- variables actually reach the app in the common local-dev setup; otherwise
--- the literal text after the first `:` (which may itself contain `:` or
--- `/`, e.g. a full JDBC URL default) is used; a placeholder with no default
--- and no match anywhere is left as `${VAR_NAME}` and reported as
--- unresolved. Values with no `${...}` at all pass through unchanged.
---@param value string
---@param dotenv table<string, string> from load_dotenv()
---@return string resolved
---@return string[] unresolved names of `${VAR}`/`${VAR:default}` placeholders that could not be resolved
local function resolve_placeholders(value, dotenv)
  local unresolved = {}
  local resolved = value:gsub("%${([^}:]+):?([^}]*)}", function(var_name, default)
    local env_val = vim.env[var_name]
    if env_val and env_val ~= "" then
      return env_val
    end
    local dotenv_val = dotenv[var_name]
    if dotenv_val and dotenv_val ~= "" then
      return dotenv_val
    end
    if default ~= "" then
      return default
    end
    table.insert(unresolved, var_name)
    return "${" .. var_name .. "}"
  end)
  return resolved, unresolved
end

--- Convert a JDBC-style URL (`jdbc:postgresql://host:port/db`) plus
--- credentials into dadbod's own connection URL convention
--- (`driver://user:password@host:port/db`) by stripping the `jdbc:` prefix
--- and splicing the (percent-encoded) credentials into the authority
--- component.
---@param jdbc_url string
---@param username string
---@param password string
---@return string? dadbod_url nil if `jdbc_url` doesn't look like a JDBC URL
local function jdbc_to_dadbod_url(jdbc_url, username, password)
  local stripped = jdbc_url:gsub("^jdbc:", "")
  local scheme, rest = stripped:match("^([%w%+]+)://(.*)$")
  if not scheme then
    return nil
  end
  return scheme .. "://" .. url_encode(username) .. ":" .. url_encode(password) .. "@" .. rest
end

--- Build a human-readable, disambiguated connection name for a discovered
--- config file. A single match just uses the project directory name; when
--- multiple modules each have their own config file, the entry's relative
--- path is appended so `vim.g.dbs` entries don't collide.
---@param root_dir string
---@param file_path string
---@param total integer total number of files found for this filename
---@return string
local function entry_name(root_dir, file_path, total)
  local project = vim.fs.basename(root_dir)
  if total <= 1 then
    return project
  end
  local rel = file_path:sub(#root_dir + 2)
  return project .. " (" .. rel .. ")"
end

--- Turn a raw `{url, username, password}` table (possibly partial or empty)
--- into a `vim.g.dbs` entry, warning and returning nil for a malformed
--- (partially-populated) block, and silently returning nil when no
--- spring.datasource.* keys were found at all (not an error).
---@param name string
---@param raw { url?: string, username?: string, password?: string }
---@param source_path string
---@param dotenv table<string, string> from load_dotenv()
---@return { name: string, url: string }?
local function build_entry(name, raw, source_path, dotenv)
  local has_url = raw.url ~= nil
  local has_username = raw.username ~= nil
  local has_password = raw.password ~= nil

  if not (has_url or has_username or has_password) then
    return nil
  end

  if not (has_url and has_username and has_password) then
    local missing = {}
    if not has_url then
      table.insert(missing, "url")
    end
    if not has_username then
      table.insert(missing, "username")
    end
    if not has_password then
      table.insert(missing, "password")
    end
    ui.notify_warn(
      string.format(
        "Incomplete spring.datasource config in %s (missing %s) -- skipping this connection",
        source_path,
        table.concat(missing, ", ")
      )
    )
    return nil
  end

  local unresolved_vars = {}
  local resolved_url, unresolved_url = resolve_placeholders(raw.url, dotenv)
  local resolved_username, unresolved_username = resolve_placeholders(raw.username, dotenv)
  local resolved_password, unresolved_password = resolve_placeholders(raw.password, dotenv)
  vim.list_extend(unresolved_vars, unresolved_url)
  vim.list_extend(unresolved_vars, unresolved_username)
  vim.list_extend(unresolved_vars, unresolved_password)

  if #unresolved_vars > 0 then
    ui.notify_warn(
      string.format(
        "Spring datasource in %s references environment variable(s) not set in this session or in a "
          .. "project-root .env file, with no default (%s) -- skipping this connection. Set them in your "
          .. "shell, add a .env file at the project root, or add `:default` fallbacks in the config.",
        source_path,
        table.concat(unresolved_vars, ", ")
      )
    )
    return nil
  end

  local url = jdbc_to_dadbod_url(resolved_url, resolved_username, resolved_password)
  if not url then
    ui.notify_warn("Could not parse spring.datasource.url in " .. source_path .. " -- skipping this connection")
    return nil
  end

  return { name = name, url = url }
end

--- Discover Spring Boot datasource credentials under `root_dir` and return
--- them as an array of `{ name, url }` entries suitable for direct
--- assignment to `vim.g.dbs`. Returns an empty table when nothing is found.
--- `application.properties` matches take precedence over `application.yml`
--- / `application.yaml` (matching Spring Boot's own precedence, which
--- treats the `.yml` and `.yaml` extensions as equivalent); the YAML tier is
--- only consulted when no valid entry was found in any `.properties` file.
---@param root_dir string
---@return { name: string, url: string }[]
function M.discover_datasources(root_dir)
  local dbs = {}
  local dotenv = load_dotenv(root_dir)

  local prop_files = find_files(root_dir, "application.properties")
  for _, path in ipairs(prop_files) do
    local raw = parse_properties_lines(vim.fn.readfile(path))
    local entry = build_entry(entry_name(root_dir, path, #prop_files), raw, path, dotenv)
    if entry then
      table.insert(dbs, entry)
    end
  end

  if #dbs > 0 then
    return dbs
  end

  -- Spring Boot recognizes both `application.yml` and `application.yaml` as
  -- the same config tier -- collect both before falling back from the
  -- .properties tier.
  local yml_files = find_files(root_dir, "application.yml")
  vim.list_extend(yml_files, find_files(root_dir, "application.yaml"))
  for _, path in ipairs(yml_files) do
    local raw = parse_yaml_lines(vim.fn.readfile(path))
    local entry = build_entry(entry_name(root_dir, path, #yml_files), raw, path, dotenv)
    if entry then
      table.insert(dbs, entry)
    end
  end

  return dbs
end

return M
