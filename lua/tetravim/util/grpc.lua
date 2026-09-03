-- TetraVim gRPC / grpcurl Integration (SPEC-3.4)
--
-- An async `grpcurl` wrapper (list / describe / invoke) modeled verbatim in
-- shape on util/http.lua's `jq_filter`: an `executable` guard with an
-- install hint, an explicit `timeout`, a timeout-code branch, a
-- `vim.schedule`d callback, and `ui.notify_err` with captured stderr on a
-- nonzero exit. grpcurl itself is a real binary shelled out via
-- `vim.system` -- no gRPC/descriptor logic is reimplemented in Lua beyond
-- walking `grpcurl describe` / `-msg-template` output.
--
-- `request_skeleton` is pure: it turns a `-msg-template` JSON object into a
-- deterministic, `TODO`-annotated JSON skeleton string, the same
-- read-and-transform discipline as util/openapi.lua's sorted `paths` walk.

local ui = require("tetravim.util.ui")

local M = {}

-- Guards against a hung/unreachable gRPC server leaving the async call
-- running indefinitely with no feedback -- mirrors http.lua's JQ_TIMEOUT_MS.
local GRPCURL_TIMEOUT_MS = 15000

local INSTALL_HINT = "`grpcurl` is not installed or not on $PATH. Install it (e.g. `brew install grpcurl`, "
  .. "`go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest`, or your distro's package) "
  .. "to use gRPC service inspection and RPC execution."

--- Whether `grpcurl` is callable; notifies with an install hint if not.
---@return boolean
local function has_grpcurl()
  if vim.fn.executable("grpcurl") ~= 1 then
    ui.notify_err(INSTALL_HINT)
    return false
  end
  return true
end

--- Run a grpcurl command array asynchronously, never blocking the UI.
--- `callback(stdout_text)` fires only on a clean (code 0) exit; every other
--- outcome notifies via `ui.notify_err` and never crashes. Copies
--- http.lua:jq_filter's vim.system(...) + vim.schedule + timeout-code +
--- stderr-surfacing shape exactly -- only the command array and stdin differ.
---@param cmd string[]
---@param stdin string|nil
---@param callback fun(stdout_text: string)|nil
local function run(cmd, stdin, callback)
  vim.system(cmd, { text = true, stdin = stdin or nil, timeout = GRPCURL_TIMEOUT_MS }, function(out)
    vim.schedule(function()
      -- vim.system() reports a timeout-triggered kill via code=124/signal=15
      -- (the `timeout(1)` convention); grpcurl never legitimately exits 124,
      -- so this is an unambiguous, separate case from a generic error exit.
      if out.code == 124 and out.signal and out.signal ~= 0 then
        ui.notify_err("grpcurl timed out after " .. (GRPCURL_TIMEOUT_MS / 1000) .. "s")
      elseif out.code == 0 then
        if type(callback) == "function" then
          callback(out.stdout or "")
        end
      else
        local detail = out.stderr
        if not detail or detail == "" then
          detail = out.stdout or ("grpcurl exited with code " .. tostring(out.code))
        end
        ui.notify_err("grpcurl failed: " .. detail)
      end
    end)
  end)
end

--- List the services a reflection-enabled gRPC server at `addr`
--- ("host:port") exposes. `cb` receives grpcurl's raw stdout (one
--- fully-qualified service name per line).
---@param addr string
---@param cb fun(stdout_text: string)
function M.list_services(addr, cb)
  if not has_grpcurl() then
    return
  end
  run({ "grpcurl", "-plaintext", addr, "list" }, nil, cb)
end

--- Describe a symbol (a service, method, or message type) on the server at
--- `addr`. `-msg-template` is always passed so that describing a message
--- type also emits an example JSON payload block that `request_skeleton`
--- can consume. A nil/empty `symbol` describes the whole server.
---@param addr string
---@param symbol string|nil
---@param cb fun(stdout_text: string)
function M.describe(addr, symbol, cb)
  if not has_grpcurl() then
    return
  end
  local cmd = { "grpcurl", "-plaintext", "-msg-template", addr, "describe" }
  if type(symbol) == "string" and symbol ~= "" then
    table.insert(cmd, symbol)
  end
  run(cmd, nil, cb)
end

--- Invoke `method` ("pkg.Service/Method" or "pkg.Service.Method") on the
--- server at `addr` with `json_payload` as the request body, piped to
--- grpcurl on stdin (`-d @`). Refuses -- via `ui.notify_err`, before any
--- `vim.system` call -- when the payload does not pass
--- http.lua:looks_like_json. `cb` receives the response JSON on success.
---@param addr string
---@param method string
---@param json_payload string
---@param cb fun(stdout_text: string)
function M.invoke(addr, method, json_payload, cb)
  if not has_grpcurl() then
    return
  end
  if not require("tetravim.util.http").looks_like_json(json_payload) then
    ui.notify_err("gRPC request payload is not valid JSON -- fix it before invoking (nothing was sent to grpcurl)")
    return
  end
  run({ "grpcurl", "-d", "@", "-plaintext", addr, method }, json_payload, cb)
end

-- ---------------------------------------------------------------------------
-- Pure text/JSON walkers over grpcurl output (no descriptor parsing).
-- ---------------------------------------------------------------------------

--- Parse `grpcurl list` / `grpcurl list <service>` stdout into a sorted,
--- de-duplicated list of the non-empty names it printed (one per line).
---@param text string
---@return string[]
function M.parse_service_list(text)
  local names, seen = {}, {}
  for _, line in ipairs(vim.split(text or "", "\n", { plain = true })) do
    local name = vim.trim(line)
    if name ~= "" and not seen[name] then
      seen[name] = true
      table.insert(names, name)
    end
  end
  table.sort(names)
  return names
end

--- Walk a `grpcurl describe <service>` textual block and pull out its
--- `rpc <Name> ( <RequestType> ) returns ( <ResponseType> );` lines.
--- Returns a list of `{ name, request_type }` (leading "." on the type
--- stripped), sorted by name. This is a shallow line scan of grpcurl's own
--- output, not a proto grammar.
---@param text string
---@return { name: string, request_type: string }[]
function M.parse_methods(text)
  local methods, seen = {}, {}
  for _, line in ipairs(vim.split(text or "", "\n", { plain = true })) do
    -- `rpc Foo ( .pkg.FooRequest ) returns ( .pkg.FooResponse );`
    -- also tolerate a `stream` qualifier before the request/response type.
    local name, req = line:match("%f[%w]rpc%s+([%w_]+)%s*%(%s*[sS]tream%s+([%w_%.]+)%s*%)")
    if not name then
      name, req = line:match("%f[%w]rpc%s+([%w_]+)%s*%(%s*([%w_%.]+)%s*%)")
    end
    if name and not seen[name] then
      seen[name] = true
      table.insert(methods, { name = name, request_type = (req or ""):gsub("^%.", "") })
    end
  end
  table.sort(methods, function(a, b)
    return a.name < b.name
  end)
  return methods
end

--- Extract the JSON object that `grpcurl -msg-template describe <type>`
--- prints under a "Message template:" heading. Falls back to the first
--- balanced top-level `{ ... }` block in `text`. Returns the JSON substring
--- or nil.
---@param text string
---@return string|nil
function M.extract_msg_template(text)
  text = text or ""
  local start = text:find("Message template:", 1, true)
  local scan_from = start and (start + #"Message template:") or 1
  local open = text:find("{", scan_from, true)
  if not open then
    return nil
  end
  local depth, in_str, esc = 0, false, false
  for i = open, #text do
    local c = text:sub(i, i)
    if in_str then
      if esc then
        esc = false
      elseif c == "\\" then
        esc = true
      elseif c == '"' then
        in_str = false
      end
    elseif c == '"' then
      in_str = true
    elseif c == "{" then
      depth = depth + 1
    elseif c == "}" then
      depth = depth - 1
      if depth == 0 then
        return text:sub(open, i)
      end
    end
  end
  return nil
end

--- Render a decoded `-msg-template` value as a `TODO`-annotated skeleton
--- with deterministic (sorted) key ordering and 2-space indentation.
---@param value any
---@param indent integer
---@return string
local function render(value, indent)
  local t = type(value)
  if t == "string" then
    return '"TODO: string"'
  elseif t == "number" then
    return '"TODO: number"'
  elseif t == "boolean" then
    return '"TODO: bool"'
  elseif t == "table" then
    local pad = string.rep("  ", indent)
    local pad_inner = string.rep("  ", indent + 1)
    if vim.islist(value) and #value > 0 then
      return "[\n" .. pad_inner .. render(value[1], indent + 1) .. "\n" .. pad .. "]"
    end
    local keys = {}
    for k in pairs(value) do
      table.insert(keys, tostring(k))
    end
    -- An empty table (including vim.empty_dict() and a bare []) is rendered
    -- as an empty object: a `-msg-template` payload's top level and its
    -- nested message fields are always JSON objects.
    if #keys == 0 then
      return "{}"
    end
    table.sort(keys)
    local parts = {}
    for _, k in ipairs(keys) do
      table.insert(parts, pad_inner .. '"' .. k .. '": ' .. render(value[k], indent + 1))
    end
    return "{\n" .. table.concat(parts, ",\n") .. "\n" .. pad .. "}"
  end
  return '"TODO"'
end

--- Turn a `grpcurl -msg-template` JSON object string into an editable
--- request skeleton: every field mapped to a `"TODO: <type>"` placeholder,
--- keys sorted for stable output. Returns the skeleton string, or nil
--- (after `ui.notify_warn`) when there is nothing parseable to build from.
---@param describe_json string
---@return string|nil
function M.request_skeleton(describe_json)
  if type(describe_json) ~= "string" or vim.trim(describe_json) == "" then
    ui.notify_warn("gRPC: no message template available to build a request skeleton from")
    return nil
  end

  local ok, decoded = pcall(vim.json.decode, describe_json)
  if not ok or type(decoded) ~= "table" then
    ui.notify_warn("gRPC: could not parse the message template as JSON")
    return nil
  end

  return render(decoded, 0)
end

return M
