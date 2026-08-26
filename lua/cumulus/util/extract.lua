local M = {}

local refactor = require("cumulus.util.refactor")

M.ACTION_TIMEOUT_MS = 10000

local function notify_err(msg)
  vim.notify(msg, vim.log.levels.ERROR, { title = "Cumulus Extract" })
end

local function notify_warn(msg)
  vim.notify(msg, vim.log.levels.WARN, { title = "Cumulus Extract" })
end

local function notify_info(msg)
  vim.notify(msg, vim.log.levels.INFO, { title = "Cumulus Extract" })
end

local function handle_action_response(bufnr, jvm_client, action_name, responses, kind_prefix, title_substring)
  local resp = responses[jvm_client.id]
  if not resp or resp.err then
    local detail = resp and resp.err and resp.err.message or "no response from language server"
    notify_err(action_name .. " aborted: " .. detail)
    M._busy = false
    return
  end

  local actions = resp.result
  if not actions or #actions == 0 then
    notify_warn("No applicable " .. action_name .. " code action available here")
    M._busy = false
    return
  end

  local target_action = nil
  for _, action in ipairs(actions) do
    local kind_match = action.kind and vim.startswith(action.kind, kind_prefix)
    local title_match = not title_substring or (action.title and string.find(action.title, title_substring, 1, true))
    if kind_match and title_match then
      target_action = action
      break
    end
  end

  if not target_action then
    notify_warn("No applicable " .. action_name .. " code action available here")
    M._busy = false
    return
  end

  -- We need a WorkspaceEdit. If it has an edit, use it.
  if target_action.edit then
    M._show_preview(target_action.edit, jvm_client, action_name)
  elseif target_action.command then
    -- It has a command but no edit. We need to resolve it or it's not supported for dry-run.
    local resolve_provider = jvm_client.server_capabilities.codeActionProvider
      and type(jvm_client.server_capabilities.codeActionProvider) == "table"
      and jvm_client.server_capabilities.codeActionProvider.resolveProvider

    if resolve_provider then
      local resolve_responded = false
      vim.defer_fn(function()
        if resolve_responded then
          return
        end
        resolve_responded = true
        notify_err(action_name .. " resolve timed out waiting for '" .. jvm_client.name .. "'")
        M._busy = false
      end, M.ACTION_TIMEOUT_MS)

      vim.lsp.buf_request_all(bufnr, "codeAction/resolve", target_action, function(resolve_responses)
        if resolve_responded then
          return
        end
        resolve_responded = true
        vim.schedule(function()
          local res = resolve_responses[jvm_client.id]
          if res and res.result and res.result.edit then
            M._show_preview(res.result.edit, jvm_client, action_name)
          else
            local err_msg = res and res.error and res.error.message or "no edit returned"
            notify_err(action_name .. ": server returned a command without an edit, and resolve failed: " .. err_msg)
            M._busy = false
          end
        end)
      end)
    else
      notify_err(
        action_name
          .. ": server returned a command without an edit, and does not support codeAction/resolve. Dry-run preview is impossible."
      )
      M._busy = false
    end
  else
    notify_warn("Code action for " .. action_name .. " returned no edit and no command.")
    M._busy = false
  end
end

function M._show_preview(workspace_edit, jvm_client, action_name)
  local locations = refactor.workspace_edit_to_locations(workspace_edit)
  if #locations == 0 then
    notify_warn(action_name .. ": no changes returned")
    M._busy = false
    return
  end

  local qf_items = vim.lsp.util.locations_to_items(locations, jvm_client.offset_encoding)

  vim.fn.setqflist({}, " ", {
    title = string.format("%s (%d location%s)", action_name, #qf_items, #qf_items == 1 and "" or "s"),
    items = qf_items,
  })
  vim.cmd("copen")

  vim.ui.select({ "Apply", "Cancel" }, {
    prompt = string.format("Apply %s to %d location(s)?", action_name, #qf_items),
  }, function(choice)
    if choice ~= "Apply" then
      notify_info(action_name .. " cancelled -- no changes applied")
      M._busy = false
      return
    end

    local lsp_ok, lsp_err = pcall(vim.lsp.util.apply_workspace_edit, workspace_edit, jvm_client.offset_encoding)
    if not lsp_ok then
      notify_err(action_name .. ": failed to apply the LSP workspace edit (" .. tostring(lsp_err) .. ")")
      M._busy = false
      return
    end

    notify_info(string.format("Applied %s across %d location(s)", action_name, #qf_items))
    M._busy = false
  end)
end

local function do_action(action_name, kind_prefix, title_substring, is_visual)
  if M._busy then
    notify_warn("An action is already in progress -- please wait for it to finish")
    return
  end

  local bufnr = vim.api.nvim_get_current_buf()
  local win = vim.api.nvim_get_current_win()

  local jvm_client = refactor.find_jvm_client(bufnr)
  if not jvm_client then
    notify_warn("No " .. action_name .. " available for this buffer (no JDTLS/Kotlin LS client attached)")
    return
  end

  M._busy = true

  local params = vim.lsp.util.make_range_params(win, jvm_client.offset_encoding)
  
  -- If in visual mode, make_range_params only uses cursor position, so we override it with '< and '>
  if is_visual then
    local start_pos = vim.api.nvim_buf_get_mark(bufnr, "<")
    local end_pos = vim.api.nvim_buf_get_mark(bufnr, ">")
    if start_pos[1] > 0 and end_pos[1] > 0 then
      params.range = {
        start = { line = start_pos[1] - 1, character = start_pos[2] },
        ["end"] = { line = end_pos[1] - 1, character = end_pos[2] + 1 },
      }
    end
  end

  params.context = {
    diagnostics = vim.diagnostic.get(bufnr, { lnum = params.range.start.line }),
    only = { kind_prefix },
  }

  local responded = false
  vim.defer_fn(function()
    if responded then
      return
    end
    responded = true
    M._busy = false
    notify_err(
      action_name
        .. " timed out waiting for '"
        .. jvm_client.name
        .. "' to respond ("
        .. (M.ACTION_TIMEOUT_MS / 1000)
        .. "s) -- no changes applied"
    )
  end, M.ACTION_TIMEOUT_MS)

  vim.lsp.buf_request_all(bufnr, "textDocument/codeAction", params, function(responses)
    if responded then
      return
    end
    responded = true
    vim.schedule(function()
      handle_action_response(bufnr, jvm_client, action_name, responses, kind_prefix, title_substring)
    end)
  end)
end

function M.extract_interface(is_visual)
  do_action("Extract interface", "refactor.extract.interface", nil, is_visual)
end

function M.inline(is_visual)
  do_action("Inline", "refactor.inline", nil, is_visual)
end

function M.extract_method(is_visual)
  do_action("Extract method", "refactor.extract", "Extract to method", is_visual)
end

function M.extract_variable(is_visual)
  do_action("Extract variable", "refactor.extract", "Extract to local variable", is_visual)
end

function M.extract_constant(is_visual)
  do_action("Extract constant", "refactor.extract", "Extract to constant", is_visual)
end

return M
