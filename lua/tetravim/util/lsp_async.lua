-- Async LSP request utilities for TetraVim
-- Provides non-blocking wrappers around LSP requests.

local M = {}

--- Synchronous wrapper using the existing vim.lsp.buf_request_all (may block).
--- @param bufnr integer Buffer number
--- @param method string LSP method name
--- @param params table Parameters for the request
--- @return table responses Mapping client.id -> { err = ..., result = ... }
function M.request_all_sync(bufnr, method, params)
  return vim.lsp.buf_request_all(bufnr, method, params)
end

--- Asynchronous wrapper that dispatches the request to all active LSP clients attached to the buffer.
--- Calls the provided callback once all client responses are collected.
--- @param bufnr integer Buffer number
--- @param method string LSP method name
--- @param params table Parameters for the request
--- @param callback function Callback invoked with the responses table when all clients have responded.
function M.request_all_async(bufnr, method, params, callback)
  local clients = vim.lsp.get_active_clients({ bufnr = bufnr })
  local pending = #clients
  local results = {}

  if pending == 0 then
    if callback then callback(results) end
    return
  end

  for _, client in ipairs(clients) do
    client.request(method, params, function(err, result, ctx, _)
      results[client.id] = { err = err, result = result }
      pending = pending - 1
      if pending == 0 and callback then
        vim.schedule(function()
          callback(results)
        end)
      end
    end, bufnr)
  end
end

return M
